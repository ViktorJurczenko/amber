// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.appointment.AppointmentWindow
import com.github.se.amber.data.appointment.FakeUserAppointmentsObserver
import com.github.se.amber.model.appointment.Appointment
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Tests [CalendarViewModel]: the month navigation and the days that have an appointment. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

  private val november = YearMonth.of(2026, 11)
  private val viewModels = mutableListOf<CalendarViewModel>()

  // The appointments of each month, by its first day. A month that isn't here has none.
  private val months = mutableMapOf<LocalDate, MutableStateFlow<Result<List<Appointment>>>>()
  private val observer = FakeUserAppointmentsObserver { month(it.firstDay) }

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    Dispatchers.resetMain()
  }

  @Test
  fun startsOnTheInitialMonth() {
    assertEquals(november, CalendarViewModel(november).uiState.value.month)
  }

  @Test
  fun showNextMonthMovesToTheFollowingMonth() {
    val viewModel = CalendarViewModel(november)
    viewModel.showNextMonth()
    assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.month)
  }

  @Test
  fun showPreviousMonthMovesToThePrecedingMonth() {
    val viewModel = CalendarViewModel(november)
    viewModel.showPreviousMonth()
    assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.month)
  }

  @Test
  fun showNextMonthAfterDecemberChangesYear() {
    val viewModel = CalendarViewModel(YearMonth.of(2026, 12))
    viewModel.showNextMonth()
    assertEquals(YearMonth.of(2027, 1), viewModel.uiState.value.month)
  }

  @Test
  fun showPreviousMonthBeforeJanuaryChangesYear() {
    val viewModel = CalendarViewModel(YearMonth.of(2026, 1))
    viewModel.showPreviousMonth()
    assertEquals(YearMonth.of(2025, 12), viewModel.uiState.value.month)
  }

  @Test
  fun nextThenPreviousComesBackToTheInitialMonth() {
    val viewModel = CalendarViewModel(november)
    viewModel.showNextMonth()
    viewModel.showPreviousMonth()
    assertEquals(november, viewModel.uiState.value.month)
  }

  // ---------- days with an appointment ----------

  @Test
  fun loadingGivesTheDaysThatHaveAnAppointment() {
    month(NOVEMBER_FIRST).value = appointmentsOn(LocalDate.of(2026, 11, 12))
    val viewModel = loadedViewModel()
    assertEquals(setOf(12), viewModel.uiState.value.appointmentDays)
  }

  @Test
  fun loadingWatchesTheWholeDisplayedMonth() {
    loadedViewModel()
    assertEquals(listOf(AppointmentWindow(NOVEMBER_FIRST, 30)), observer.windows)
  }

  @Test
  fun loadingAsksForTheAppointmentsOfTheGivenRole() {
    viewModel().loadAppointments(isPatient = false)
    assertEquals(listOf(false), observer.roles)
  }

  @Test
  fun loadingIsForAPatientByDefault() {
    viewModel().loadAppointments()
    assertEquals(listOf(true), observer.roles)
  }

  @Test
  fun theDaysFollowWhenTheAppointmentsChange() {
    val viewModel = loadedViewModel()
    month(NOVEMBER_FIRST).value = appointmentsOn(LocalDate.of(2026, 11, 20))
    assertEquals(setOf(20), viewModel.uiState.value.appointmentDays)
  }

  @Test
  fun theNextMonthGivesItsOwnDays() {
    month(NOVEMBER_FIRST).value = appointmentsOn(LocalDate.of(2026, 11, 12))
    month(DECEMBER_FIRST).value = appointmentsOn(LocalDate.of(2026, 12, 5))
    val viewModel = loadedViewModel()
    viewModel.showNextMonth()
    assertEquals(setOf(5), viewModel.uiState.value.appointmentDays)
  }

  @Test
  fun changingMonthForgetsThePreviousDaysBeforeTheNewOnesArrive() {
    month(NOVEMBER_FIRST).value = appointmentsOn(LocalDate.of(2026, 11, 12))
    val viewModel = loadedViewModel()
    observer.answer = { emptyFlow() }
    viewModel.showNextMonth()
    assertEquals(emptySet<Int>(), viewModel.uiState.value.appointmentDays)
  }

  @Test
  fun changingMonthDoesNotRestartARunningLoad() {
    val viewModel = loadedViewModel()
    viewModel.showNextMonth()
    assertEquals(1, observer.roles.size)
  }

  @Test
  fun changingMonthBeforeLoadingDoesNotLoad() {
    viewModel().showNextMonth()
    assertEquals(0, observer.roles.size)
  }

  @Test
  fun loadingAgainStopsThePreviousWatch() {
    val viewModel = loadedViewModel()
    viewModel.loadAppointments()
    assertEquals(1, month(NOVEMBER_FIRST).subscriptionCount.value)
  }

  // ---------- failures ----------

  @Test
  fun aLoadingFailureShowsAnError() {
    month(NOVEMBER_FIRST).value = Result.failure(IllegalStateException())
    val viewModel = loadedViewModel()
    assertEquals(R.string.calendarErrorLoadAppointments, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun clearErrorMsgRemovesTheError() {
    month(NOVEMBER_FIRST).value = Result.failure(IllegalStateException())
    val viewModel = loadedViewModel()
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun changingMonthRetriesALoadThatStopped() {
    observer.wholeAnswer = flowOf(Result.failure(IllegalStateException()))
    val viewModel = viewModel()
    viewModel.loadAppointments(isPatient = false)
    viewModel.showNextMonth()
    assertEquals(listOf(false, false), observer.roles)
  }

  // ---------- helpers ----------

  private fun viewModel() =
      CalendarViewModel(november, ZoneOffset.UTC, observer).also { viewModels += it }

  private fun loadedViewModel() = viewModel().also { it.loadAppointments() }

  private fun month(firstDay: LocalDate) =
      months.getOrPut(firstDay) { MutableStateFlow(Result.success(emptyList())) }

  private fun appointmentsOn(day: LocalDate): Result<List<Appointment>> {
    val noon = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    return Result.success(listOf(Appointment(startAt = noon, durationMinutes = 60)))
  }

  private companion object {
    val NOVEMBER_FIRST: LocalDate = LocalDate.of(2026, 11, 1)
    val DECEMBER_FIRST: LocalDate = LocalDate.of(2026, 12, 1)
  }
}
