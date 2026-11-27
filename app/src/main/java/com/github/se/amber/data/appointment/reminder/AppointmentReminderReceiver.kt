// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs the reminder check when Android wakes the app for it, even with the app closed:
 * - when the timer goes off ([ACTION_TIMER]): at a reminder, for the check every 6 hours, or when
 *   the app sets it for now;
 * - after a restart (`BOOT_COMPLETED`), since a restart clears the timer. A reminder due while the
 *   phone was off then comes once it is back on;
 * - after an app update (`MY_PACKAGE_REPLACED`), to set the timer again, to be safe.
 *
 * Android gives a receiver about 10 seconds. It keeps the app awake with `goAsync`, and stops the
 * check after [TIME_LIMIT]. Offline, the check reads what the phone has already loaded, so it
 * doesn't wait for the server.
 *
 * Android creates it itself, with no arguments, so it uses the app's check by default.
 *
 * @param check gives the check to run, only once Android wakes the app.
 * @param scope where the check runs.
 */
class AppointmentReminderReceiver(
    private val check: () -> AppointmentReminderCheck = {
      AppointmentReminderCheckProvider.default.check
    },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in ACTIONS) return
    // null when a test calls onReceive itself
    val pending: PendingResult? = goAsync()
    scope.launch {
      try {
        withTimeout(TIME_LIMIT) { check().run() }
      } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "The reminder check took too long", e)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "The reminder check failed", e)
      } finally {
        pending?.finish()
      }
    }
  }

  companion object {
    internal const val TAG = "AppointmentReminderReceiver"

    /** The action of the timer's intent. */
    const val ACTION_TIMER = "com.github.se.amber.action.APPOINTMENT_REMINDER"

    /** Below Android's limit of about 10 seconds, so the receiver always finishes in time. */
    internal val TIME_LIMIT = 9.seconds

    private val ACTIONS =
        setOf(
            ACTION_TIMER,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
  }
}
