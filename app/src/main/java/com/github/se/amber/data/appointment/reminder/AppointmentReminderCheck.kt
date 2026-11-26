// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.util.Log
import com.github.se.amber.data.appointment.AppointmentError
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.appointment.AppointmentsRead
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.PlannedReminder
import com.github.se.amber.model.appointment.isLinkedTo
import com.github.se.amber.model.appointment.occurrencesIn
import com.github.se.amber.model.appointment.planReminders
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.localDateOf
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that decides which reminders this phone shows, and when its timer goes off.
 *
 * Only the timer's receiver runs it: when a reminder is due, every 6 hours, and when the app opens
 * or saves an appointment, which set the timer for now. Each [run] reads the appointments of every
 * circle of the signed-in user, keeps the ones the user is linked to, and expands them with
 * `occurrencesIn`. Then it:
 * 1. shows every reminder due since the user's last check, through [poster], unless it already
 *    showed it;
 * 2. stores the current time as the user's last check;
 * 3. sets [timer] for the next reminder, or for 6 hours from now if none comes sooner.
 *
 * So the timer is always set, and the check comes back at least every 6 hours. Signed out, or
 * without a circle, it reads nothing and only sets the timer 6 hours ahead, so a later sign-in is
 * seen too.
 *
 * Every circle is read, not only the one on screen, since a reminder must come whatever the user is
 * looking at. The circles are read at the same time, so they share the wait for the server.
 *
 * It reads the appointments with `getAppointments`, which asks the server first. That is how a
 * check sees the changes made on other phones while this app was closed. It waits for the server
 * for at most 5 seconds, since the alarm that runs it only has a few seconds. Offline, or when the
 * server is too slow, it plans from what the phone has already loaded, and sets the timer 15
 * minutes ahead at the latest, to ask the server again soon.
 *
 * What the phone has loaded can miss an appointment saved on another phone. So after a read from
 * the cache, the check shows what is due but keeps the last check, and only a read from the server
 * moves it. A reminder the cache missed then still comes with the next answer from the server, if
 * its appointment hasn't started. To not show a reminder twice, the store remembers the reminders
 * shown since the last check, and forgets them when the last check moves. Otherwise each try would
 * show them again, and bring back the ones the user had swiped away.
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
 * If the profile, the last check or the appointments of a circle can't be read, it shows nothing
 * and keeps the last check. It sets the timer 15 minutes ahead, and that run shows what is due
 * then. A circle the user can't read, or with an invalid id, is skipped instead: trying again
 * wouldn't help, and it would hold back the other circles. Two runs never overlap: the second
 * waits.
 *
 * @param zone the phone's time zone, read at each run.
 * @param nowMillis the current time.
 */
class AppointmentReminderCheck(
    private val appointmentRepository: AppointmentRepository,
    private val profileRepository: ProfileRepository,
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
   *
   * @throws java.io.IOException if the last check can't be read. The timer is still set.
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
   * Shows the reminders due since the user's last check that it hasn't shown yet. After a read from
   * the server, stores [now] as the last check. After a read from the cache, keeps it and remembers
   * what it showed.
   *
   * @return when the timer goes off next: the first reminder within 6 hours, or 6 hours from now.
   *   15 minutes from now at the latest after a read from the cache, and 15 minutes from now if the
   *   profile or the appointments can't be read.
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
    if (profile.circleIds.isEmpty()) return recheckAt
    val last = store.lastCheckedAt(uid) ?: now
    val alreadyShown = store.shownSinceLastCheck(uid)
    val zone = zone()
    val today = localDateOf(now, zone)
    val reads = readEveryCircle(profile.circleIds, today) ?: return retryAt
    val window = dayWindow(today, WINDOW_DAYS, zone)

    var timerAt = recheckAt
    val shown = mutableSetOf<String>()
    for ((circleId, read) in reads) {
      val occurrences =
          read.appointments.filter { it.isLinkedTo(uid) }.flatMap { it.occurrencesIn(window, zone) }
      planReminders(occurrences, last, now)
          .filter { it.occurrence.startAt > now }
          // soonest first, so the last one of each occurrence is the latest
          .associateBy { it.occurrence.id to it.occurrence.startAt }
          .values
          .filter { it.key !in alreadyShown }
          .forEach {
            post(circleId, it)
            shown += it.key
          }
      // planReminders only keeps the reminders until recheckAt, so the timer is never set more
      // than 6 hours ahead. A reminder further away is planned by a later check.
      planReminders(occurrences, now, recheckAt).firstOrNull()?.let {
        timerAt = minOf(timerAt, it.fireAt)
      }
    }
    if (reads.values.any { it.isFromCache }) {
      store.addShown(uid, shown)
      return minOf(timerAt, retryAt)
    }
    store.setLastCheckedAt(uid, now)
    return timerAt
  }

  /** Names the reminder, so the store can tell it was shown. */
  private val PlannedReminder.key: String
    get() = "${occurrence.id}@${occurrence.startAt}@$minutesBefore"

  /**
   * Reads the appointments of [circleIds] at the same time.
   *
   * @return the read of each circle by id, without the circles the user can't read. Null if a
   *   circle failed for another reason, so the check tries again.
   */
  private suspend fun readEveryCircle(
      circleIds: List<String>,
      today: LocalDate,
  ): Map<String, AppointmentsRead>? {
    val results = coroutineScope {
      circleIds
          .map { id ->
            async { id to appointmentRepository.getAppointments(id, today, WINDOW_DAYS) }
          }
          .awaitAll()
    }
    val reads = mutableMapOf<String, AppointmentsRead>()
    for ((circleId, result) in results) {
      result
          .onSuccess { reads[circleId] = it }
          .onFailure {
            Log.w(TAG, "Couldn't read the appointments of $circleId", it)
            if (
                it !is AppointmentError.PermissionDenied && it !is AppointmentError.InvalidCircleId
            ) {
              return null
            }
          }
    }
    return reads
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

    /**
     * How soon the check tries again after it couldn't read the profile, the last check or the
     * appointments, or after it read them from the cache.
     */
    val RETRY_MILLIS = 15.minutes.inWholeMilliseconds

    /**
     * How many days of appointments are read, from today. A due reminder belongs to an appointment
     * starting within 7 days, its longest reminder. The next one, within 6 hours, belongs to one
     * starting within 7 days and 6 hours. That can end on the 9th day, counting today.
     */
    const val WINDOW_DAYS = 9
  }
}
