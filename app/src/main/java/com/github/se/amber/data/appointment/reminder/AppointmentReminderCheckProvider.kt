// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import com.github.se.amber.data.appointment.AppointmentRepositoryProvider
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds a single [AppointmentReminderCheck], built lazily by `factory` on first access.
 *
 * Only the timer's receiver uses it. There is one check for the app, so two runs never overlap.
 * ViewModels never use it. With the default factory, the default Firebase app must be initialized
 * before [check] is first accessed; if building fails, the next access tries again.
 */
class AppointmentReminderCheckProvider(
    factory: () -> AppointmentReminderCheck = { firebaseCheck() },
) {
  val check: AppointmentReminderCheck by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = AppointmentReminderCheckProvider()

    /** Builds the check of the app, on this phone's alarm manager and DataStore. */
    internal fun firebaseCheck(
        context: Context = Firebase.app.applicationContext,
    ): AppointmentReminderCheck =
        AppointmentReminderCheck(
            appointmentRepository = AppointmentRepositoryProvider.default.repository,
            profileRepository = ProfileRepositoryProvider.default.repository,
            activeCircleRepository = ActiveCircleRepositoryProvider.default.repository,
            authRepository = AuthRepositoryProvider.repository,
            poster = AppointmentReminderProvider.default.poster,
            store = DataStoreReminderCheckStore(context.reminderCheckDataStore),
            timer = AlarmManagerReminderTimer(context),
        )
  }
}
