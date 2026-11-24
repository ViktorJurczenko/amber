// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import kotlin.time.Duration.Companion.minutes

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
 * One reminder to show on this phone: the notification for [occurrence] comes at [fireAt].
 *
 * @property occurrence the occurrence the reminder is for, from `occurrencesIn`.
 * @property minutesBefore the value of [Appointment.reminderMinutesBefore] this reminder is for.
 * @property fireAt when the notification comes, in epoch milliseconds. Usually [minutesBefore]
 *   minutes before the occurrence starts, or later when the appointment was saved after that time
 *   (see [planReminders]).
 */
data class PlannedReminder(val occurrence: Appointment, val minutesBefore: Int, val fireAt: Long)

/**
 * The reminders of [occurrences] that come after [afterMillis] and until [untilMillis], soonest
 * first.
 *
 * Each reminder time gives one reminder, that many minutes before the start, but never before the
 * appointment was last saved. So a reminder whose time had already passed comes at the save, and an
 * edit made after a reminder's time shows it again. No reminder comes after the start.
 *
 * The caller passes only the occurrences the user is linked to (see [isLinkedTo]).
 *
 * @param occurrences the occurrences to plan, from `occurrencesIn`.
 * @param afterMillis the start of the period, usually the last check. It is left out.
 * @param untilMillis the end of the period, usually now. It is kept.
 * @return the reminders, sorted by [PlannedReminder.fireAt], then by appointment and by start. It
 *   is empty when there are none.
 */
fun planReminders(
    occurrences: List<Appointment>,
    afterMillis: Long,
    untilMillis: Long,
): List<PlannedReminder> =
    occurrences
        .asSequence()
        .flatMap { occurrence ->
          occurrence.reminderMinutesBefore
              .asSequence()
              // closest to the start first, so distinctBy below keeps that one
              .sorted()
              // minutes before the start, but not before the last save
              .map { minutes ->
                PlannedReminder(
                    occurrence = occurrence,
                    minutesBefore = minutes,
                    fireAt =
                        maxOf(
                            occurrence.startAt - minutes.minutes.inWholeMilliseconds,
                            occurrence.updatedAt,
                        ),
                )
              }
              // never after the start
              .filter { it.fireAt <= occurrence.startAt }
              // one reminder per time: a time listed twice, or several moved to the same save
              .distinctBy { it.fireAt }
        }
        // after afterMillis (excluded) and until untilMillis (included): times are whole
        // milliseconds
        .filter { it.fireAt in afterMillis + 1..untilMillis }
        .sortedWith(compareBy({ it.fireAt }, { it.occurrence.id }, { it.occurrence.startAt }))
        .toList()
