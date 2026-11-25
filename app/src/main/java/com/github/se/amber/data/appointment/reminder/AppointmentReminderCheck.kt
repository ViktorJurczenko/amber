// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.util.Log
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.PlannedReminder
import com.github.se.amber.model.appointment.isLinkedTo
import com.github.se.amber.model.appointment.occurrencesIn
import com.github.se.amber.model.appointment.planReminders
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.localDateOf
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that decides which reminders this phone shows, and when its timer goes off.
 *
 * Only the timer's receiver runs it: when a reminder is due, every 6 hours, and when the app opens
 * or saves an appointment, which set the timer for now. Each [run] reads the appointments of the
 * active circle, keeps the ones the signed-in user is linked to, and expands them with
 * `occurrencesIn`. Then it:
 * 1. shows every reminder due since the last check, through [poster];
 * 2. stores the current time as the last check;
 * 3. sets [timer] for the next reminder, or for 6 hours from now if none comes sooner.
 *
 * So the timer is always set, and the check comes back at least every 6 hours. Signed out, or
 * without an active circle, it reads nothing and only sets the timer 6 hours ahead, so a later
 * sign-in is seen too.
 *
 * It reads the appointments with `getAppointments`, which asks the server first. That is how a
 * check sees the changes made on other phones while this app was closed. It waits for the server
 * for at most 5 seconds, since the alarm that runs it only has a few seconds. Offline, or when the
 * server is too slow, it plans from what the phone has already loaded.
 *
 * One case is accepted. Someone saves an appointment after one of its reminder times, so that
 * reminder moves to the save. If a check then runs offline, before this phone has loaded that save,
 * it doesn't see the appointment, and it still stores the current time as the last check. The next
 * check online sees the appointment, but the moved reminder now lies before the last check, so it
 * is skipped. The appointment's other reminders still come.
 *
 * It runs on every member's phone, but shows only the appointments the user is linked to: the
 * patient gets all of them, a caregiver only the ones they attend.
 *
 * It shows a due reminder only if its occurrence hasn't started. A reminder planned before the
 * start can be due long after it, for example when the phone was off. A reminder for something
 * already happening is no use. Of the due reminders of one occurrence, only the latest is shown.
 *
 * It plans from the current appointments only. So an appointment deleted or moved, a reminder time
 * removed, or a caregiver removed from the attendees, gets no reminder. There is no older copy to
 * check again.
 *
 * If the profile or the appointments can't be read, it shows nothing and keeps the last check. It
 * sets the timer 15 minutes ahead, and that run shows what is due then. Two runs never overlap: the
 * second waits.
 *
 * @param zone the phone's time zone, read at each run.
 * @param nowMillis the current time.
 */
class AppointmentReminderCheck(
    private val appointmentRepository: AppointmentRepository,
    private val profileRepository: ProfileRepository,
    private val activeCircleRepository: ActiveCircleRepository,
    private val authRepository: AuthRepository,
    private val poster: AppointmentReminderPoster,
    private val store: ReminderCheckStore,
    private val timer: ReminderTimer,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  private val mutex = Mutex()

  /**
   * Shows what is due, stores the time, and sets the timer for the next reminder within 6 hours, or
   * for 6 hours from now.
   *
   * The timer is always set, even when the run fails or is stopped. Otherwise nothing would run the
   * check again until the app is opened. After a failure, it tries again in 15 minutes.
   */
  suspend fun run(): Unit = mutex.withLock {
    val now = nowMillis()
    var timerAt = now + RETRY_MILLIS
    try {
      timerAt = showDueAndPlan(now)
    } finally {
      timer.set(timerAt)
    }
  }

  /**
   * Shows the reminders due since the last check, and stores [now] as the last check.
   *
   * @return when the timer goes off next: the first reminder within 6 hours, or 6 hours from now.
   *   If the profile or the appointments can't be read, 15 minutes from now.
   */
  private suspend fun showDueAndPlan(now: Long): Long {
    val recheckAt = now + RECHECK_MILLIS
    val retryAt = now + RETRY_MILLIS
    val uid = authRepository.currentUser?.uid ?: return recheckAt
    val profile =
        profileRepository.getCurrentProfile().getOrElse {
          Log.w(TAG, "Couldn't read the profile", it)
          return retryAt
        } ?: return recheckAt
    val circleId =
        activeCircleRepository.activeCircleId(uid, profile.circleIds).first() ?: return recheckAt
    val zone = zone()
    val today = localDateOf(now, zone)
    val appointments =
        appointmentRepository.getAppointments(circleId, today, WINDOW_DAYS).getOrElse {
          Log.w(TAG, "Couldn't read the appointments", it)
          return retryAt
        }
    val window = dayWindow(today, WINDOW_DAYS, zone)
    val occurrences =
        appointments.filter { it.isLinkedTo(uid) }.flatMap { it.occurrencesIn(window, zone) }

    val last = store.lastCheckedAt() ?: now
    planReminders(occurrences, last, now)
        .filter { it.occurrence.startAt > now }
        // soonest first, so the last one of each occurrence is the latest
        .associateBy { it.occurrence.id to it.occurrence.startAt }
        .values
        .forEach { post(circleId, it) }
    store.setLastCheckedAt(now)

    // planReminders only keeps the reminders until recheckAt, so the timer is never set more than
    // 6 hours ahead. A reminder further away is planned by a later check.
    return planReminders(occurrences, now, recheckAt).firstOrNull()?.fireAt ?: recheckAt
  }

  /** Shows [reminder]. A failure is only logged, so the other reminders still come. */
  private suspend fun post(circleId: String, reminder: PlannedReminder) {
    try {
      poster.post(circleId, reminder)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't show the reminder of ${reminder.occurrence.id}", e)
    }
  }

  internal companion object {
    const val TAG = "AppointmentReminderCheck"

    /**
     * The longest time between two checks. Without a reminder sooner, the timer goes off then, so
     * the changes made on other phones are seen within this time.
     */
    val RECHECK_MILLIS = 6.hours.inWholeMilliseconds

    /** How soon the check tries again after it couldn't read the profile or the appointments. */
    val RETRY_MILLIS = 15.minutes.inWholeMilliseconds

    /**
     * How many days of appointments are read, from today. A due reminder belongs to an appointment
     * starting within 7 days, its longest reminder. The next one, within 6 hours, belongs to one
     * starting within 7 days and 6 hours. That can end on the 9th day, counting today.
     */
    const val WINDOW_DAYS = 9
  }
}
