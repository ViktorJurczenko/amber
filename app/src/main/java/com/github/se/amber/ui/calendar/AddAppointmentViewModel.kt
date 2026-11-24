// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.appointment.AppointmentError
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.appointment.AppointmentRepositoryProvider
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.AppointmentValidationError
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.calendar.DEFAULT_DURATION_MINUTES
import com.github.se.amber.model.location.GeoLocation
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * The screen only asks for a title and a note. Until it asks for the rest, the other fields of the
 * [Appointment] take the defaults below: it starts on the chosen day at the current time, lasts
 * [DEFAULT_DURATION_MINUTES] minutes, has an empty [GeoLocation] and no attendee. Leaving the
 * screen stays in the composable: this class does not navigate.
 *
 * @param appointmentRepo The repository used to save the appointment.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param activeCircleRepo The repository used to find the care circle the user is working in.
 */
open class AddAppointmentViewModel(
    private val appointmentRepo: AppointmentRepository =
        AppointmentRepositoryProvider.default.repository,
    private val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val activeCircleRepo: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddAppointmentUIState())

    /** The state shown by the add appointment screen. */
    val uiState: StateFlow<AddAppointmentUIState> = _uiState.asStateFlow()

    private val _events = Channel<AddAppointmentEvent>(Channel.BUFFERED)

    /** The one-shot events of the screen, such as [AddAppointmentEvent.Added]. */
    val events: Flow<AddAppointmentEvent> = _events.receiveAsFlow()

    /** Stores [title] as the title of the appointment. */
    fun setTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    /** Stores [note] as the note of the appointment. */
    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    /** Clears the current error message from the UI state. */
    fun clearErrorMsg() {
        _uiState.update { it.copy(errorMsgRes = null) }
    }

    /**
     * Adds the typed event to the appointments of the active care circle, starting on [date] at the
     * current time.
     *
     * Does nothing while an earlier add is still running or while the title is empty: the screen
     * already disables its button then ([AddAppointmentUIState.canAdd]). Any problem is shown through
     * [AddAppointmentUIState.errorMsgRes]. On success, [AddAppointmentEvent.Added] is sent.
     *
     * @param date the day of the appointment, the day clicked on the calendar. Today by default.
     */
    fun addAppointment(date: LocalDate = LocalDate.now()) {
        val state = _uiState.value
        if (!state.canAdd) return

        val appointment =
            Appointment(
                id = appointmentRepo.newId(),
                title = state.title,
                notes = state.note,
                // TODO: will need to add a field allowing the user to select
                //  1. a date
                //  2. a duration
                //  3. a location
                //  4. chips for all the attendee
                startAt =
                    date
                        .atTime(LocalTime.now())
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                durationMinutes = DEFAULT_DURATION_MINUTES,
                location = GeoLocation(),
                attendeeIds = emptyList(),
            )

        _uiState.update { it.copy(isAdding = true) }
        viewModelScope.launch {
            try {
                val circleId = activeCircleId()
                if (circleId == null) {
                    fail(R.string.addAppointmentErrorNoCircle)
                    return@launch
                }
                appointmentRepo
                    .addAppointment(circleId, appointment)
                    .onSuccess {
                        _events.send(AddAppointmentEvent.Added)
                        _uiState.update { it.copy(isAdding = false) }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Adding the appointment failed", error)
                        fail(errorMsgResOf(error))
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Adding the appointment failed", e)
                fail(R.string.addAppointmentErrorAdd)
            }
        }
    }

    /** The id of the care circle the signed-in user is working in, or null when there is none. */
    private suspend fun activeCircleId(): String? {
        val uid = authRepo.currentUser?.uid ?: return null
        val profile = profileRepo.getProfile(uid).getOrNull() ?: return null
        return activeCircleRepo.activeCircleId(uid, profile.circleIds).first()
    }

    /**
     * The message to show for a failed add, one for each [AppointmentError] the repository can give.
     * Any other failure gets the generic message.
     */
    @StringRes
    private fun errorMsgResOf(error: Throwable): Int =
        when (error) {
            is AppointmentError.InvalidCircleId -> R.string.addAppointmentErrorInvalidCircle
            is AppointmentError.InvalidAppointmentId -> R.string.addAppointmentErrorInvalidId
            is AppointmentError.InvalidAppointment ->
                when (error.reason) {
                    AppointmentValidationError.BLANK_TITLE -> R.string.addAppointmentErrorTitleEmpty
                    AppointmentValidationError.MISSING_START -> R.string.addAppointmentErrorMissingStart
                    AppointmentValidationError.INVALID_DURATION ->
                        R.string.addAppointmentErrorInvalidDuration
                }
            is AppointmentError.NotFound -> R.string.addAppointmentErrorNotFound
            is AppointmentError.NotAuthenticated -> R.string.addAppointmentErrorNotSignedIn
            is AppointmentError.PermissionDenied -> R.string.addAppointmentErrorPermissionDenied
            is AppointmentError.Unknown -> R.string.addAppointmentErrorAdd
            else -> R.string.addAppointmentErrorAdd
        }

    /** Stops the add and shows [errorMsgRes] as a message. */
    private fun fail(@StringRes errorMsgRes: Int) {
        _uiState.update { it.copy(isAdding = false, errorMsgRes = errorMsgRes) }
    }

    private companion object {
        const val TAG = "AddAppointmentViewModel"
    }
}
