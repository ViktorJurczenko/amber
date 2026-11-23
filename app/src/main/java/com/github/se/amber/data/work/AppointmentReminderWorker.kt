// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.se.amber.data.appointment.reminder.AppointmentReminderPoster
import com.github.se.amber.data.appointment.reminder.DueReminder
import kotlinx.coroutines.CancellationException

/**
 * The background job showing one appointment reminder once it is due. WorkManager runs it, even
 * after the app was closed or the phone restarted. Its input is [inputData].
 *
 * It doesn't trust what was scheduled. The appointment may have changed since, so the poster reads
 * it again first and shows the reminder only if it is still wanted (see
 * [AppointmentReminderPoster]).
 *
 * It runs once and never retries. A reminder that can't be checked is not shown: a retry could show
 * it after the appointment.
 */
class AppointmentReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val poster: () -> AppointmentReminderPoster,
) : CoroutineWorker(context, params) {

  /** @return [Result.failure] if its input lacks a value, [Result.success] otherwise. */
  override suspend fun doWork(): Result {
    val reminder = readInput() ?: return Result.failure()
    try {
      poster().post(reminder)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't check the reminder of appointment ${reminder.appointmentId}", e)
    }
    return Result.success()
  }

  private fun readInput(): DueReminder? {
    val startAt = inputData.getLong(KEY_START_AT, -1)
    val minutes = inputData.getInt(KEY_MINUTES_BEFORE, -1)
    return DueReminder(
            circleId = inputData.getString(KEY_CIRCLE_ID) ?: return null,
            appointmentId = inputData.getString(KEY_APPOINTMENT_ID) ?: return null,
            startAt = startAt,
            minutesBefore = minutes,
            notificationTag = inputData.getString(KEY_NOTIFICATION_TAG) ?: return null,
        )
        .takeIf { startAt >= 0 && minutes >= 0 }
  }

  companion object {
    internal const val TAG = "AppointmentReminder"
    private const val KEY_CIRCLE_ID = "circleId"
    private const val KEY_APPOINTMENT_ID = "appointmentId"
    private const val KEY_START_AT = "startAt"
    private const val KEY_MINUTES_BEFORE = "minutesBefore"
    private const val KEY_NOTIFICATION_TAG = "notificationTag"

    /** The input of the job showing [reminder]. */
    fun inputData(reminder: DueReminder): Data =
        workDataOf(
            KEY_CIRCLE_ID to reminder.circleId,
            KEY_APPOINTMENT_ID to reminder.appointmentId,
            KEY_START_AT to reminder.startAt,
            KEY_MINUTES_BEFORE to reminder.minutesBefore,
            KEY_NOTIFICATION_TAG to reminder.notificationTag,
        )
  }
}

/**
 * Creates the [AppointmentReminderWorker]s with their poster, since WorkManager can only pass them
 * a context and their parameters. It is one of the factories of [AmberWorkerFactory].
 *
 * @param poster gives the poster. Called only when a worker runs, so building this factory or a
 *   worker doesn't build the repositories.
 */
class AppointmentReminderWorkerFactory(private val poster: () -> AppointmentReminderPoster) :
    WorkerFactory() {

  /** An [AppointmentReminderWorker] if [workerClassName] names it, else null. */
  override fun createWorker(
      appContext: Context,
      workerClassName: String,
      workerParameters: WorkerParameters,
  ): ListenableWorker? =
      if (workerClassName == AppointmentReminderWorker::class.java.name) {
        AppointmentReminderWorker(appContext, workerParameters, poster)
      } else {
        null
      }
}
