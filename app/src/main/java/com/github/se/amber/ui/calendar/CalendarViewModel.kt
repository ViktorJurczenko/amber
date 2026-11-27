// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.appointment.AppointmentWindow
import com.github.se.amber.data.appointment.UserAppointmentsObserver
import com.github.se.amber.model.appointment.Appointment
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * @param initialMonth the month displayed first. Defaults to the current month.
 * @param zone the time zone the days are counted in. Defaults to the phone's.
 * @param appointmentsObserver watches the appointments of the signed-in user.
 */
class CalendarViewModel(
    initialMonth: YearMonth = YearMonth.now(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val appointmentsObserver: UserAppointmentsObserver = UserAppointmentsObserver(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CalendarUIState(month = initialMonth))
  val uiState: StateFlow<CalendarUIState> = _uiState.asStateFlow()

  private var loadJob: Job? = null
  private var loadedAsPatient = true

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
   * @param isPatient whether the user viewing the calendar is a patient.
   */
  fun loadAppointments(isPatient: Boolean = true) {
    loadedAsPatient = isPatient
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      val windows =
          _uiState
              .map { it.month }
              .distinctUntilChanged()
              .map { AppointmentWindow(it.atDay(1), it.lengthOfMonth()) }
      appointmentsObserver.observeUserAppointments(isPatient, windows).collect { result ->
        result.onSuccess(::showAppointments).onFailure(::showLoadError)
      }
    }
  }

  /**
   * Displays [month]. The days of the previous month are forgotten. If the loading has stopped
   * after a failure, it is tried again.
   */
  private fun showMonth(month: YearMonth) {
    _uiState.update { it.copy(month = month, appointmentDays = emptySet()) }
    if (loadJob?.isCompleted == true) loadAppointments(loadedAsPatient)
  }

  /** Marks the days of the displayed month on which one of the [appointments] starts. */
  private fun showAppointments(appointments: List<Appointment>) {
    _uiState.update { it.copy(appointmentDays = appointmentDays(appointments, it.month, zone)) }
  }

  private fun showLoadError(error: Throwable) {
    Log.e(TAG, "Loading the appointments failed", error)
    _uiState.update { it.copy(errorMsgRes = R.string.calendarErrorLoadAppointments) }
  }

  private companion object {
    const val TAG = "CalendarViewModel"
  }
}
