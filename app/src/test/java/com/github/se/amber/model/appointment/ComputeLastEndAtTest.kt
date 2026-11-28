// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.toEpochMillis
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests [computeLastEndAt]: the end of the last occurrence, which the repository stores in
 * [Appointment.lastEndAt]. The dates are real ones in Zurich. 3 November 2026 is a Tuesday.
 */
class ComputeLastEndAtTest {

  private val zone = ZoneId.of("Europe/Zurich")
  private val weekly = Recurrence(unit = RecurrenceUnit.WEEK)
  private val monthly = Recurrence(unit = RecurrenceUnit.MONTH)

  /** @return the given clock time in Zurich, in epoch milliseconds. */
  private fun time(year: Int, month: Int, day: Int, hour: Int = 10) =
      LocalDateTime.of(year, month, day, hour, 0).toEpochMillis(zone)

  /** @return [minutes] in milliseconds. */
  private fun minutes(minutes: Int) = minutes.minutes.inWholeMilliseconds

  /** @return an appointment that starts at [startAt], lasts [durationMinutes] and repeats so. */
  private fun at(startAt: Long, recurrence: Recurrence? = null, durationMinutes: Int? = null) =
      Appointment(
          id = "a1",
          title = "Physio",
          startAt = startAt,
          durationMinutes = durationMinutes,
          recurrence = recurrence,
      )

  @Test
  fun aOneOffAppointmentEndsAtItsOwnEnd() {
    val start = time(2026, 11, 3)

    assertEquals(start + minutes(30), at(start, durationMinutes = 30).computeLastEndAt(zone))
    // An unknown duration counts as 60 minutes.
    assertEquals(start + minutes(60), at(start).computeLastEndAt(zone))
  }

  @Test
  fun aSeriesWithoutAnEndNeverEnds() {
    assertEquals(Long.MAX_VALUE, at(time(2026, 11, 3), weekly).computeLastEndAt(zone))
  }

  @Test
  fun aSeriesEndsWithItsOccurrenceThatStartsAtItsLastStart() {
    val series = at(time(2026, 11, 3), weekly.copy(untilAt = time(2026, 11, 17)), 45)

    assertEquals(time(2026, 11, 17) + minutes(45), series.computeLastEndAt(zone))
  }

  @Test
  fun aSeriesEndsWithItsLastOccurrenceBeforeItsLastStart() {
    // 20 November is a Friday: the last occurrence is on Tuesday the 17th.
    val series = at(time(2026, 11, 3), weekly.copy(untilAt = time(2026, 11, 20)))

    assertEquals(time(2026, 11, 17) + minutes(60), series.computeLastEndAt(zone))
  }

  @Test
  fun aSeriesEveryOtherWeekEndsOnItsOwnWeek() {
    // untilAt is on the 24th, but the occurrences are on the 3rd and the 17th.
    val series = at(time(2026, 11, 3), weekly.copy(every = 2, untilAt = time(2026, 11, 24, 9)))

    assertEquals(time(2026, 11, 17) + minutes(60), series.computeLastEndAt(zone))
  }

  @Test
  fun aSeriesThatEndsAtItsFirstStartHasOneOccurrence() {
    val start = time(2026, 11, 3)

    assertEquals(
        start + minutes(60),
        at(start, weekly.copy(untilAt = start)).computeLastEndAt(zone),
    )
  }

  @Test
  fun aMonthlySeriesOnThe31stEndsOnTheLastDayOfAShorterMonth() {
    // A whole month hasn't passed from 31 January to 28 February, yet 28 February is an
    // occurrence: it must not be missed.
    val series = at(time(2026, 1, 31), monthly.copy(untilAt = time(2026, 2, 28, 12)))

    assertEquals(time(2026, 2, 28) + minutes(60), series.computeLastEndAt(zone))
  }

  @Test
  fun aSeriesKeepsItsClockTimeUpToItsLastOccurrence() {
    // The clocks go forward on 29 March: the last occurrence is still at 10:00.
    val series = at(time(2026, 3, 23), weekly.copy(untilAt = time(2026, 3, 30)))

    assertEquals(time(2026, 3, 30, 11), series.computeLastEndAt(zone))
  }

  @Test
  fun aSeriesWithEveryBelowOneEndsAtItsFirstEnd() {
    // Only a document written by hand can hold it: validationError refuses it.
    val start = time(2026, 11, 3)

    assertEquals(
        start + minutes(60),
        at(start, weekly.copy(every = 0, untilAt = null)).computeLastEndAt(zone),
    )
  }
}
