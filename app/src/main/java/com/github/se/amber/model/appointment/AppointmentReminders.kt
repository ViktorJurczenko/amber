// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

/** The reminders an appointment gets when nobody chose any: one, 1 day before its start. */
val DEFAULT_REMINDER_MINUTES: List<Int> = listOf(24 * 60)

/** The most reminders one appointment may have. A longer list is refused. */
const val MAX_REMINDERS = 5

/** The earliest a reminder may come before the start: 7 days, in minutes. */
const val MAX_REMINDER_MINUTES = 7 * 24 * 60

/**
 * Whether the user [uid] is linked to this appointment, and so gets its reminders.
 *
 * The linked people are [Appointment.patientId] and each member in [Appointment.attendeeIds].
 * Everyone else in the circle is not linked. Creating an appointment doesn't link you to it: a
 * caregiver who plans it for others gets no reminder unless they are an attendee too.
 *
 * @param uid the user to check, usually the one signed in on this phone.
 * @return true if [uid] is the patient or an attendee.
 */
fun Appointment.isLinkedTo(uid: String): Boolean = uid == patientId || uid in attendeeIds

/**
 * One reminder to show on this phone: [occurrence] starts in [minutesBefore] minutes, and the
 * notification comes at [fireAt].
 *
 * @property key identifies this reminder among all the reminders of the circle. It is
 *   `appointmentId@start@minutes`, so it stays the same as long as the appointment keeps its start
 *   and this reminder time. It is the WorkManager work name. Changing its format leaves the
 *   reminders already scheduled under the old name, and they fire twice.
 * @property occurrence the occurrence the reminder is for, from `occurrencesIn`.
 * @property minutesBefore the value of [Appointment.reminderMinutesBefore] this reminder is for.
 *   The reminder checks it is still there before it shows anything.
 * @property fireAt when the notification comes, in epoch milliseconds: [minutesBefore] minutes
 *   before the occurrence starts.
 */
data class PlannedReminder(
    val key: String,
    val occurrence: Appointment,
    val minutesBefore: Int,
    val fireAt: Long,
)

/**
 * The reminders of [occurrences] that come from [nowMillis] until [horizonMillis], soonest first.
 *
 * Each value of [Appointment.reminderMinutesBefore] gives one reminder, that many minutes before
 * the occurrence starts. A value listed twice gives one reminder. A reminder before [nowMillis] has
 * passed and is left out. One at [nowMillis] is kept, so it shows right away. One at
 * [horizonMillis] or after is left out: a later sync plans it.
 *
 * It only reads the id, the start and the reminder times. So an appointment that is not synced yet
 * gets its reminders too: it has its id from the moment it is created, even offline.
 *
 * It doesn't check who is linked to the appointments. The caller passes only the ones the user is
 * linked to (see [isLinkedTo]).
 *
 * @param occurrences the occurrences to plan, from `occurrencesIn`.
 * @param nowMillis the current time, in epoch milliseconds.
 * @param horizonMillis the end of the planned period, in epoch milliseconds. It is not part of it.
 * @return the reminders, sorted by [PlannedReminder.fireAt], then by [PlannedReminder.key]. The
 *   list is empty when there are none.
 */
fun planReminders(
    occurrences: List<Appointment>,
    nowMillis: Long,
    horizonMillis: Long,
): List<PlannedReminder> =
    occurrences
        .flatMap { occurrence ->
          occurrence.reminderMinutesBefore.distinct().map { minutes ->
            PlannedReminder(
                key = "${occurrence.id}@${occurrence.startAt}@$minutes",
                occurrence = occurrence,
                minutesBefore = minutes,
                fireAt = occurrence.startAt - minutes * MILLIS_PER_MINUTE,
            )
          }
        }
        .filter { it.fireAt in nowMillis..<horizonMillis }
        .sortedWith(compareBy({ it.fireAt }, { it.key }))

private const val MILLIS_PER_MINUTE = 60_000L
