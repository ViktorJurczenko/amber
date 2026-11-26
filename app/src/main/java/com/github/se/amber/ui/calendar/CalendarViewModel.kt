// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.appointment.AppointmentRepositoryProvider
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state of the calendar.
 *
 * @property month the month currently displayed.
 * @property appointmentDays the days of [month] (1 to 31) on which an appointment starts.
 * @property errorMsgRes the error to display as a message, as a string resource, if any.
 */
data class CalendarUIState(
    val month: YearMonth = YearMonth.now(),
    val appointmentDays: Set<Int> = emptySet(),
    @param:StringRes val errorMsgRes: Int? = null,
)

/**
 * ViewModel of [Calendar]: it owns the displayed month, the month navigation and the days that have
 * an appointment.
 *
 * The repositories are given as functions, so they are only built once the appointments are loaded.
 * A failure to build one is then handled like any other loading error.
 *
 * @param initialMonth the month displayed first. Defaults to the current month.
 * @param zone the time zone the days are counted in. Defaults to the phone's.
 * @param appointmentRepo gives the repository used to watch the appointments.
 * @param authRepo gives the repository used to access the currently authenticated user.
 * @param profileRepo gives the repository used to load the authenticated user's profile.
 * @param activeCircleRepo gives the repository used to find the care circle a caregiver works in.
 */
class CalendarViewModel(
    initialMonth: YearMonth = YearMonth.now(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val appointmentRepo: () -> AppointmentRepository = {
      AppointmentRepositoryProvider.default.repository
    },
    private val authRepo: () -> AuthRepository = { AuthRepositoryProvider.repository },
    private val profileRepo: () -> ProfileRepository = {
      ProfileRepositoryProvider.default.repository
    },
    private val activeCircleRepo: () -> ActiveCircleRepository = {
      ActiveCircleRepositoryProvider.default.repository
    },
) : ViewModel() {

  private val _uiState = MutableStateFlow(CalendarUIState(month = initialMonth))
  val uiState: StateFlow<CalendarUIState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  /** Displays the month before the current one. */
  fun showPreviousMonth() {
    showMonth(_uiState.value.month.minusMonths(1))
  }

  /** Displays the month after the current one. */
  fun showNextMonth() {
    showMonth(_uiState.value.month.plusMonths(1))
  }

  /** Clears the current error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsgRes = null) }
  }

  /**
   * Watches the appointments of the displayed month, and keeps [CalendarUIState.appointmentDays] up
   * to date when an appointment changes or when another month is displayed. Calling it again
   * restarts the watch.
   *
   * A patient only has one care circle. A caregiver sees the appointments of the active one.
   *
   * @param isPatient whether the user viewing the calendar is a patient.
   */
  fun loadAppointments(isPatient: Boolean = true) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        circleIds(isPatient).collectLatest { circleId ->
          _uiState.update { it.copy(appointmentDays = emptySet()) }
          if (circleId == null) return@collectLatest
          _uiState
              .map { it.month }
              .distinctUntilChanged()
              .collectLatest { month -> watchMonth(circleId, month) }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        showLoadError(e)
      }
    }
  }

  /** Displays [month]. The days of the previous month are forgotten. */
  private fun showMonth(month: YearMonth) {
    _uiState.update { it.copy(month = month, appointmentDays = emptySet()) }
  }

  /**
   * The id of the care circle whose appointments are shown, or null when there is none.
   *
   * Throws when the profile can't be read: that is a failure, not a missing circle.
   */
  private suspend fun circleIds(isPatient: Boolean): Flow<String?> {
    // Without a known user there is nothing to show. No message: the screens holding the calendar
    // already report their own sign-in and profile errors, and this one would hide them.
    val uid =
        runCatching { authRepo().currentUser?.uid }
            .onFailure { Log.e(TAG, "Reading the signed-in user failed", it) }
            .getOrNull() ?: return flowOf(null)
    val profile = profileRepo().getProfile(uid).getOrThrow() ?: return flowOf(null)
    return if (isPatient) flowOf(profile.circleIds.firstOrNull { it.isNotBlank() })
    else activeCircleRepo().activeCircleId(uid, profile.circleIds)
  }

  /**
   * Updates the days that have an appointment in [month], until canceled. An error is handled
   * here, so that displaying another month tries again.
   */
  private suspend fun watchMonth(circleId: String, month: YearMonth) {
    try {
      appointmentRepo()
          .observeAppointments(circleId, month.atDay(1), month.lengthOfMonth())
          .collect { appointments ->
            val days = appointmentDays(appointments.map { it.value }, month, zone)
            _uiState.update { it.copy(appointmentDays = days) }
          }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      showLoadError(e)
    }
  }

  private fun showLoadError(error: Exception) {
    Log.e(TAG, "Loading the appointments failed", error)
    _uiState.update { it.copy(errorMsgRes = R.string.calendarErrorLoadAppointments) }
  }

  private companion object {
    const val TAG = "CalendarViewModel"
  }
}
