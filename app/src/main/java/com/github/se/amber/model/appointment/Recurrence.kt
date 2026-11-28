// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import java.time.temporal.ChronoUnit

/**
 * The calendar unit a repeating appointment counts in. It holds only what belongs to the unit: the
 * [ChronoUnit] for the date arithmetic. How many units lie between two occurrences is
 * [Recurrence.every], so every rhythm works without a new value here.
 *
 * It is a calendar unit, not a number of days, because a month doesn't always have the same number
 * of days. Firestore stores only the name of the unit, such as "WEEK".
 *
 * A document with a unit this version of the app doesn't know can't be read, and is skipped. So
 * adding a unit makes older versions of the app skip the appointments that use it.
 *
 * @property chronoUnit the unit `java.time` adds, in the phone's time zone.
 */
enum class RecurrenceUnit(val chronoUnit: ChronoUnit) {
  DAY(ChronoUnit.DAYS),
  WEEK(ChronoUnit.WEEKS),
  MONTH(ChronoUnit.MONTHS),
}

/**
 * How an appointment repeats, such as physio every Tuesday. It is stored in
 * [Appointment.recurrence]. An appointment without it happens once.
 *
 * The first occurrence starts at [Appointment.startAt]. The nth one starts [every] times n [unit]s
 * later, at the same clock time (see `occurrencesIn`).
 *
 * Every field has a default, so Firestore can read it back.
 *
 * @property unit the calendar unit the series counts in.
 * @property every how many [unit]s lie between two occurrences. It is at least 1. 2 with
 *   [RecurrenceUnit.WEEK] means every 2 week.
 * @property untilAt the last time an occurrence may start, in epoch milliseconds. It is not before
 *   [Appointment.startAt]. Null means the series has no end.
 * @property cancelledStarts the start of each occurrence cancelled on its own, in epoch
 *   milliseconds. The series stays as it is, and only those occurrences are left out. For a weekly
 *   physio on Tuesdays at 10:00 with no session on 24 November, it holds 24 November 10:00. It
 *   holds exact start times: if the series moves, the old cancelled starts no longer match any
 *   occurrence and are simply ignored.
 */
data class Recurrence(
    val unit: RecurrenceUnit = RecurrenceUnit.WEEK,
    val every: Int = 1,
    val untilAt: Long? = null,
    val cancelledStarts: List<Long> = emptyList(),
)
