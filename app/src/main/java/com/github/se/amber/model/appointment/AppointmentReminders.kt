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
