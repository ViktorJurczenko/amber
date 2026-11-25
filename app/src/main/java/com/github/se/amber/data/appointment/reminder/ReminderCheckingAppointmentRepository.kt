// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

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
      repository.addAppointment(circle, appointment).onSuccess { checkNow() }

  override suspend fun updateAppointment(circleId: String, appointment: Appointment) =
      repository.updateAppointment(circleId, appointment).onSuccess { checkNow() }
}
