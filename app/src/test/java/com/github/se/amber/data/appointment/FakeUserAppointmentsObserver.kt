// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.model.appointment.Appointment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

/**
 * A [UserAppointmentsObserver] for tests: [answer] gives what is observed for each window. It never
 * builds a repository.
 *
 * @property answer the values observed for a window.
 */
class FakeUserAppointmentsObserver(
    var answer: (AppointmentWindow) -> Flow<Result<List<Appointment>>>,
) : UserAppointmentsObserver() {

  /** The role given on each call, in order. */
  val roles = mutableListOf<Boolean>()

  /** The windows watched, in order. */
  val windows = mutableListOf<AppointmentWindow>()

  /** When not null, replaces the whole observed flow, for a flow that ends. */
  var wholeAnswer: Flow<Result<List<Appointment>>>? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeUserAppointments(
      isPatient: Boolean,
      windows: Flow<AppointmentWindow>,
  ): Flow<Result<List<Appointment>>> {
    roles += isPatient
    return wholeAnswer ?: windows.onEach { this.windows += it }.flatMapLatest { answer(it) }
  }
}
