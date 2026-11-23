// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.isLinkedTo
import com.github.se.amber.model.appointment.occurrencesIn
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.localDateOf
import com.github.se.amber.model.calendar.startDateTime
import com.github.se.amber.model.circle.displayNameOf
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * A reminder whose time has come, as its WorkManager job receives it. It is not checked yet: see
 * [AppointmentReminderPoster].
 *
 * @property circleId the circle of the appointment.
 * @property appointmentId the appointment to remind.
 * @property startAt when the occurrence starts, in epoch milliseconds, as it was when the reminder
 *   was scheduled. Usually days after the reminder comes.
 * @property minutesBefore the value of [Appointment.reminderMinutesBefore] this reminder is for.
 * @property notificationTag identifies the notification, so showing it again replaces it.
 */
data class DueReminder(
    val circleId: String,
    val appointmentId: String,
    val startAt: Long,
    val minutesBefore: Int,
    val notificationTag: String,
)

/**
 * Shows a reminder once it is due, if it is still wanted.
 *
 * A reminder is scheduled days before it comes. Meanwhile, the appointment may have been moved or
 * deleted, its reminder times changed, or the user removed from the attendees. The phone may also
 * have been offline, so this phone couldn't know. So it reads the appointment again first, and
 * shows the reminder only if all of these still hold:
 * - `occurrencesIn` still gives an occurrence at [DueReminder.startAt];
 * - [DueReminder.minutesBefore] is still one of its reminder times;
 * - the signed-in user is still linked to it (see `isLinkedTo`).
 *
 * It reads only the day the appointment starts on, found from [DueReminder.startAt], not today. So
 * a reminder 3 days before reads the day 3 days ahead, which holds its appointment.
 *
 * [AppointmentReminderWorker][com.github.se.amber.data.work.AppointmentReminderWorker] calls it
 * when the reminder is due, and [AppointmentReminderNotifier] shows the notification.
 *
 * The names of the other people are best effort. If the circle or a profile can't be read, the
 * reminder still comes, without those names.
 *
 * @param zone the phone's time zone, read when the reminder comes.
 * @param nowMillis the current time.
 */
class AppointmentReminderPoster(
    private val appointmentRepository: AppointmentRepository,
    private val careCircleRepository: CareCircleRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val notifier: AppointmentReminderNotifier,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

  /**
   * Shows [reminder] if it is still wanted.
   *
   * @return true if it was shown, false if it isn't wanted anymore.
   * @throws Exception if the appointments can't be read, for example once the user left the circle.
   */
  suspend fun post(reminder: DueReminder): Boolean {
    val uid = authRepository.currentUser?.uid ?: return false
    val zone = zone()
    // The day the appointment starts on, from the start the job was given. Not today: for a
    // reminder 3 days before, it is 3 days from now, and that day holds the appointment.
    val appointmentDay = localDateOf(reminder.startAt, zone)
    // occurrencesIn takes the zone too: a repeating appointment will need it to keep its clock time
    val window = dayWindow(appointmentDay, 1, zone)
    val occurrence =
        appointmentRepository
            .observeAppointments(reminder.circleId, appointmentDay, 1)
            .first()
            .map { it.value }
            .firstOrNull { it.id == reminder.appointmentId }
            ?.occurrencesIn(window, zone)
            ?.firstOrNull { it.startAt == reminder.startAt } ?: return false
    if (reminder.minutesBefore !in occurrence.reminderMinutesBefore) return false
    if (!occurrence.isLinkedTo(uid)) return false
    notifier.show(
        ReminderNotification(
            tag = reminder.notificationTag,
            title = occurrence.title,
            start = occurrence.startDateTime(zone),
            today = localDateOf(nowMillis(), zone),
            otherNames = otherNames(reminder.circleId, occurrence, uid),
        )
    )
    return true
  }

  /**
   * The names of the people linked to [occurrence], except the reader [uid]. On the patient's
   * phone, these are the attendees. On an attendee's phone, they are the patient and the other
   * attendees.
   *
   * Each name is the one [uid] sees, from `displayNameOf`: their own nickname for that person
   * first. The circle and the profiles are read only when someone else is linked.
   *
   * It is best effort, so it never fails. A person whose name can't be found is left out, and if
   * the circle can't be read, no name is given.
   *
   * @param circleId the circle of the appointment.
   * @param occurrence the occurrence the reminder is for.
   * @param uid the signed-in user, who reads the reminder.
   * @return the names, the patient first, then the attendees in their order. Empty when nobody else
   *   is linked, or when no name could be found.
   */
  private suspend fun otherNames(
      circleId: String,
      occurrence: Appointment,
      uid: String,
  ): List<String> {
    val ids =
        (listOf(occurrence.patientId) + occurrence.attendeeIds).distinct().filter {
          it.isNotBlank() && it != uid
        }
    if (ids.isEmpty()) return emptyList()
    val circle = careCircleRepository.getCircle(circleId).getOrNull() ?: return emptyList()
    val members =
        ids.filter { it in circle.memberIds }
            .mapNotNull { id ->
              profileRepository.getProfile(id).getOrNull()?.let { id to it.person }
            }
            .toMap()
    return ids.mapNotNull { circle.displayNameOf(it, uid, members) }
  }
}
