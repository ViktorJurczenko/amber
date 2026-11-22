// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests who is linked to an appointment, and so gets its reminders. */
class AppointmentRemindersTest {

  private val appointment =
      Appointment(
          id = "a1",
          title = "Doctor",
          startAt = 1_000L,
          attendeeIds = listOf("ada", "bob"),
          patientId = "pat",
      )

  @Test
  fun thePatientIsLinkedEvenWhenNotListed() {
    // The patient always takes part, so they are never in attendeeIds.
    assertTrue(appointment.isLinkedTo("pat"))
    assertTrue(appointment.copy(attendeeIds = emptyList()).isLinkedTo("pat"))
  }

  @Test
  fun eachAttendeeIsLinked() {
    assertTrue(appointment.isLinkedTo("ada"))
    assertTrue(appointment.isLinkedTo("bob"))
  }

  @Test
  fun aCaregiverWhoIsNotComingIsNotLinkedEvenAsTheCreator() {
    val createdByEve = appointment.copy(createdBy = "eve", updatedBy = "eve")

    assertFalse(createdByEve.isLinkedTo("eve"))
  }

  @Test
  fun theDefaultIsOneReminderOneDayBefore() {
    assertEquals(listOf(24 * 60), Appointment().reminderMinutesBefore)
  }
}
