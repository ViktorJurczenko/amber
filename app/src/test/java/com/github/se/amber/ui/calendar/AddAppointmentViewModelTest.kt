// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.appointment.AppointmentError
import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.AppointmentValidationError
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.calendar.DEFAULT_DURATION_MINUTES
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks what [AddAppointmentViewModel] stores while the caregiver types, when the appointment can
 * be added, what is saved in the active care circle, and the message shown for each failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddAppointmentViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private val viewModels = mutableListOf<AddAppointmentViewModel>()
  private val appointments = FakeAppointmentRepository()
  private val authRepo = mockk<AuthRepository>()
  private val profileRepo = mockk<ProfileRepository>()
  private val activeCircleRepo = mockk<ActiveCircleRepository>()
  private val day = LocalDate.of(2026, 11, 21)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { authRepo.currentUser } returns AuthUser(UID)
    coEvery { profileRepo.getProfile(UID) } returns
        Result.success(UserProfile(uid = UID, circleIds = listOf(CIRCLE)))
    every { activeCircleRepo.activeCircleId(UID, listOf(CIRCLE)) } returns flowOf(CIRCLE)
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    Dispatchers.resetMain()
  }

  // ---- State ----

  @Test
  fun setTitleStoresTheTitle() {
    val viewModel = viewModel()
    viewModel.setTitle("Dr. Martin")
    assertEquals("Dr. Martin", viewModel.uiState.value.title)
  }

  @Test
  fun setNoteStoresTheNote() {
    val viewModel = viewModel()
    viewModel.setNote("Bring the blood test results")
    assertEquals("Bring the blood test results", viewModel.uiState.value.note)
  }

  @Test
  fun clearErrorMsgRemovesTheError() {
    val viewModel = viewModel()
    viewModel.setTitle("Dr. Martin")
    every { authRepo.currentUser } returns null
    viewModel.addAppointment(day)
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun cannotAddWithABlankTitle() {
    assertFalse(AddAppointmentUIState(title = "   ").canAdd)
  }

  @Test
  fun cannotAddWhileAdding() {
    assertFalse(AddAppointmentUIState(title = "Dr. Martin", isAdding = true).canAdd)
  }

  // ---- What is saved ----

  @Test
  fun nothingIsSavedWithAnEmptyTitle() {
    val viewModel = viewModel()
    viewModel.addAppointment(day)
    assertTrue(appointments.appointmentsOf(CIRCLE).isEmpty())
  }

  @Test
  fun oneAppointmentIsSavedInTheActiveCircle() {
    addWithTitle("Dr. Martin")
    assertEquals(1, appointments.appointmentsOf(CIRCLE).size)
  }

  @Test
  fun savedAppointmentHasTheIdOfTheRepository() {
    addWithTitle("Dr. Martin")
    assertEquals("appointment-1", saved().id)
  }

  @Test
  fun savedAppointmentHasTheTypedTitle() {
    addWithTitle("Dr. Martin")
    assertEquals("Dr. Martin", saved().title)
  }

  @Test
  fun savedAppointmentHasTheTypedNote() {
    addWithTitle("Dr. Martin", note = "Bring the blood test results")
    assertEquals("Bring the blood test results", saved().notes)
  }

  @Test
  fun savedAppointmentStartsOnTheGivenDay() {
    addWithTitle("Dr. Martin")
    assertEquals(day, dayOf(saved().startAt))
  }

  @Test
  fun savedAppointmentStartsTodayByDefault() {
    val viewModel = viewModel()
    viewModel.setTitle("Dr. Martin")
    viewModel.addAppointment()
    assertEquals(LocalDate.now(), dayOf(saved().startAt))
  }

  @Test
  fun savedAppointmentHasTheDefaultDuration() {
    addWithTitle("Dr. Martin")
    assertEquals(DEFAULT_DURATION_MINUTES, saved().durationMinutes)
  }

  @Test
  fun savedAppointmentHasAnEmptyLocation() {
    addWithTitle("Dr. Martin")
    assertEquals(GeoLocation(), saved().location)
  }

  @Test
  fun savedAppointmentHasNoAttendee() {
    addWithTitle("Dr. Martin")
    assertTrue(saved().attendeeIds.isEmpty())
  }

  @Test
  fun failedAddSavesNothing() {
    appointments.failWith = AppointmentError.PermissionDenied()
    addWithTitle("Dr. Martin")
    assertTrue(appointments.appointmentsOf(CIRCLE).isEmpty())
  }

  // ---- Result of the add ----

  @Test
  fun successSendsTheAddedEvent() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setTitle("Dr. Martin")
        viewModel.addAppointment(day)
        assertEquals(AddAppointmentEvent.Added, viewModel.events.first())
      }

  @Test
  fun successStopsAdding() {
    val viewModel = addWithTitle("Dr. Martin")
    assertFalse(viewModel.uiState.value.isAdding)
  }

  @Test
  fun successShowsNoError() {
    val viewModel = addWithTitle("Dr. Martin")
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun failureStopsAdding() {
    appointments.failWith = AppointmentError.PermissionDenied()
    val viewModel = addWithTitle("Dr. Martin")
    assertFalse(viewModel.uiState.value.isAdding)
  }

  // ---- Missing circle ----

  @Test
  fun noSignedInUserShowsTheNoCircleMessage() {
    every { authRepo.currentUser } returns null
    val viewModel = addWithTitle("Dr. Martin")
    assertEquals(R.string.addAppointmentErrorNoCircle, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun noProfileShowsTheNoCircleMessage() {
    coEvery { profileRepo.getProfile(UID) } returns Result.success(null)
    val viewModel = addWithTitle("Dr. Martin")
    assertEquals(R.string.addAppointmentErrorNoCircle, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun unreadableProfileSetsTheNoCircleMessage() {
    coEvery { profileRepo.getProfile(UID) } returns Result.failure(RuntimeException("offline"))
    val viewModel = addWithTitle("Dr. Martin")
    assertEquals(R.string.addAppointmentErrorNoCircle, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun noActiveCircleSetsTheNoCircleMessage() {
    every { activeCircleRepo.activeCircleId(UID, listOf(CIRCLE)) } returns flowOf(null)
    val viewModel = addWithTitle("Dr. Martin")
    assertEquals(R.string.addAppointmentErrorNoCircle, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun unexpectedExceptionSetsTheGenericMessage() {
    coEvery { profileRepo.getProfile(UID) } throws IllegalStateException("boom")
    val viewModel = addWithTitle("Dr. Martin")
    assertEquals(R.string.addAppointmentErrorAdd, viewModel.uiState.value.errorMsgRes)
  }

  // ---- One message for each repository error ----

  @Test
  fun invalidCircleIdSetsItsMessage() {
    assertEquals(
        R.string.addAppointmentErrorInvalidCircle,
        messageFor(AppointmentError.InvalidCircleId()),
    )
  }

  @Test
  fun invalidAppointmentIdSetsItsMessage() {
    assertEquals(
        R.string.addAppointmentErrorInvalidId,
        messageFor(AppointmentError.InvalidAppointmentId()),
    )
  }

  @Test
  fun blankTitleErrorSetsTheEmptyTitleMessage() {
    assertEquals(
        R.string.addAppointmentErrorTitleEmpty,
        messageFor(AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE)),
    )
  }

  @Test
  fun missingStartErrorSetsItsMessage() {
    assertEquals(
        R.string.addAppointmentErrorMissingStart,
        messageFor(AppointmentError.InvalidAppointment(AppointmentValidationError.MISSING_START)),
    )
  }

  @Test
  fun invalidDurationErrorSetsItsMessage() {
    assertEquals(
        R.string.addAppointmentErrorInvalidDuration,
        messageFor(
            AppointmentError.InvalidAppointment(AppointmentValidationError.INVALID_DURATION)
        ),
    )
  }

  @Test
  fun notFoundSetsItsMessage() {
    assertEquals(R.string.addAppointmentErrorNotFound, messageFor(AppointmentError.NotFound()))
  }

  @Test
  fun notAuthenticatedSetsItsMessage() {
    assertEquals(
        R.string.addAppointmentErrorNotSignedIn,
        messageFor(AppointmentError.NotAuthenticated()),
    )
  }

  @Test
  fun permissionDeniedShowsItsMessage() {
    assertEquals(
        R.string.addAppointmentErrorPermissionDenied,
        messageFor(AppointmentError.PermissionDenied()),
    )
  }

  @Test
  fun unknownErrorShowsTheGenericMessage() {
    assertEquals(
        R.string.addAppointmentErrorAdd,
        messageFor(AppointmentError.Unknown(RuntimeException("boom"))),
    )
  }

  /** Makes the repository fail with [error] and returns the message the screen would show. */
  private fun messageFor(error: AppointmentError): Int? {
    appointments.failWith = error
    return addWithTitle("Dr. Martin").uiState.value.errorMsgRes
  }

  /** Types [title] and [note], then adds the appointment on [day]. */
  private fun addWithTitle(title: String, note: String = ""): AddAppointmentViewModel {
    val viewModel = viewModel()
    viewModel.setTitle(title)
    viewModel.setNote(note)
    viewModel.addAppointment(day)
    return viewModel
  }

  private fun saved() = appointments.appointmentsOf(CIRCLE).single()

  private fun dayOf(epochMillis: Long): LocalDate =
      Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

  private fun viewModel(): AddAppointmentViewModel =
      AddAppointmentViewModel(
              appointmentRepo = appointments,
              authRepo = authRepo,
              profileRepo = profileRepo,
              activeCircleRepo = activeCircleRepo,
          )
          .also { viewModels += it }

  private companion object {
    const val UID = "user1"
    const val CIRCLE = "circle1"
  }
}
