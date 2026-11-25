// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What a reminder notification shows: when, what and who else is coming. It never names the person
 * reading it.
 *
 * @property tag identifies the notification. Showing a reminder with the same tag replaces it.
 * @property title the appointment's title.
 * @property start when the occurrence starts, as a clock time in the phone's zone.
 * @property today the day the reminder is shown, to write "Today" or "Tomorrow".
 * @property otherNames the other people linked to the appointment, ready to show. Empty when nobody
 *   else is, or when no name could be found.
 */
data class ReminderNotification(
    val tag: String,
    val title: String,
    val start: LocalDateTime,
    val today: LocalDate,
    val otherNames: List<String>,
)

/**
 * Shows the appointment reminders in the phone's notification shade.
 *
 * Only [AppointmentReminderPoster] uses it. It gets everything to show in a [ReminderNotification],
 * so it reads no data and decides nothing: the poster has already checked who is signed in and
 * found the names. Its tests can use a fake that records what it is given.
 */
fun interface AppointmentReminderNotifier {

  /**
   * Shows [reminder], or replaces the notification that has the same [ReminderNotification.tag].
   *
   * It can show nothing, for example when the user hasn't allowed notifications. The caller isn't
   * told, and the reminder is then lost.
   */
  fun show(reminder: ReminderNotification)
}
