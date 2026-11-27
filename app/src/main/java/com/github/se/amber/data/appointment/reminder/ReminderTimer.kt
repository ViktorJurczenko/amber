// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * The one timer of this phone. When it goes off, the reminder check runs and sets it again: for the
 * next reminder, or for the next check 6 hours later. Nobody sees the timer itself: the user only
 * sees the notification.
 */
interface ReminderTimer {

  /** Sets the timer for [atMillis], in epoch milliseconds. It replaces the previous one. */
  fun set(atMillis: Long)

  /** Cancels the timer, if one is set. */
  fun cancel()
}

/**
 * A [ReminderTimer] made of one [AlarmManager] alarm. Nothing rings: Android silently wakes the
 * app's [AppointmentReminderReceiver], which runs the check.
 *
 * It always uses the same [PendingIntent], so setting the timer again replaces the previous one.
 *
 * It goes off on time even when the phone is idle (Doze). In Doze, though, Android lets an app's
 * alarm go off at most once every 9 minutes. So two reminders a few minutes apart can come a few
 * minutes late.
 *
 * An exact alarm needs a permission. The app declares `USE_EXACT_ALARM`, granted at install from
 * Android 13: a calendar sending event reminders is one of the uses Google Play allows for it.
 * Android 12 uses `SCHEDULE_EXACT_ALARM` instead, which the user can turn off. Then the timer falls
 * back to an inexact alarm, which still goes off in Doze, but can come later.
 *
 * @param context any context of the app. Only the application context is kept.
 */
class AlarmManagerReminderTimer(context: Context) : ReminderTimer {

  private val context = context.applicationContext
  private val alarmManager = context.getSystemService(AlarmManager::class.java)

  override fun set(atMillis: Long) {
    if (AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
      AlarmManagerCompat.setExactAndAllowWhileIdle(
          alarmManager,
          AlarmManager.RTC_WAKEUP,
          atMillis,
          intent(),
      )
    } else {
      AlarmManagerCompat.setAndAllowWhileIdle(
          alarmManager,
          AlarmManager.RTC_WAKEUP,
          atMillis,
          intent(),
      )
    }
  }

  override fun cancel() = alarmManager.cancel(intent())

  /** The same intent every time, so the system sees one timer. */
  private fun intent(): PendingIntent =
      PendingIntent.getBroadcast(
          context,
          REQUEST_CODE,
          Intent(context, AppointmentReminderReceiver::class.java)
              .setAction(AppointmentReminderReceiver.ACTION_TIMER),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

  private companion object {
    // The app has a single timer, so one request code is enough
    const val REQUEST_CODE = 0
  }
}

/**
 * Sets the app's timer for now, so the reminder check runs at once, through the receiver. The app
 * calls it when it comes to the front and after a save, so a change is seen without waiting for the
 * next check.
 *
 * @param context any context of the app.
 */
fun checkRemindersNow(context: Context = Firebase.app.applicationContext) {
  AlarmManagerReminderTimer(context).set(System.currentTimeMillis())
}
