// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.PlannedReminder
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that a reminder is shown with the right text and names, for the signed-in user. */
class AppointmentReminderPosterTest {

  private val circles: CareCircleRepository = mockk()
  private val profiles: ProfileRepository = mockk()
  private val auth: AuthRepository = mockk()
  private val shown = mutableListOf<ReminderNotification>()
  private val notifier = AppointmentReminderNotifier { shown += it }
  private val poster =
      AppointmentReminderPoster(
          circles,
          profiles,
          auth,
          notifier,
          zone = { ZoneOffset.UTC },
          nowMillis = { NOW },
      )

  private val circle =
      CareCircle(
          id = CIRCLE,
          patientId = PATIENT,
          memberIds = listOf(PATIENT, ANNA, BEN),
          nicknames = mapOf(PATIENT to mapOf(ANNA to "Annie")),
      )

  init {
    coEvery { circles.getCircle(CIRCLE) } returns Result.success(circle)
    coEvery { profiles.getProfile(any()) } answers
        {
          val uid = firstArg<String>()
          Result.success(
              UserProfile(uid = uid, person = Person(id = uid, firstName = NAMES[uid]!!))
          )
        }
    signIn(PATIENT)
  }

  private fun signIn(uid: String?) {
    every { auth.currentUser } returns uid?.let(::AuthUser)
  }

  /** Posts the reminder [minutes] before [occurrence], the doctor appointment by default. */
  private fun post(occurrence: Appointment = DOCTOR, minutes: Int = 60) = runBlocking {
    poster.post(
        CIRCLE,
        PlannedReminder(occurrence, minutes, occurrence.startAt - minutes * 60_000L),
    )
  }

  @Test
  fun showsWhenWhatAndTheAttendeesToThePatient() {
    assertTrue(post())

    assertEquals(
        listOf(
            ReminderNotification(
                tag = "a1@${DOCTOR.startAt}",
                title = "Doctor",
                start = LocalDateTime.of(2027, 1, 16, 14, 0),
                today = LocalDate.of(2027, 1, 15),
                otherNames = listOf("Annie", "Ben"),
            )
        ),
        shown,
    )
  }

  @Test
  fun anAttendeeSeesThePatientAndTheOtherAttendeesButNeverThemselves() {
    signIn(BEN)

    post()

    assertEquals(listOf("Pat", "Anna"), shown.single().otherNames)
  }

  @Test
  fun aPatientAloneSeesNoNames() {
    post(DOCTOR.copy(attendeeIds = emptyList()))

    assertEquals(emptyList<String>(), shown.single().otherNames)
  }

  @Test
  fun bothRemindersOfOneOccurrenceShareTheirTag() {
    post(minutes = 24 * 60)
    post(minutes = 60)
    // the next occurrence of a repeating appointment shares its id, but not its start
    post(DOCTOR.copy(startAt = DOCTOR.startAt + 7 * 24 * 3_600_000L))

    val tags = shown.map { it.tag }
    assertEquals(tags[0], tags[1])
    assertTrue(tags[2] != tags[0])
  }

  @Test
  fun nothingIsShownOnceSignedOut() {
    signIn(null)

    assertFalse(post())
    assertTrue(shown.isEmpty())
  }

  @Test
  fun theReminderStillComesWhenTheCircleCantBeRead() {
    coEvery { circles.getCircle(CIRCLE) } returns Result.failure(IllegalStateException("offline"))

    assertTrue(post())
    assertEquals(emptyList<String>(), shown.single().otherNames)
  }

  @Test
  fun aPersonWhoseProfileCantBeReadIsLeftOut() {
    coEvery { profiles.getProfile(BEN) } returns Result.failure(IllegalStateException("offline"))

    assertTrue(post())
    // Anna is still named, from the patient's own nickname for her
    assertEquals(listOf("Annie"), shown.single().otherNames)
  }

  @Test
  fun anAttendeeWhoLeftTheCircleIsNotNamed() {
    coEvery { circles.getCircle(CIRCLE) } returns
        Result.success(circle.copy(memberIds = listOf(PATIENT, ANNA)))

    post()

    assertEquals(listOf("Annie"), shown.single().otherNames)
    coVerify(exactly = 0) { profiles.getProfile(BEN) }
  }

  private companion object {
    const val CIRCLE = "c1"
    const val PATIENT = "patient"
    const val ANNA = "anna"
    const val BEN = "ben"
    val NAMES = mapOf(PATIENT to "Pat", ANNA to "Anna", BEN to "Ben")

    // Friday 15 January 2027, 12:00 UTC
    val NOW = LocalDateTime.of(2027, 1, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    val DOCTOR =
        Appointment(
            id = "a1",
            title = "Doctor",
            startAt = LocalDateTime.of(2027, 1, 16, 14, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            patientId = PATIENT,
            attendeeIds = listOf(ANNA, BEN),
            reminderMinutesBefore = listOf(60, 24 * 60),
        )
  }
}
