// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests who is linked to an appointment, and when its reminders are planned. */
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

  private val minute = 60_000L
  private val hour = 60 * minute
  private val day = 24 * hour
  private val now = 100 * day

  /** An appointment [startsIn] after [now], with [reminders] in minutes before its start. */
  private fun appointmentIn(startsIn: Long, reminders: List<Int>, id: String = "a1") =
      appointment.copy(id = id, startAt = now + startsIn, reminderMinutesBefore = reminders)

  @Test
  fun eachReminderComesItsMinutesBeforeTheStart() {
    val doctor = appointmentIn(startsIn = 3 * day, reminders = listOf(60, 24 * 60))

    val planned = planReminders(listOf(doctor), now, now + 7 * day)

    assertEquals(
        listOf(
            PlannedReminder("a1@${doctor.startAt}@1440", doctor, 1440, doctor.startAt - day),
            PlannedReminder("a1@${doctor.startAt}@60", doctor, 60, doctor.startAt - hour),
        ),
        planned,
    )
  }

  @Test
  fun remindersOfSeveralAppointmentsComeSoonestFirst() {
    val later = appointmentIn(startsIn = 2 * day, reminders = listOf(60), id = "later")
    val sooner = appointmentIn(startsIn = 1 * day, reminders = listOf(60, 30), id = "sooner")

    val planned = planReminders(listOf(later, sooner), now, now + 7 * day)

    assertEquals(
        listOf(sooner.startAt - hour, sooner.startAt - 30 * minute, later.startAt - hour),
        planned.map { it.fireAt },
    )
  }

  @Test
  fun aReminderThatHasPassedIsLeftOutButOneDueNowIsKept() {
    // Starts in 2 hours: 1 day before has passed, 2 hours before is right now.
    val soon = appointmentIn(startsIn = 2 * hour, reminders = listOf(24 * 60, 120, 60))

    val planned = planReminders(listOf(soon), now, now + 7 * day)

    assertEquals(listOf(now, now + hour), planned.map { it.fireAt })
  }

  @Test
  fun aReminderAtOrAfterTheHorizonIsLeftOut() {
    // 1 day before falls on the horizon, 1 hour before after it.
    val farAway = appointmentIn(startsIn = 8 * day, reminders = listOf(24 * 60, 60))

    assertEquals(emptyList<PlannedReminder>(), planReminders(listOf(farAway), now, now + 7 * day))
  }

  @Test
  fun noReminderIsPlannedWithoutReminderTimesOrAppointments() {
    val silent = appointmentIn(startsIn = 1 * day, reminders = emptyList())

    assertEquals(emptyList<PlannedReminder>(), planReminders(listOf(silent), now, now + 7 * day))
    assertEquals(emptyList<PlannedReminder>(), planReminders(emptyList(), now, now + 7 * day))
  }

  @Test
  fun aTimeListedTwiceGivesOneReminder() {
    val doubled = appointmentIn(startsIn = 1 * day, reminders = listOf(60, 60))

    assertEquals(1, planReminders(listOf(doubled), now, now + 7 * day).size)
  }

  @Test
  fun theKeyStaysTheSameWhenNothingChanges() {
    val doctor = appointmentIn(startsIn = 3 * day, reminders = listOf(60))
    // An edit that keeps the start and the reminder time, planned again an hour later.
    val renamed = doctor.copy(title = "Dentist", notes = "Bring the X-rays")

    val first = planReminders(listOf(doctor), now, now + 7 * day).single()
    val second = planReminders(listOf(renamed), now + hour, now + hour + 7 * day).single()

    assertEquals("a1@${doctor.startAt}@60", first.key)
    assertEquals(first.key, second.key)
  }

  @Test
  fun theKeyChangesWhenTheAppointmentMoves() {
    val doctor = appointmentIn(startsIn = 3 * day, reminders = listOf(60))
    val moved = doctor.copy(startAt = doctor.startAt + hour)

    val before = planReminders(listOf(doctor), now, now + 7 * day).single()
    val after = planReminders(listOf(moved), now, now + 7 * day).single()

    assertNotEquals(before.key, after.key)
  }
}
