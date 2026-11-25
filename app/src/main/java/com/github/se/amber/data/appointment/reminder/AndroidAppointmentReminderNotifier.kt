// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.se.amber.R
import java.time.format.DateTimeFormatter

/**
 * Shows the reminders with Android's [NotificationManager], on their own channel, so the user can
 * turn them off alone. Tapping one opens the app.
 *
 * On Android 13 and later, it shows nothing until the user allows notifications in the system
 * settings, since the app doesn't ask for the permission yet.
 *
 * @param context any context of the app. Only the application context is kept.
 */
class AndroidAppointmentReminderNotifier(context: Context) : AppointmentReminderNotifier {

  private val context = context.applicationContext
  private val manager = context.getSystemService(NotificationManager::class.java)

  override fun show(reminder: ReminderNotification) {
    if (!canPost()) return
    // Creating a channel that exists already only updates its name, e.g. after a language change
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.appointmentReminderChannel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    )
    val openApp =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
          PendingIntent.getActivity(
              context,
              OPEN_APP_REQUEST_CODE,
              it,
              PendingIntent.FLAG_IMMUTABLE,
          )
        }
    val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.amber_logomark)
            .setContentTitle(context.getString(R.string.appointmentReminderTitle))
            .setContentText(reminderText(context, reminder))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
    manager.notify(reminder.tag, NOTIFICATION_ID, notification)
  }

  /**
   * Removes every reminder shown, for sign-out. Notifications on other channels stay, should the
   * app get some.
   */
  fun cancelAll() {
    manager.activeNotifications
        .filter { it.notification.channelId == CHANNEL_ID }
        .forEach { manager.cancel(it.tag, it.id) }
  }

  private fun canPost(): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
              PackageManager.PERMISSION_GRANTED

  internal companion object {
    const val CHANNEL_ID = "appointment-reminders"
    // Each occurrence has its own tag, so they can share an id
    const val NOTIFICATION_ID = 1
    // The app has a single intent for every reminder, so one request code is enough
    const val OPEN_APP_REQUEST_CODE = 0
  }
}

/**
 * The text of [reminder], in the language of [context]: "Tomorrow 14:00 · Doctor · with Marie". The
 * day is "Today", "Tomorrow", or the weekday and the date for a later day. Every word, pattern and
 * separator comes from strings.xml, so each language sets its own.
 */
internal fun reminderText(context: Context, reminder: ReminderNotification): String {
  val locale = context.resources.configuration.locales[0]
  fun format(patternId: Int) =
      reminder.start.format(DateTimeFormatter.ofPattern(context.getString(patternId), locale))
  val day =
      when (reminder.start.toLocalDate()) {
        reminder.today -> context.getString(R.string.appointmentReminderToday)
        reminder.today.plusDays(1) -> context.getString(R.string.appointmentReminderTomorrow)
        else -> format(R.string.appointmentReminderDatePattern)
      }
  val time = format(R.string.appointmentReminderTimePattern)
  return if (reminder.otherNames.isEmpty()) {
    context.getString(R.string.appointmentReminderText, day, time, reminder.title)
  } else {
    context.getString(
        R.string.appointmentReminderTextWith,
        day,
        time,
        reminder.title,
        namesText(context, reminder.otherNames),
    )
  }
}

/**
 * [names] as one list, in the language of [context]: "Marie", "Marie and Paul", "Marie, Paul and
 * Jean". The separator and the words before the last name come from strings.xml.
 *
 * @param names the names to list. It must not be empty.
 * @return the names, ready to show.
 */
private fun namesText(context: Context, names: List<String>): String =
    if (names.size == 1) {
      names.single()
    } else {
      val separator = context.getString(R.string.appointmentReminderNameSeparator)
      context.getString(
          R.string.appointmentReminderLastNames,
          names.dropLast(1).joinToString(separator),
          names.last(),
      )
    }
