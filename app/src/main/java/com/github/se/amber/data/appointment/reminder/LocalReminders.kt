// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import com.github.se.amber.data.runEveryStep

/**
 * The appointment reminders this phone keeps for the signed-in user: the timer, the time of the
 * last check, and the reminders already shown.
 *
 * Sign-out and account deletion clear them through `DeviceLocalPhone`. Without it, the next user of
 * the phone would get the old user's reminders. The timer is set again the next time the app opens,
 * or saves an appointment.
 *
 * @param timer the timer of the next reminder.
 * @param store the time of the last check.
 * @param cancelShown removes the reminders already shown.
 */
class LocalReminders(
    private val timer: ReminderTimer,
    private val store: ReminderCheckStore,
    private val cancelShown: () -> Unit,
) {

  /**
   * Cancels the timer, forgets the last check, and removes the reminders shown. Every step is still
   * attempted when an earlier one fails. A cancellation stops at once and is rethrown.
   *
   * @throws Exception the first failure, with the later ones added to it.
   */
  suspend fun clearAll() = runEveryStep({ timer.cancel() }, { store.clear() }, { cancelShown() })

  companion object {
    /** The reminders of the app, on this phone's alarm manager, DataStore and notifications. */
    fun android(context: Context): LocalReminders =
        LocalReminders(
            timer = AlarmManagerReminderTimer(context),
            store = DataStoreReminderCheckStore(context.applicationContext.reminderCheckDataStore),
            cancelShown = AndroidAppointmentReminderNotifier(context)::cancelAll,
        )
  }
}
