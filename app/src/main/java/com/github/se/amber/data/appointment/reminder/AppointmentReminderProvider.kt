// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds a single [AppointmentReminderPoster], built lazily by `factory` on first access.
 *
 * Only the reminder check uses it. ViewModels never do. With the default factory, the default
 * Firebase app must be initialized before [poster] is first accessed; if building fails, the next
 * access tries again.
 */
class AppointmentReminderProvider(
    factory: () -> AppointmentReminderPoster = { firebasePoster() },
) {
  val poster: AppointmentReminderPoster by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = AppointmentReminderProvider()

    /**
     * Builds the poster of the app. Its tests pass their own [authRepository], so they don't build
     * the app-wide one that other tests check.
     */
    internal fun firebasePoster(
        authRepository: AuthRepository = AuthRepositoryProvider.repository
    ): AppointmentReminderPoster =
        AppointmentReminderPoster(
            careCircleRepository = CareCircleRepositoryProvider.default.repository,
            profileRepository = ProfileRepositoryProvider.default.repository,
            authRepository = authRepository,
            notifier = AndroidAppointmentReminderNotifier(Firebase.app.applicationContext),
        )
  }
}
