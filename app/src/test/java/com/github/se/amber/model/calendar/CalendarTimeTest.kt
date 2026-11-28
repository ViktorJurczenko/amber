// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import com.github.se.amber.model.appointment.Appointment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the clock time helpers of CalendarTime.kt.
 *
 * The expected times are built with ZonedDateTime from fixed dates, so the tests don't depend on
 * the zone of the machine running them. The daylight saving dates are the European ones of 2026:
 * clocks go from 02:00 to 03:00 on 29 March, and from 03:00 back to 02:00 on 25 October.
 */
class CalendarTimeTest {

  private val zurich = ZoneId.of("Europe/Zurich")
  private val tokyo = ZoneId.of("Asia/Tokyo")

  /** @return the epoch milliseconds of this clock time in Zurich, with the offset it really has. */
  private fun zurichMillis(dateTime: LocalDateTime): Long =
      ZonedDateTime.of(dateTime, zurich).toInstant().toEpochMilli()

  @Test
  fun aTimeGivesTheDayAndTheClockTimeInTheGivenZone() {
    // 23:30 in Zurich is already 07:30 the next day in Tokyo
    val lateEvening = zurichMillis(LocalDateTime.of(2026, 11, 24, 23, 30))

    assertEquals(LocalDate.of(2026, 11, 24), lateEvening.toLocalDate(zurich))
    assertEquals(LocalDate.of(2026, 11, 25), lateEvening.toLocalDate(tokyo))
    assertEquals(LocalDateTime.of(2026, 11, 24, 23, 30), lateEvening.toLocalDateTime(zurich))
    assertEquals(LocalDateTime.of(2026, 11, 25, 7, 30), lateEvening.toLocalDateTime(tokyo))
  }

  @Test
  fun startAndEndAreTheClockTimesInTheGivenZone() {
    val start = LocalDateTime.of(2026, 11, 24, 14, 0)
    val event = Appointment(startAt = zurichMillis(start), durationMinutes = 45)

    assertEquals(start, event.startDateTime(zurich))
    assertEquals(LocalDateTime.of(2026, 11, 24, 14, 45), event.endDateTime(zurich))
    // Tokyo is 8 hours ahead of Zurich in winter.
    assertEquals(LocalDateTime.of(2026, 11, 24, 22, 0), event.startDateTime(tokyo))
  }

  @Test
  fun theEndOfAnEventWithoutDurationIsOneHourLater() {
    val event = Appointment(startAt = zurichMillis(LocalDateTime.of(2026, 11, 24, 23, 30)))

    // It also shows that the end can fall on the next day.
    assertEquals(LocalDateTime.of(2026, 11, 25, 0, 30), event.endDateTime(zurich))
  }

  @Test
  fun anEventAcrossTheSpringChangeEndsAtTheRightClockTime() {
    // 01:30 plus 60 minutes is 03:30, since 02:00 to 03:00 doesn't exist that night.
    val event = Appointment(startAt = zurichMillis(LocalDateTime.of(2026, 3, 29, 1, 30)))

    assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), event.endDateTime(zurich))
  }

  @Test
  fun toEpochMillisGivesBackTheStoredTimeOnANormalDay() {
    val startAt = zurichMillis(LocalDateTime.of(2026, 11, 24, 14, 0))
    val event = Appointment(startAt = startAt)

    assertEquals(startAt, event.startDateTime(zurich).toEpochMillis(zurich))
  }

  @Test
  fun aTimeInTheSpringGapMovesForwardByTheGap() {
    val inTheGap = LocalDateTime.of(2026, 3, 29, 2, 30)

    val millis = inTheGap.toEpochMillis(zurich)

    val shown = Appointment(startAt = millis).startDateTime(zurich)
    assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), shown)
  }

  @Test
  fun aTimeInTheAutumnOverlapTakesTheEarlierOne() {
    val twice = LocalDateTime.of(2026, 10, 25, 2, 30)
    // The earlier 02:30 is still on summer time, at UTC+2, so it is 00:30 UTC.
    val earlier = ZonedDateTime.parse("2026-10-25T00:30:00Z").toInstant().toEpochMilli()

    assertEquals(earlier, twice.toEpochMillis(zurich))
  }
}
