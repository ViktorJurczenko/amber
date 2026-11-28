// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.data.Synced
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests [UserAppointmentsObserver]: which appointments it gives, and how it handles failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class UserAppointmentsObserverTest {

  private val appointments = FakeAppointmentRepository()
  private val authRepo = mockk<AuthRepository>()
  private val profileRepo = mockk<ProfileRepository>()
  private val activeCircleRepo = mockk<ActiveCircleRepository>()

  private val november = AppointmentWindow(LocalDate.of(2026, 11, 1), 30)
  private val december = AppointmentWindow(LocalDate.of(2026, 12, 1), 31)

  @Before
  fun setUp() {
    every { authRepo.currentUser } returns AuthUser(UID)
    givenCircles(FIRST_CIRCLE, SECOND_CIRCLE)
    every { activeCircleRepo.activeCircleId(UID, any()) } returns flowOf(SECOND_CIRCLE)
  }

  // ---------- which care circle ----------

  @Test
  fun aPatientSeesTheAppointmentsOfTheirCircle() = runTest {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    save(SECOND_CIRCLE, "second", LocalDate.of(2026, 11, 12))
    assertEquals(listOf("first"), loadedIds(isPatient = true))
  }

  @Test
  fun aBlankCircleIdIsSkippedForAPatient() = runTest {
    saveInBothCircles()
    givenCircles("", SECOND_CIRCLE)
    assertEquals(listOf("second"), loadedIds(isPatient = true))
  }

  @Test
  fun aCaregiverSeesTheAppointmentsOfTheActiveCircle() = runTest {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    save(SECOND_CIRCLE, "second", LocalDate.of(2026, 11, 12))
    assertEquals(listOf("second"), loadedIds(isPatient = false))
  }

  @Test
  fun aPatientDoesNotUseTheActiveCircle() = runTest {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    loadedIds(isPatient = true)
    verify(exactly = 0) { activeCircleRepo.activeCircleId(any(), any()) }
  }

  @Test
  fun theListFollowsWhenACaregiverSwitchesCircle() = runTest {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    save(SECOND_CIRCLE, "second", LocalDate.of(2026, 11, 12))
    val active = MutableStateFlow(SECOND_CIRCLE)
    every { activeCircleRepo.activeCircleId(UID, any()) } returns active
    val results = collectInBackground(observer().observeUserAppointments(false, flowOf(november)))
    active.value = FIRST_CIRCLE
    assertEquals(listOf("first"), results.last().ids())
  }

  // ---------- what is in the list ----------

  @Test
  fun theListIsEmptyWhileAWindowLoads() = runTest {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    val first = observer().observeUserAppointments(true, flowOf(november)).first()
    assertEquals(emptyList<String>(), first.ids())
  }

  @Test
  fun anAppointmentAddedLaterAppearsInTheList() = runTest {
    val results = collectInBackground(observer().observeUserAppointments(true, flowOf(november)))
    save(FIRST_CIRCLE, "added", LocalDate.of(2026, 11, 20))
    assertEquals(listOf("added"), results.last().ids())
  }

  @Test
  fun aNewWindowReplacesTheAppointmentsOfThePreviousOne() = runTest {
    save(FIRST_CIRCLE, "november", LocalDate.of(2026, 11, 12))
    save(FIRST_CIRCLE, "december", LocalDate.of(2026, 12, 5))
    val windows = MutableStateFlow(november)
    val results = collectInBackground(observer().observeUserAppointments(true, windows))
    windows.value = december
    assertEquals(listOf("december"), results.last().ids())
  }

  @Test
  fun severalWindowsReadTheProfileOnce() = runTest {
    val windows = MutableStateFlow(november)
    collectInBackground(observer().observeUserAppointments(true, windows))
    windows.value = december
    coVerify(exactly = 1) { profileRepo.getProfile(UID) }
  }

  // ---------- nothing to show ----------

  @Test
  fun theListIsEmptyWithoutASignedInUser() = runTest {
    saveInBothCircles()
    every { authRepo.currentUser } returns null
    assertEquals(emptyList<String>(), latestIds(true, observer()))
  }

  @Test
  fun theListIsEmptyWhenTheSignedInUserCannotBeRead() = runTest {
    saveInBothCircles()
    assertEquals(emptyList<String>(), latestIds(true, observer(auth = { error("no Firebase") })))
  }

  @Test
  fun theListIsEmptyWithoutAProfile() = runTest {
    saveInBothCircles()
    coEvery { profileRepo.getProfile(UID) } returns Result.success(null)
    assertEquals(emptyList<String>(), latestIds(true, observer()))
  }

  @Test
  fun theListIsEmptyForAPatientWithoutACircle() = runTest {
    saveInBothCircles()
    givenCircles("")
    assertEquals(emptyList<String>(), latestIds(true, observer()))
  }

  @Test
  fun theListIsEmptyForACaregiverWithoutAnActiveCircle() = runTest {
    saveInBothCircles()
    every { activeCircleRepo.activeCircleId(UID, any()) } returns flowOf(null)
    assertEquals(emptyList<String>(), latestIds(false, observer()))
  }

  // ---------- failures ----------

  @Test
  fun aProfileThatCannotBeReadGivesItsFailure() = runTest {
    val failure = IllegalStateException("offline")
    coEvery { profileRepo.getProfile(UID) } returns Result.failure(failure)
    val result = observer().observeUserAppointments(true, flowOf(november)).first()
    assertEquals(failure.message, result.exceptionOrNull()?.message)
  }

  @Test
  fun appointmentsThatCannotBeReadGiveTheirFailure() = runTest {
    val failure = AppointmentError.PermissionDenied()
    val repository = mockk<AppointmentRepository>()
    every { repository.observeAppointments(any(), any(), any()) } returns flow { throw failure }
    val results = observer(repository).observeUserAppointments(true, flowOf(november)).toList()
    // Not compared by instance: coroutines may copy an exception to complete its stack trace.
    assertTrue(results.last().exceptionOrNull() is AppointmentError.PermissionDenied)
  }

  @Test
  fun theNextWindowIsStillWatchedAfterAFailedOne() = runTest {
    val repository = mockk<AppointmentRepository>()
    every { repository.observeAppointments(any(), november.firstDay, any()) } returns
        flow { throw AppointmentError.PermissionDenied() }
    val decemberList = flowOf(emptyList<Synced<Appointment>>())
    every { repository.observeAppointments(any(), december.firstDay, any()) } returns decemberList
    val windows = MutableStateFlow(november)
    collectInBackground(observer(repository).observeUserAppointments(true, windows))
    windows.value = december
    verify(exactly = 1) {
      repository.observeAppointments(FIRST_CIRCLE, december.firstDay, december.days)
    }
  }

  @Test
  fun aRepositoryThatCannotBeBuiltGivesItsFailure() = runTest {
    val failure = IllegalStateException("not built")
    val observer =
        UserAppointmentsObserver(
            appointmentRepo = { throw failure },
            authRepo = { authRepo },
            profileRepo = { profileRepo },
            activeCircleRepo = { activeCircleRepo },
        )
    val results = observer.observeUserAppointments(true, flowOf(november)).toList()
    assertEquals(failure.message, results.last().exceptionOrNull()?.message)
  }

  // ---------- default parameters ----------

  @Test
  fun byDefaultAPatientWatchesTheAppointmentsOfTheCurrentDay() = runTest {
    val local = FakeAppointmentRepository(ZoneId.systemDefault())
    val noon = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault())
    // It lasts until noon tomorrow, so the test also passes if the day changes while it runs.
    local.addAppointment(
        CareCircle(id = FIRST_CIRCLE, patientId = PATIENT),
        Appointment(
            id = "today",
            title = "Visit",
            startAt = noon.toInstant().toEpochMilli(),
            durationMinutes = MINUTES_IN_A_DAY,
        ),
    )
    val result = observer(local).observeUserAppointments().first { it.ids().isNotEmpty() }
    assertEquals(listOf("today"), result.ids())
  }

  @Test
  fun aWindowLastsOneDayByDefault() {
    assertEquals(1, AppointmentWindow().days)
  }

  @Test
  fun aWindowStartsTodayByDefault() {
    // Today is read before and after, so the test also passes if the day changes while it runs.
    val before = LocalDate.now()
    val firstDay = AppointmentWindow().firstDay
    val after = LocalDate.now()
    assertTrue(firstDay == before || firstDay == after)
  }

  @Test
  fun byDefaultThePatientRepositoriesComeFromTheProviders() = runTest {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    val ids = withDefaultProviders { loadedIds(isPatient = true, UserAppointmentsObserver()) }
    assertEquals(listOf("first"), ids)
  }

  @Test
  fun byDefaultTheActiveCircleComesFromItsProvider() = runTest {
    save(SECOND_CIRCLE, "second", LocalDate.of(2026, 11, 12))
    val ids = withDefaultProviders { loadedIds(isPatient = false, UserAppointmentsObserver()) }
    assertEquals(listOf("second"), ids)
  }

  // ---------- helpers ----------

  private fun observer(
      repository: AppointmentRepository = appointments,
      auth: () -> AuthRepository = { authRepo },
  ) =
      UserAppointmentsObserver(
          appointmentRepo = { repository },
          authRepo = auth,
          profileRepo = { profileRepo },
          activeCircleRepo = { activeCircleRepo },
      )

  private suspend fun saveInBothCircles() {
    save(FIRST_CIRCLE, "first", LocalDate.of(2026, 11, 12))
    save(SECOND_CIRCLE, "second", LocalDate.of(2026, 11, 12))
  }

  /** The ids in the latest list of November. */
  private fun TestScope.latestIds(
      isPatient: Boolean,
      observer: UserAppointmentsObserver,
  ): List<String> =
      collectInBackground(observer.observeUserAppointments(isPatient, flowOf(november)))
          .last()
          .ids()

  private fun givenCircles(vararg circleIds: String) {
    coEvery { profileRepo.getProfile(UID) } returns
        Result.success(UserProfile(uid = UID, circleIds = circleIds.toList()))
  }

  private suspend fun save(circleId: String, id: String, day: LocalDate) {
    appointments.addAppointment(
        CareCircle(id = circleId, patientId = PATIENT),
        Appointment(
            id = id,
            title = "Visit",
            startAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
    )
  }

  /** The ids in November, once the list has loaded. */
  private suspend fun loadedIds(
      isPatient: Boolean,
      observer: UserAppointmentsObserver = observer(),
  ): List<String> =
      observer
          .observeUserAppointments(isPatient, flowOf(november))
          .first { it.ids().isNotEmpty() }
          .ids()

  /**
   * Runs [block] while the default providers give the fakes of this test, so that an observer built
   * without arguments never opens Firebase.
   */
  private suspend fun <T> withDefaultProviders(block: suspend () -> T): T {
    mockkObject(
        AuthRepositoryProvider,
        AppointmentRepositoryProvider.Companion,
        ProfileRepositoryProvider.Companion,
        ActiveCircleRepositoryProvider.Companion,
    )
    every { AuthRepositoryProvider.repository } returns authRepo
    every { AppointmentRepositoryProvider.default } returns
        AppointmentRepositoryProvider { appointments }
    every { ProfileRepositoryProvider.default } returns ProfileRepositoryProvider { profileRepo }
    every { ActiveCircleRepositoryProvider.default } returns
        ActiveCircleRepositoryProvider { activeCircleRepo }
    return try {
      block()
    } finally {
      unmockkObject(
          AuthRepositoryProvider,
          AppointmentRepositoryProvider.Companion,
          ProfileRepositoryProvider.Companion,
          ActiveCircleRepositoryProvider.Companion,
      )
    }
  }

  private fun Result<List<Appointment>>.ids(): List<String> = getOrThrow().map { it.id }

  /** Collects [values] until the end of the test, and gives the list they are added to. */
  private fun <T> TestScope.collectInBackground(values: Flow<T>): List<T> {
    val collected = mutableListOf<T>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      values.collect { collected += it }
    }
    return collected
  }

  private companion object {
    const val UID = "user"
    const val PATIENT = "patient"
    const val FIRST_CIRCLE = "first-circle"
    const val SECOND_CIRCLE = "second-circle"
    const val MINUTES_IN_A_DAY = 24 * 60
  }
}
