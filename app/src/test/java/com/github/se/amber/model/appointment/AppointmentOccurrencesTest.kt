// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.DayWindow
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.occurrenceKey
import com.github.se.amber.model.calendar.startDateTime
import com.github.se.amber.model.calendar.toEpochMillis
import com.github.se.amber.model.location.GeoLocation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [occurrencesIn], for one-off and repeating appointments.
 *
 * It relies on `overlaps`, so the edges of the window (an appointment that ends right at its start,
 * or starts right at its end) are left to DayWindowTest. The dates are real ones in Zurich, where
 * the clocks change on 29 March and 25 October 2026. 3 November 2026 is a Tuesday.
 */
class AppointmentOccurrencesTest {

  private val zone = ZoneId.of("Europe/Zurich")
  private val hour = 1.hours.inWholeMilliseconds

  /** @return a one-hour appointment that starts at [startAt] and repeats as [recurrence]. */
  private fun at(startAt: Long, recurrence: Recurrence? = null) =
      Appointment(id = "a1", title = "Physio", startAt = startAt, recurrence = recurrence)

  /** @return the given clock time in Zurich, in epoch milliseconds. */
  private fun time(year: Int, month: Int, day: Int, hour: Int = 10) =
      LocalDateTime.of(year, month, day, hour, 0).toEpochMillis(zone)

  /** @return the window of [days] days from the given day, in Zurich. */
  private fun days(year: Int, month: Int, day: Int, days: Int) =
      dayWindow(LocalDate.of(year, month, day), days, zone)

  /** @return the clock times the occurrences of [appointment] in [window] start at. */
  private fun startsIn(appointment: Appointment, window: DayWindow): List<LocalDateTime> =
      appointment.occurrencesIn(window, zone).map { it.startDateTime(zone) }

  /** @return the given clock times. */
  private fun clock(vararg times: Long): List<LocalDateTime> = times.map {
    at(it).startDateTime(zone)
  }

  // One-off appointments

  @Test
  fun anAppointmentInsideTheWindowIsGivenItself() {
    val window = DayWindow(startMillis = 100 * hour, endMillis = 124 * hour)
    val appointment = at(110 * hour)

    // The very same object: nothing is copied or changed for a one-off appointment.
    assertSame(appointment, appointment.occurrencesIn(window, zone).single())
  }

  @Test
  fun anAppointmentOutsideTheWindowIsNotGiven() {
    val window = DayWindow(startMillis = 100 * hour, endMillis = 124 * hour)

    assertEquals(emptyList<Appointment>(), at(10 * hour).occurrencesIn(window, zone))
    assertEquals(emptyList<Appointment>(), at(500 * hour).occurrencesIn(window, zone))
  }

  // Each unit, every 1 and more

  @Test
  fun aDailySeriesGivesEachDay() {
    val daily = at(time(2026, 11, 2), Recurrence(unit = RecurrenceUnit.DAY))

    assertEquals(
        clock(time(2026, 11, 2), time(2026, 11, 3), time(2026, 11, 4)),
        startsIn(daily, days(2026, 11, 2, 3)),
    )
  }

  @Test
  fun aSeriesEveryThreeDaysSkipsTheDaysBetween() {
    val series = at(time(2026, 11, 2), Recurrence(unit = RecurrenceUnit.DAY, every = 3))

    assertEquals(
        clock(time(2026, 11, 2), time(2026, 11, 5), time(2026, 11, 8), time(2026, 11, 11)),
        startsIn(series, days(2026, 11, 2, 10)),
    )
  }

