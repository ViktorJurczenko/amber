// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.PlannedReminder
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.calendar.occurrenceKey
import com.github.se.amber.model.calendar.startDateTime
import com.github.se.amber.model.calendar.toLocalDate
import com.github.se.amber.model.circle.displayNameOf
import java.time.ZoneId

/**
 * Shows a reminder it is given. It doesn't decide when, or whether, a reminder is due: the reminder
 * check does, from the current appointments, and calls it for each reminder due. So it doesn't read
 * the appointment again.
 *
 * It adds the names of the other people linked to the appointment, then
 * [AppointmentReminderNotifier] shows the notification. The names are best effort: if the circle or
 * a profile can't be read, the reminder still comes, without those names.
 *
 * @param zone the phone's time zone, read when the reminder comes.
 * @param nowMillis the current time.
 */
class AppointmentReminderPoster(
    private val careCircleRepository: CareCircleRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val notifier: AppointmentReminderNotifier,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

  /**
   * Shows [reminder], for the signed-in user.
   *
   * @param circleId the circle of the appointment, to find the names.
   * @return true if it was shown, false if nobody is signed in.
   */
  suspend fun post(circleId: String, reminder: PlannedReminder): Boolean {
    val uid = authRepository.currentUser?.uid ?: return false
    val zone = zone()
    val occurrence = reminder.occurrence
    notifier.show(
        ReminderNotification(
            // Every reminder of one occurrence gets the same tag, so a later one replaces the
            // earlier one in the notification shade, and the occurrence is never shown twice.
            tag = occurrence.occurrenceKey(),
            title = occurrence.title,
            start = occurrence.startDateTime(zone),
            today = nowMillis().toLocalDate(zone),
            otherNames = otherNames(circleId, occurrence, uid),
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
