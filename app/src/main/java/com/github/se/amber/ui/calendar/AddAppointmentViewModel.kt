// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * UI state of the add appointment screen.
 *
 * @property title the title of the appointment typed by the caregiver.
 * @property note the note of the appointment typed by the caregiver.
 * @property isAdding whether the appointment is being saved, so it is not added twice.
 * @property errorMsgRes the error to display as a message, as a string resource, if any.
 */
data class AddAppointmentUIState(
    val title: String = "",
    val note: String = "",
    val isAdding: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
) {
  /** Whether the event has a title and is not being added already, so the button can be pressed. */
  val canAdd: Boolean
    get() = title.isNotBlank() && !isAdding
}

/** One-shot events sent by [AddAppointmentViewModel] to the add appointment screen. */
sealed interface AddAppointmentEvent {
  /** The appointment was saved, so the screen can be left. */
  data object Added : AddAppointmentEvent
}

/**
 * Holds the add appointment screen state.
 *
 * Only the functions the screen calls are declared here. Their bodies are not implemented yet.
 * Leaving the screen stays in the composable: this class does not navigate.
 */
open class AddAppointmentViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(AddAppointmentUIState())

  /** The state shown by the add appointment screen. */
  val uiState: StateFlow<AddAppointmentUIState> = _uiState.asStateFlow()

  /** The one-shot events of the screen, such as [AddAppointmentEvent.Added]. */
  val events: Flow<AddAppointmentEvent> = emptyFlow()

  /** Stores [title] as the title of the appointment. */
  fun setTitle(title: String) {
    // Todo: save the title in the UI state
  }

  /** Stores [note] as the note of the appointment. */
  fun setNote(note: String) {
    // Todo: save the note in the UI state
  }

  /** Clears the current error message from the UI state. */
  fun clearErrorMsg() {
    // Todo: clear the error message from the UI state
  }

  /**
   * Adds the typed event to the appointments of the active care circle, starting on [date].
   *
   * @param date the day of the appointment, the day clicked on the calendar. Today by default.
   */
  fun addAppointment(date: LocalDate = LocalDate.now()) {
    // Todo: add the event to the existing appointments
  }
}
