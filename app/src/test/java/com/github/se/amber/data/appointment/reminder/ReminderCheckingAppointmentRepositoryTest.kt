// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.appointment.AppointmentError
import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.circle.CareCircle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that a save made on this phone runs the reminder check, and that a failed one doesn't. */
class ReminderCheckingAppointmentRepositoryTest {

  private val fake = FakeAppointmentRepository()
  private var checks = 0
  private val repository = ReminderCheckingAppointmentRepository(fake) { checks++ }

  @Test
  fun addingAnAppointmentSavesItAndRunsTheCheck() = runBlocking {
    val saved = repository.addAppointment(CIRCLE, APPOINTMENT)

    assertEquals("patient", saved.getOrThrow().patientId)
    assertEquals(1, checks)
  }

  @Test
  fun editingAnAppointmentSavesItAndRunsTheCheck() = runBlocking {
    fake.addAppointment(CIRCLE, APPOINTMENT)

    val saved = repository.updateAppointment(CIRCLE.id, APPOINTMENT.copy(title = "Dentist"))

    assertEquals("Dentist", saved.getOrThrow().title)
    assertEquals(1, checks)
  }

  @Test
  fun aSaveThatFailsRunsNoCheck() = runBlocking {
    fake.failWith = AppointmentError.NotAuthenticated()

    assertTrue(repository.addAppointment(CIRCLE, APPOINTMENT).isFailure)
    assertTrue(repository.updateAppointment(CIRCLE.id, APPOINTMENT).isFailure)
    assertEquals(0, checks)
  }

  @Test
  fun aCheckThatFailsKeepsTheSaveSuccessful() = runBlocking {
    val failing =
        ReminderCheckingAppointmentRepository(fake) {
          checks++
          throw SecurityException("no exact alarm")
        }

    val added = failing.addAppointment(CIRCLE, APPOINTMENT)
    val edited = failing.updateAppointment(CIRCLE.id, APPOINTMENT.copy(title = "Dentist"))

    assertTrue(added.isSuccess)
    assertEquals("Dentist", edited.getOrThrow().title)
    assertEquals(listOf("Dentist"), fake.appointmentsOf(CIRCLE.id).map { it.title })
    assertEquals(2, checks)
  }

  @Test
  fun aDeletionRunsNoCheck() = runBlocking {
    fake.addAppointment(CIRCLE, APPOINTMENT)

    assertTrue(repository.deleteAppointment(CIRCLE.id, APPOINTMENT.id).isSuccess)
    assertEquals(0, checks)
  }

  private companion object {
    val CIRCLE = CareCircle(id = "c1", patientId = "patient", memberIds = listOf("patient"))
    val APPOINTMENT =
        Appointment(id = "a1", title = "Doctor", startAt = 1_800_000_000_000L, durationMinutes = 30)
  }
}
