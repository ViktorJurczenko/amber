// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.DayWindow
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests [occurrencesIn] for one-off appointments.
 *
 * The window is built by hand, not with dayWindow, so these tests only check the overlap and not
 * how the days are counted. DayWindowTest covers that.
 */
class AppointmentOccurrencesTest {

  private val zone = ZoneId.of("Europe/Zurich")
  private val hour = 1.hours.inWholeMilliseconds
  private val window = DayWindow(startMillis = 100 * hour, endMillis = 124 * hour)

  /** @return an appointment that starts at [startAt] and lasts [minutes]. */
  private fun at(startAt: Long, minutes: Int = 60) =
      Appointment(id = "a1", title = "Doctor", startAt = startAt, durationMinutes = minutes)

  @Test
  fun anAppointmentInsideTheWindowIsGivenItself() {
    val appointment = at(110 * hour)

    val occurrences = appointment.occurrencesIn(window, zone)

    assertEquals(1, occurrences.size)
    // The very same object: nothing is copied or changed for a one-off appointment.
    assertSame(appointment, occurrences.single())
  }

  @Test
  fun anAppointmentThatStartedBeforeAndIsNotOverIsGiven() {
    val appointment = at(99 * hour, minutes = 90)

    assertEquals(listOf(appointment), appointment.occurrencesIn(window, zone))
  }

  @Test
  fun anAppointmentThatEndsRightAtTheStartIsNotGiven() {
    assertEquals(emptyList<Appointment>(), at(99 * hour).occurrencesIn(window, zone))
  }

  @Test
  fun anAppointmentThatStartsRightAtTheEndIsNotGiven() {
    assertEquals(emptyList<Appointment>(), at(124 * hour).occurrencesIn(window, zone))
  }

  @Test
  fun anAppointmentThatStartsAMinuteBeforeTheEndIsGiven() {
    val appointment = at(124 * hour - 1.minutes.inWholeMilliseconds)

    assertEquals(listOf(appointment), appointment.occurrencesIn(window, zone))
  }

  @Test
  fun anAppointmentFarFromTheWindowIsNotGiven() {
    assertEquals(emptyList<Appointment>(), at(10 * hour).occurrencesIn(window, zone))
    assertEquals(emptyList<Appointment>(), at(500 * hour).occurrencesIn(window, zone))
  }
}
