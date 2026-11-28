// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import android.util.Log
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A span of whole days to watch the appointments of. See
 * [AppointmentRepository.observeAppointments] for how it is counted.
 *
 * @property firstDay the first day of the window. It is included.
 * @property days how many days the window holds. It must be at least 1.
 */
data class AppointmentWindow(val firstDay: LocalDate = LocalDate.now(), val days: Int = 1)

/**
 * Watches the appointments the signed-in user can see: those of their care circle. It can be used
 * by any ViewModel that shows appointments.
 *
 * The repositories are given as functions, so they are only built once the appointments are
 * watched. A failure to build one then reaches the collector, like any other loading error.
 *
 * @param appointmentRepo gives the repository used to watch the appointments.
 * @param authRepo gives the repository used to access the currently authenticated user.
 * @param profileRepo gives the repository used to load the authenticated user's profile.
 * @param activeCircleRepo gives the repository used to find the care circle a caregiver works in.
 */
open class UserAppointmentsObserver(
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
) {

  /**
   * Watches the appointments that happen, at least in part, during the latest window of [windows].
   * The care circle is looked up once, so a new window doesn't read the profile again.
   *
   * A patient only has one care circle. A caregiver sees the appointments of the active one, and
   * the list follows when they switch to another circle. The list is empty when there is no
   * signed-in user, no profile or no care circle. It is also empty while a window loads.
   *
   * @param isPatient whether the signed-in user is a patient.
   * @param windows the windows to watch. Each one replaces the one before. For a single window, use
   *   `flowOf`.
   * @return the live list, sorted by start, or the failure to read it. After a window fails, the
   *   next window is still watched. When the care circle can't be looked up, the failure is the
   *   last value: collect again to retry.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  open fun observeUserAppointments(
      isPatient: Boolean = true,
      windows: Flow<AppointmentWindow> = flowOf(AppointmentWindow()),
  ): Flow<Result<List<Appointment>>> =
      circleIds(isPatient)
          .flatMapLatest { circleId ->
            windows.flatMapLatest { window -> observeWindow(circleId, window) }
          }
          .catch { emit(Result.failure(it)) }

  /** Watches the appointments of the circle [circleId] during [window]. */
  private fun observeWindow(
      circleId: String?,
      window: AppointmentWindow,
  ): Flow<Result<List<Appointment>>> {
    if (circleId == null) return flowOf(Result.success(emptyList()))
    return flow {
      emitAll(
          appointmentRepo().observeAppointments(circleId, window.firstDay, window.days).map {
              appointments ->
            Result.success(appointments.map { it.value })
          }
      )
    }
        // The list of the previous window or circle is dropped while this one loads.
        .onStart { emit(Result.success(emptyList())) }
        // Caught here, so that the next window is still watched.
        .catch { emit(Result.failure(it)) }
  }

  /**
   * The id of the care circle whose appointments are watched, or null when there is none.
   *
   * The user and the profile are read when the flow is collected. The flow fails when the profile
   * can't be read: that is a failure, not a missing circle.
   */
  private fun circleIds(isPatient: Boolean): Flow<String?> = flow {
    // Without a known user there is nothing to show. It is not reported as a failure: the screens
    // already report their own sign-in and profile errors, and this one would hide them.
    val uid = runCatching {
      authRepo().currentUser?.uid
    }.onFailure { Log.e(TAG, "Reading the signed-in user failed", it) }.getOrNull()
    val profile = uid?.let { profileRepo().getProfile(it).getOrThrow() }
    when {
      uid == null || profile == null -> emit(null)
      isPatient -> emit(profile.circleIds.firstOrNull { it.isNotBlank() })
      else -> emitAll(activeCircleRepo().activeCircleId(uid, profile.circleIds))
    }
  }

  private companion object {
    const val TAG = "UserAppointmentsObserver"
  }
}
