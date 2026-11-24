// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests who is linked to an appointment, and so gets its reminders. */
class AppointmentRemindersTest {

  @Test
  fun thePatientAndTheAttendeesAreLinkedButNotTheCreator() {
    val appointment =
        Appointment(
            id = "a1",
            title = "Doctor",
            startAt = 1_000L,
            attendeeIds = listOf("ada", "bob"),
            patientId = "pat",
            createdBy = "eve",
            updatedBy = "eve",
        )

    // The patient always takes part, so they are never in attendeeIds.
    assertTrue(appointment.isLinkedTo("pat"))
    assertTrue(appointment.copy(attendeeIds = emptyList()).isLinkedTo("pat"))
    assertTrue(appointment.isLinkedTo("ada"))
    assertTrue(appointment.isLinkedTo("bob"))
    // Creating an appointment doesn't link a caregiver who isn't coming.
    assertFalse(appointment.isLinkedTo("eve"))
  }
}
