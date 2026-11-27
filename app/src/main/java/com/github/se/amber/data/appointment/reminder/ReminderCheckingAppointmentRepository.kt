// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.util.Log
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.circle.CareCircle

/**
 * An [AppointmentRepository] that runs the reminder check after each save made on this phone.
 *
 * A new appointment, or an edit, can bring a reminder sooner than the timer, or one already due.
 * The check sees it at once instead of at the next check, up to 6 hours later. A deletion needs
 * nothing: the next check leaves the appointment out.
 *
 * The check only runs after the save, and a failure to run it is only logged. So a save that worked
 * is always reported as a success, as the repository promises.
 *
 * Everything else goes to [repository] unchanged.
 *
 * @param repository the repository that saves the appointments.
 * @param checkNow runs the check soon, after a successful save.
 */
class ReminderCheckingAppointmentRepository(
    private val repository: AppointmentRepository,
    private val checkNow: () -> Unit,
) : AppointmentRepository by repository {

  override suspend fun addAppointment(circle: CareCircle, appointment: Appointment) =
      repository.addAppointment(circle, appointment).onSuccess { tryCheckNow() }

  override suspend fun updateAppointment(circleId: String, appointment: Appointment) =
      repository.updateAppointment(circleId, appointment).onSuccess { tryCheckNow() }

  /** Runs [checkNow]. A failure is only logged: the save already worked. */
  private fun tryCheckNow() {
    try {
      checkNow()
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't run the reminder check after a save", e)
    }
  }

  private companion object {
    const val TAG = "ReminderCheckingRepo"
  }
}
