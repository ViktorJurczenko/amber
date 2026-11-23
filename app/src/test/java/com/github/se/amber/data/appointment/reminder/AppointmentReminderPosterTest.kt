// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
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

/** Checks that a due reminder is shown only if it is still wanted, with the right names. */
class AppointmentReminderPosterTest {

  private val appointments = FakeAppointmentRepository()
  private val circles: CareCircleRepository = mockk()
  private val profiles: ProfileRepository = mockk()
  private val auth: AuthRepository = mockk()
  private val shown = mutableListOf<ReminderNotification>()
  private val notifier = AppointmentReminderNotifier { shown += it }
  private val poster =
      AppointmentReminderPoster(
          appointments,
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

  /** Saves the doctor appointment, tomorrow at 14:00, with Anna and Ben, then [change] applied. */
  private fun save(change: (Appointment) -> Appointment = { it }) = runBlocking {
    appointments.addAppointment(circle, change(DOCTOR)).getOrThrow()
  }

  private fun post(minutes: Int = 60, startAt: Long = DOCTOR.startAt) = runBlocking {
    poster.post(DueReminder(CIRCLE, DOCTOR.id, startAt, minutes, "tag"))
  }

  @Test
  fun showsWhenWhatAndTheAttendeesToThePatient() {
    save()

    assertTrue(post())

    assertEquals(
        listOf(
            ReminderNotification(
                tag = "tag",
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
    save()
    signIn(BEN)

    post()

    assertEquals(listOf("Pat", "Anna"), shown.single().otherNames)
  }

  @Test
  fun aPatientAloneSeesNoNames() {
    save { it.copy(attendeeIds = emptyList()) }

    post()

    assertEquals(emptyList<String>(), shown.single().otherNames)
  }

  @Test
  fun nothingIsShownOnceTheAppointmentIsDeleted() {
    assertFalse(post())
    assertTrue(shown.isEmpty())
  }

  @Test
  fun nothingIsShownOnceTheAppointmentHasMoved() {
    save { it.copy(startAt = it.startAt + 30 * 60_000) }

    assertFalse(post())
    assertTrue(shown.isEmpty())
  }

  @Test
  fun aReminderDaysAheadFindsItsAppointment() {
    // shown on Friday 15, for Monday 18 at 9:00, 3 days before
    val monday = DOCTOR.startAt + (2 * 24 - 5) * 3_600_000L
    save { it.copy(startAt = monday, reminderMinutesBefore = listOf(3 * 24 * 60)) }

    assertTrue(post(minutes = 3 * 24 * 60, startAt = monday))
    assertEquals(LocalDateTime.of(2027, 1, 18, 9, 0), shown.single().start)
  }

  @Test
  fun nothingIsShownOnceThatReminderTimeIsRemoved() {
    save { it.copy(reminderMinutesBefore = listOf(24 * 60)) }

    assertFalse(post(minutes = 60))
    assertTrue(post(minutes = 24 * 60))
  }

  @Test
  fun nothingIsShownToACaregiverRemovedFromTheAttendees() {
    save { it.copy(attendeeIds = listOf(ANNA)) }
    signIn(BEN)

    assertFalse(post())
    assertTrue(shown.isEmpty())
  }

  @Test
  fun nothingIsShownOnceSignedOut() {
    save()
    signIn(null)

    assertFalse(post())
  }

  @Test
  fun theReminderStillComesWhenTheCircleCantBeRead() {
    save()
    coEvery { circles.getCircle(CIRCLE) } returns Result.failure(IllegalStateException("offline"))

    assertTrue(post())
    assertEquals(emptyList<String>(), shown.single().otherNames)
  }

  @Test
  fun aPersonWhoseProfileCantBeReadIsLeftOut() {
    save()
    coEvery { profiles.getProfile(BEN) } returns Result.failure(IllegalStateException("offline"))

    assertTrue(post())
    // Anna is still named, from the patient's own nickname for her
    assertEquals(listOf("Annie"), shown.single().otherNames)
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
            attendeeIds = listOf(ANNA, BEN),
            reminderMinutesBefore = listOf(60, 24 * 60),
        )
  }
}
