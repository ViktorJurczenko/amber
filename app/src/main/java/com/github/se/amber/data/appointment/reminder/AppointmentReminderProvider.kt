// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import androidx.work.WorkerFactory
import com.github.se.amber.data.appointment.AppointmentRepositoryProvider
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.data.work.AppointmentReminderWorkerFactory
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds a single [AppointmentReminderPoster], built lazily by `factory` on first access, and the
 * factory of the reminder jobs that use it.
 *
 * Only the app's WorkManager setup uses it, through [workerFactory]. ViewModels never do. With the
 * default factory, the default Firebase app must be initialized before [poster] is first accessed;
 * if building fails, the next access tries again.
 */
class AppointmentReminderProvider(
    factory: () -> AppointmentReminderPoster = { firebasePoster() },
) {
  val poster: AppointmentReminderPoster by lazy(factory)

  /**
   * Creates the reminder jobs with [poster]. Building it doesn't build the poster: a job asks for
   * it only when it runs.
   */
  val workerFactory: WorkerFactory = AppointmentReminderWorkerFactory { poster }

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = AppointmentReminderProvider()

    private fun firebasePoster() =
        AppointmentReminderPoster(
            appointmentRepository = AppointmentRepositoryProvider.default.repository,
            careCircleRepository = CareCircleRepositoryProvider.default.repository,
            profileRepository = ProfileRepositoryProvider.default.repository,
            authRepository = AuthRepositoryProvider.repository,
            notifier = AndroidAppointmentReminderNotifier(Firebase.app.applicationContext),
        )
  }
}
