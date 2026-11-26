// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checks the reminder notification: its text in English and French, its channel, and that sign-out
 * removes the reminders only.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAppointmentReminderNotifierTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager = context.getSystemService(NotificationManager::class.java)
  private val notifier = AndroidAppointmentReminderNotifier(context)

  @Before
  fun setUp() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun reminder(
      daysAfterToday: Long = 1,
      otherNames: List<String> = listOf("Marie"),
      tag: String = "t1",
  ) =
      ReminderNotification(
          tag = tag,
          title = "Doctor",
          start = TODAY.plusDays(daysAfterToday).atTime(14, 0),
          today = TODAY,
          otherNames = otherNames,
      )

  private fun shownText(): String =
      manager.activeNotifications
          .single()
          .notification
          .extras
          .getString(Notification.EXTRA_TEXT)
          .orEmpty()

  @Test
  fun showsTheDayTimeTitleAndTheOthersInEnglish() {
    notifier.show(reminder(otherNames = listOf("Marie", "Paul")))

    assertEquals("Tomorrow 14:00 · Doctor · with Marie and Paul", shownText())
    val shown = manager.activeNotifications.single()
    assertEquals("t1", shown.tag)
    assertEquals(
        "Upcoming appointment",
        shown.notification.extras.getString(Notification.EXTRA_TITLE),
    )
  }

  @Test
  fun expandingShowsTheWholeTextAndTheLockScreenCanHideIt() {
    notifier.show(reminder(otherNames = listOf("Marie", "Paul", "Jean")))

    val shown = manager.activeNotifications.single().notification
    assertEquals(
        "Tomorrow 14:00 · Doctor · with Marie, Paul and Jean",
        shown.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
    )
    assertEquals(Notification.VISIBILITY_PRIVATE, shown.visibility)
    // a lock screen that hides sensitive content shows the title only
    val lockScreen = shown.publicVersion.extras
    assertEquals("Upcoming appointment", lockScreen.getString(Notification.EXTRA_TITLE))
    assertNull(lockScreen.getCharSequence(Notification.EXTRA_TEXT))
  }

  @Test
  fun keepsOnlyTheApplicationContext() {
    // a context whose own services can't be used, like an activity's once it is gone
    val activity =
        object : ContextWrapper(context) {
          override fun getSystemService(name: String): Any = error("the activity's $name")
        }

    AndroidAppointmentReminderNotifier(activity).show(reminder())

    assertEquals(1, manager.activeNotifications.size)
  }

  @Test
  @Config(qualifiers = "fr")
  fun writesTheReminderInFrench() {
    notifier.show(reminder())

    assertEquals("Demain 14:00 · Doctor · avec Marie", shownText())
    assertEquals(
        "Rappels de rendez-vous",
        manager.getNotificationChannel(AndroidAppointmentReminderNotifier.CHANNEL_ID).name,
    )
    assertEquals(
        "Aujourd'hui 14:00 · Doctor",
        reminderText(context, reminder(daysAfterToday = 0, otherNames = emptyList())),
    )
    // Friday 15 January 2027 + 3 days: the weekday gets a capital letter
    assertEquals(
        "Lundi 18 janvier 14:00 · Doctor · avec Marie et Paul",
        reminderText(context, reminder(daysAfterToday = 3, otherNames = listOf("Marie", "Paul"))),
    )
    assertEquals(
        "Demain 14:00 · Doctor · avec Marie, Paul et Jean",
        reminderText(context, reminder(otherNames = listOf("Marie", "Paul", "Jean"))),
    )
  }

  @Test
  fun joinsTheLastTwoNamesWithAndInEnglish() {
    assertEquals(
        "Tomorrow 14:00 · Doctor · with Marie, Paul and Jean",
        reminderText(context, reminder(otherNames = listOf("Marie", "Paul", "Jean"))),
    )
  }

  @Test
  fun showingTheSameReminderAgainReplacesIt() {
    notifier.show(reminder())
    notifier.show(reminder(otherNames = emptyList()))

    assertEquals("Tomorrow 14:00 · Doctor", shownText())
  }

  @Test
  fun showsNothingWithoutThePermission() {
    shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    notifier.show(reminder())

    assertEquals(0, manager.activeNotifications.size)
  }

  @Test
  fun cancelAllRemovesTheRemindersButNoOtherNotification() {
    notifier.show(reminder(tag = "t1"))
    notifier.show(reminder(tag = "t2"))
    manager.createNotificationChannel(
        NotificationChannel("other", "Other", NotificationManager.IMPORTANCE_DEFAULT)
    )
    manager.notify(
        "other",
        1,
        Notification.Builder(context, "other")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build(),
    )

    notifier.cancelAll()

    assertEquals(listOf("other"), manager.activeNotifications.map { it.tag })
    assertNull(manager.activeNotifications.find { it.tag == "t1" })
  }

  private companion object {
    val TODAY: LocalDate = LocalDateTime.of(2027, 1, 15, 12, 0).toLocalDate()
  }
}
