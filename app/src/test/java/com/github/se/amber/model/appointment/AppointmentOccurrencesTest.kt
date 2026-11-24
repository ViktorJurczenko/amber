// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.DayWindow
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests [occurrencesIn] for one-off appointments.
 *
 * It relies on `overlaps`, so the edges of the window (an appointment that ends right at its start,
 * or starts right at its end) are left to DayWindowTest.
 */
class AppointmentOccurrencesTest {

  private val zone = ZoneId.of("Europe/Zurich")
  private val hour = 1.hours.inWholeMilliseconds
  private val window = DayWindow(startMillis = 100 * hour, endMillis = 124 * hour)

  /** @return a one-hour appointment that starts at [startAt]. */
  private fun at(startAt: Long) = Appointment(id = "a1", title = "Doctor", startAt = startAt)

  @Test
  fun anAppointmentInsideTheWindowIsGivenItself() {
    val appointment = at(110 * hour)

    // The very same object: nothing is copied or changed for a one-off appointment.
    assertSame(appointment, appointment.occurrencesIn(window, zone).single())
  }

  @Test
  fun anAppointmentOutsideTheWindowIsNotGiven() {
    assertEquals(emptyList<Appointment>(), at(10 * hour).occurrencesIn(window, zone))
    assertEquals(emptyList<Appointment>(), at(500 * hour).occurrencesIn(window, zone))
  }
}
