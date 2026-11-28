// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.DayWindow
import com.github.se.amber.model.calendar.endAt
import com.github.se.amber.model.calendar.overlaps
import com.github.se.amber.model.calendar.startDateTime
import com.github.se.amber.model.calendar.toEpochMillis
import com.github.se.amber.model.calendar.toLocalDateTime
import java.time.ZoneId

/**
 * The occurrences of this appointment that overlap [window], sorted by start. Each one is a copy of
 * the appointment with its own [Appointment.startAt], so the status helpers work on it as they are.
 * The calendar screens and the reminders read appointments through this function.
 *
 * An appointment without [Appointment.recurrence] happens once. It gives the appointment itself if
 * it overlaps the window, and nothing otherwise.
 *
 * A repeating appointment gives every occurrence that overlaps the window:
 * - the nth occurrence starts at the first start plus [Recurrence.every] times n units. Adding the
 *   units again and again would drift: after 31 January would come 28 February, then 28 March;
 * - the units are added to the clock time in [zone], so a weekly physio at 10:00 stays at 10:00
 *   across a daylight saving change;
 * - a monthly appointment on the 31st falls on the last day of shorter months, and on the 31st
 *   again after them;
 * - no occurrence starts after [Recurrence.untilAt];
 * - the starts in [Recurrence.cancelledStarts] are left out.
 *
 * An occurrence that started before the window and isn't over is given too.
 *
 * Each occurrence is a copy, for display and reminders only. The copies of one series share their
 * [Appointment.id] and differ by [Appointment.startAt]. Edits and cancellations take the
 * appointment from `observeAppointments`, never a copy: saving a copy moves the whole series to
 * that day. For the same reason, the key of a list item must be the id plus [Appointment.startAt],
 * not the id alone.
 *
 * A series with [Recurrence.every] below 1 can only come from a document written by hand, since
 * [validationError] refuses it. It is given as a one-off appointment, at its first start.
 *
 * @param window the days to show.
 * @param zone the time zone the days and the clock times are counted in, usually the phone's.
 * @return the occurrences that overlap [window]. The list is empty when there are none.
 */
fun Appointment.occurrencesIn(window: DayWindow, zone: ZoneId): List<Appointment> {
  val recurrence = validRecurrence() ?: return if (overlaps(window)) listOf(this) else emptyList()
  val untilAt = recurrence.untilAt ?: Long.MAX_VALUE
  val cancelled = recurrence.cancelledStarts.toSet()
  // Skip the occurrences that are over before the window starts. Start one occurrence early: the
  // one just before the window may still be going on when the window starts.
  val firstIndex =
      maxOf(0L, unitsUntil(window.startMillis, recurrence, zone) / recurrence.every - 1)

  return generateSequence(firstIndex) { it + 1 }
      .map { index -> nthStart(index, recurrence, zone) }
      .takeWhile { it < window.endMillis && it <= untilAt }
      .filter { it !in cancelled }
      .map { copy(startAt = it) }
      .filter { it.overlaps(window) }
      .toList()
}

/**
 * When the last occurrence of this appointment ends, in epoch milliseconds. The repository stores
 * it in [Appointment.lastEndAt], which the calendar's query filters on.
 * - a one-off appointment gives its `endAt()`;
 * - a series with [Recurrence.untilAt] gives the end of its last occurrence that starts at or
 *   before it;
 * - a series without an end gives [Long.MAX_VALUE], so it stays in every later window.
 *
 * [Recurrence.cancelledStarts] are ignored. If the last occurrence is cancelled, the result is its
 * end anyway, which only keeps the series in a window where it has nothing to show.
 *
 * A series with [Recurrence.every] below 1 counts as a one-off appointment, as in [occurrencesIn].
 *
 * @param zone the time zone the clock times are counted in, usually the phone's. It must be the one
 *   [occurrencesIn] uses, so both find the same occurrences.
 * @return the end of the last occurrence, or [Long.MAX_VALUE] for a series without an end.
 */
fun Appointment.computeLastEndAt(zone: ZoneId): Long {
  val recurrence = validRecurrence() ?: return endAt()
  val untilAt = recurrence.untilAt ?: return Long.MAX_VALUE
  // An occurrence that starts at or before untilAt. A month may have fewer days than the first one,
  // so it can be one too early: move on while the next occurrence still starts before untilAt.
  var index = maxOf(0L, unitsUntil(untilAt, recurrence, zone) / recurrence.every)
  while (nthStart(index + 1, recurrence, zone) <= untilAt) index++
  return copy(startAt = nthStart(index, recurrence, zone)).endAt()
}

/**
 * Whether this appointment repeats, so [occurrencesIn] can give it several times. A screen can use
 * it to mark a series.
 *
 * A [Recurrence] with [Recurrence.every] below 1 can only come from a document written by hand. It
 * counts as not repeating, like in [occurrencesIn].
 *
 * @return true if it has a [Recurrence] with [Recurrence.every] of at least 1.
 */
fun Appointment.isRepeating(): Boolean = validRecurrence() != null

/**
 * @return [Appointment.recurrence], or null if there is none or its [Recurrence.every] is below 1.
 */
private fun Appointment.validRecurrence(): Recurrence? = recurrence?.takeIf { it.every >= 1 }

/**
 * The start of occurrence [index] of the series, counted from the first start at 0.
 *
 * @return the first start plus [Recurrence.every] times [index] units, at the same clock time in
 *   [zone], in epoch milliseconds.
 */
private fun Appointment.nthStart(index: Long, recurrence: Recurrence, zone: ZoneId): Long =
    startDateTime(zone)
        .plus(index * recurrence.every, recurrence.unit.chronoUnit)
        .toEpochMillis(zone)

/**
 * How many whole units of [recurrence] lie between the first start and [millis], in [zone]. The
 * start that many units after the first is never after [millis]. It is negative when [millis] is
 * before the first start.
 *
 * For example, for a weekly appointment first on Tuesday 3 November at 10:00:
 * - until Tuesday 17 November at 09:00, it is 1: the third Tuesday, at 10:00, is after it;
 * - until Tuesday 17 November at 10:00, it is 2.
 *
 * For a monthly appointment first on 31 January, until 28 February it is 0: a whole month from the
 * 31st hasn't passed, although 28 February is an occurrence. That is why [computeLastEndAt] checks
 * the next start too.
 */
private fun Appointment.unitsUntil(millis: Long, recurrence: Recurrence, zone: ZoneId): Long =
    recurrence.unit.chronoUnit.between(startDateTime(zone), millis.toLocalDateTime(zone))