  @Test
  fun aWeeklySeriesGivesTheSameWeekday() {
    val weekly = at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.WEEK))

    assertEquals(
        clock(time(2026, 11, 3), time(2026, 11, 10), time(2026, 11, 17)),
        startsIn(weekly, days(2026, 11, 1, 21)),
    )
  }

  @Test
  fun aSeriesEveryOtherWeekSkipsAWeek() {
    val series = at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.WEEK, every = 2))

    assertEquals(
        clock(time(2026, 11, 3), time(2026, 11, 17)),
        startsIn(series, days(2026, 11, 1, 28)),
    )
  }

  @Test
  fun aMonthlySeriesGivesTheSameDayOfTheMonth() {
    val monthly = at(time(2026, 1, 15), Recurrence(unit = RecurrenceUnit.MONTH))

    assertEquals(
        clock(time(2026, 1, 15), time(2026, 2, 15), time(2026, 3, 15), time(2026, 4, 15)),
        startsIn(monthly, days(2026, 1, 1, 120)),
    )
  }

  @Test
  fun aSeriesEveryThreeMonthsGivesOneDayAQuarter() {
    val series = at(time(2026, 1, 15), Recurrence(unit = RecurrenceUnit.MONTH, every = 3))

    assertEquals(
        clock(time(2026, 1, 15), time(2026, 4, 15), time(2026, 7, 15), time(2026, 10, 15)),
        startsIn(series, days(2026, 1, 1, 365)),
    )
  }

  // The calendar

  @Test
  fun aMonthlySeriesOnThe31stFallsOnTheLastDayOfShorterMonths() {
    val monthly = at(time(2026, 1, 31), Recurrence(unit = RecurrenceUnit.MONTH))

    // Counted from the first start: 31 March again after 28 February, not 28 March.
    assertEquals(
        clock(time(2026, 1, 31), time(2026, 2, 28), time(2026, 3, 31), time(2026, 4, 30)),
        startsIn(monthly, days(2026, 1, 1, 150)),
    )
  }

  @Test
  fun aMonthlySeriesOnThe31stFallsOnThe29thOfFebruaryInALeapYear() {
    val monthly = at(time(2028, 1, 31), Recurrence(unit = RecurrenceUnit.MONTH))

    assertEquals(clock(time(2028, 2, 29)), startsIn(monthly, days(2028, 2, 1, 29)))
  }

  @Test
  fun aWeeklySeriesKeepsItsClockTimeWhenTheClocksGoForward() {
    val weekly = at(time(2026, 3, 23), Recurrence(unit = RecurrenceUnit.WEEK))

    val occurrences = weekly.occurrencesIn(days(2026, 3, 23, 14), zone)

    assertEquals(
        clock(time(2026, 3, 23), time(2026, 3, 30)),
        occurrences.map { it.startDateTime(zone) },
    )
    // The week of the change is one hour short.
    assertEquals(7.days.inWholeMilliseconds - hour, occurrences[1].startAt - occurrences[0].startAt)
  }

  @Test
  fun aWeeklySeriesKeepsItsClockTimeWhenTheClocksGoBack() {
    val weekly = at(time(2026, 10, 19), Recurrence(unit = RecurrenceUnit.WEEK))

    val occurrences = weekly.occurrencesIn(days(2026, 10, 19, 14), zone)

    assertEquals(
        clock(time(2026, 10, 19), time(2026, 10, 26)),
        occurrences.map { it.startDateTime(zone) },
    )
    // The week of the change is one hour long.
    assertEquals(7.days.inWholeMilliseconds + hour, occurrences[1].startAt - occurrences[0].startAt)
  }

  // The end of the series, and cancelled occurrences

  @Test
  fun aSeriesStopsAtItsLastStart() {
    val weekly = Recurrence(unit = RecurrenceUnit.WEEK)
    val window = days(2026, 11, 1, 35)

    // An occurrence that starts right at untilAt is kept.
    assertEquals(
        clock(time(2026, 11, 3), time(2026, 11, 10), time(2026, 11, 17)),
        startsIn(at(time(2026, 11, 3), weekly.copy(untilAt = time(2026, 11, 17))), window),
    )
    assertEquals(
        clock(time(2026, 11, 3), time(2026, 11, 10)),
        startsIn(at(time(2026, 11, 3), weekly.copy(untilAt = time(2026, 11, 16))), window),
    )
  }

  @Test
  fun aSeriesThatEndedBeforeTheWindowGivesNothing() {
    val series =
        at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.WEEK, untilAt = time(2026, 11, 3)))

    assertEquals(emptyList<LocalDateTime>(), startsIn(series, days(2026, 12, 1, 7)))
  }

  @Test
  fun aSeriesWithoutAnEndKeepsGoingYearsLater() {
    val daily = at(time(2020, 1, 1), Recurrence(unit = RecurrenceUnit.DAY))

    assertEquals(
        clock(time(2030, 6, 10), time(2030, 6, 11)),
        startsIn(daily, days(2030, 6, 10, 2)),
    )
  }

  @Test
  fun aSeriesGivesNothingBeforeItsFirstStart() {
    val weekly = at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.WEEK))

    assertEquals(emptyList<LocalDateTime>(), startsIn(weekly, days(2026, 10, 1, 30)))
  }

  @Test
  fun aCancelledOccurrenceIsLeftOutAndTheOthersStay() {
    val weekly = Recurrence(unit = RecurrenceUnit.WEEK)
    val window = days(2026, 11, 1, 21)

    assertEquals(
        clock(time(2026, 11, 3), time(2026, 11, 17)),
        startsIn(
            at(time(2026, 11, 3), weekly.copy(cancelledStarts = listOf(time(2026, 11, 10)))),
            window,
        ),
    )
    // A start that isn't one of an occurrence, as after the series moved, changes nothing.
    assertEquals(
        clock(time(2026, 11, 3), time(2026, 11, 10), time(2026, 11, 17)),
        startsIn(
            at(time(2026, 11, 3), weekly.copy(cancelledStarts = listOf(time(2026, 11, 10, 11)))),
            window,
        ),
    )
  }

  // Occurrences around the window, and their fields

  @Test
  fun anOccurrenceThatStartedBeforeTheWindowAndIsNotOverIsGiven() {
    val lateNight =
        Appointment(
            id = "a1",
            title = "Night shift",
            startAt = time(2026, 11, 1, hour = 23),
            durationMinutes = 120,
            recurrence = Recurrence(unit = RecurrenceUnit.DAY),
        )

    // The 4 November one ends at 01:00 on the 5th. The 3 November one is over.
    assertEquals(
        clock(time(2026, 11, 4, hour = 23), time(2026, 11, 5, hour = 23)),
        startsIn(lateNight, days(2026, 11, 5, 1)),
    )
  }

  @Test
  fun eachOccurrenceKeepsEveryFieldButItsStart() {
    val series =
        Appointment(
            id = "a1",
            title = "Physio",
            notes = "Bring a towel",
            startAt = time(2026, 11, 3),
            durationMinutes = 45,
            recurrence = Recurrence(unit = RecurrenceUnit.WEEK, untilAt = time(2026, 12, 29)),
            location = GeoLocation(lat = 46.5, lng = 6.6, placeName = "Physio Lausanne"),
            attendeeIds = listOf("u1"),
            reminderMinutesBefore = listOf(60),
            patientId = "pat",
            lastEndAt = Long.MAX_VALUE,
            createdBy = "u1",
            createdAt = 1L,
            updatedBy = "u2",
            updatedAt = 2L,
        )

    val occurrences = series.occurrencesIn(days(2026, 11, 1, 21), zone)

    assertEquals(3, occurrences.size)
    occurrences.forEach { assertEquals(series.copy(startAt = it.startAt), it) }
  }

  @Test
  fun eachOccurrenceOfASeriesHasItsOwnKey() {
    val weekly = at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.WEEK))

    val keys = weekly.occurrencesIn(days(2026, 11, 1, 21), zone).map { it.occurrenceKey() }

    // The same id, told apart by the start.
    assertEquals(
        listOf(time(2026, 11, 3), time(2026, 11, 10), time(2026, 11, 17)).map { "a1@$it" },
        keys,
    )
  }

  @Test
  fun aSeriesWithEveryBelowOneIsGivenOnce() {
    // Only a document written by hand can hold it: validationError refuses it.
    val window = days(2026, 11, 1, 21)
    listOf(0, -1).forEach { every ->
      val broken = at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.WEEK, every = every))

      assertSame(broken, broken.occurrencesIn(window, zone).single())
    }
  }

  // isRepeating

  @Test
  fun onlyAnAppointmentWithAValidRecurrenceRepeats() {
    assertFalse(at(time(2026, 11, 3)).isRepeating())
    assertTrue(at(time(2026, 11, 3), Recurrence()).isRepeating())
    assertTrue(
        at(time(2026, 11, 3), Recurrence(unit = RecurrenceUnit.DAY, every = 3)).isRepeating()
    )
    // Given once by occurrencesIn, so it doesn't count as repeating either.
    assertFalse(at(time(2026, 11, 3), Recurrence(every = 0)).isRepeating())
  }
}
