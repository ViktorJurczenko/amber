// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  private val minute = 1.minutes.inWholeMilliseconds
  private val hour = 1.hours.inWholeMilliseconds
  private val day = 1.days.inWholeMilliseconds
  private val now = 100 * day
  private val horizon = now + 7 * day

  // reminder times, in minutes before the start
  private val oneHourBefore = 60
  private val halfAnHourBefore = 30
  private val twoHoursBefore = 2 * 60
  private val oneDayBefore = 24 * 60
  private val twoDaysBefore = 2 * 24 * 60

  /**
   * An appointment [startsIn] after [now], with [reminders] in minutes before its start. It was
   * saved [savedAgo] before [now]: a week by default, so the save doesn't move any reminder.
   */
  private fun appointmentIn(
      startsIn: Long,
      reminders: List<Int>,
      id: String = "a1",
      savedAgo: Long = 7 * day,
  ) =
      appointment.copy(
          id = id,
          startAt = now + startsIn,
          reminderMinutesBefore = reminders,
          updatedAt = now - savedAgo,
      )

  @Test
  fun eachReminderComesItsMinutesBeforeTheStart() {
    val doctor = appointmentIn(startsIn = 3 * day, reminders = listOf(oneHourBefore, oneDayBefore))

    val planned = planReminders(listOf(doctor), now, horizon)

    assertEquals(
        listOf(
            PlannedReminder(doctor, oneDayBefore, doctor.startAt - day),
            PlannedReminder(doctor, oneHourBefore, doctor.startAt - hour),
        ),
        planned,
    )
  }

  @Test
  fun remindersOfSeveralAppointmentsComeSoonestFirst() {
    val later = appointmentIn(startsIn = 2 * day, reminders = listOf(oneHourBefore), id = "later")
    val sooner =
        appointmentIn(
            startsIn = 1 * day,
            reminders = listOf(oneHourBefore, halfAnHourBefore),
            id = "sooner",
        )

    val planned = planReminders(listOf(later, sooner), now, horizon)

    assertEquals(
        listOf(
            sooner.startAt - hour,
            sooner.startAt - halfAnHourBefore * minute,
            later.startAt - hour,
        ),
        planned.map { it.fireAt },
    )
  }

  @Test
  fun remindersAtTheSameTimeAreSortedByAppointmentThenByStart() {
    val b = appointmentIn(startsIn = 1 * day, reminders = listOf(oneHourBefore), id = "b")
    val a = appointmentIn(startsIn = 1 * day, reminders = listOf(oneHourBefore), id = "a")
    // Two days of one repeating appointment: tomorrow's start and the day after's "1 day before"
    // come at the same time.
    val tomorrow =
        appointmentIn(startsIn = 1 * day, reminders = listOf(0, oneDayBefore), id = "series")
    val dayAfter = tomorrow.copy(startAt = tomorrow.startAt + day)

    val byAppointment = planReminders(listOf(b, a), now, horizon)
    val byStart = planReminders(listOf(dayAfter, tomorrow), now, now + day)

    assertEquals(listOf("a", "b"), byAppointment.map { it.occurrence.id })
    assertEquals(listOf(tomorrow.startAt, dayAfter.startAt), byStart.map { it.occurrence.startAt })
  }

  @Test
  fun onlyTheRemindersAfterTheStartOfThePeriodAndUntilItsEndAreGiven() {
    // 2 hours before is the start of the period, 1 hour before its end, 30 minutes before is after.
    val soon =
        appointmentIn(
            startsIn = 2 * hour,
            reminders = listOf(twoHoursBefore, oneHourBefore, halfAnHourBefore),
        )

    val planned = planReminders(listOf(soon), afterMillis = now, untilMillis = now + hour)

    assertEquals(listOf(oneHourBefore), planned.map { it.minutesBefore })
  }

  @Test
  fun aReminderWhoseTimePassedBeforeTheSaveComesAtTheSave() {
    // Saved at 20:00 for 9:00 the next day: 1 day before was 9:00 that morning.
    val savedLate =
        appointmentIn(
            startsIn = 13 * hour,
            reminders = listOf(oneDayBefore, oneHourBefore),
            savedAgo = 0,
        )

    val planned = planReminders(listOf(savedLate), now - minute, horizon)

    assertEquals(
        listOf(
            PlannedReminder(savedLate, oneDayBefore, now),
            PlannedReminder(savedLate, oneHourBefore, savedLate.startAt - hour),
        ),
        planned,
    )
  }

  @Test
  fun remindersMovedToTheSaveGiveOneTheClosestToTheStart() {
    val savedLate =
        appointmentIn(
            startsIn = 13 * hour,
            reminders = listOf(twoDaysBefore, oneDayBefore),
            savedAgo = 0,
        )

    val reminder = planReminders(listOf(savedLate), now - minute, horizon).single()

    assertEquals(oneDayBefore, reminder.minutesBefore)
    assertEquals(now, reminder.fireAt)
  }

  @Test
  fun aReminderComesAtTheStartAtTheLatest() {
    val atTheStart = appointmentIn(startsIn = 1 * hour, reminders = listOf(0))
    // Saved an hour after it started, for example to fix its notes.
    val started =
        appointment.copy(
            startAt = now - hour,
            reminderMinutesBefore = listOf(oneHourBefore),
            updatedAt = now,
        )

    val reminder = planReminders(listOf(atTheStart), now, horizon).single()

    assertEquals(atTheStart.startAt, reminder.fireAt)
    assertEquals(emptyList<PlannedReminder>(), planReminders(listOf(started), now - day, horizon))
  }

  @Test
  fun noReminderIsPlannedWithoutReminderTimesOrAppointments() {
    val silent = appointmentIn(startsIn = 1 * day, reminders = emptyList())

    assertEquals(emptyList<PlannedReminder>(), planReminders(listOf(silent), now, horizon))
    assertEquals(emptyList<PlannedReminder>(), planReminders(emptyList(), now, horizon))
  }

  @Test
  fun aTimeListedTwiceGivesOneReminder() {
    val doubled =
        appointmentIn(startsIn = 1 * day, reminders = listOf(oneHourBefore, oneHourBefore))

    val reminder = planReminders(listOf(doubled), now, horizon).single()

    assertEquals(oneHourBefore, reminder.minutesBefore)
    assertEquals(doubled.startAt - hour, reminder.fireAt)
  }
}
