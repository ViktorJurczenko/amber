// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Checks that sign-out clears every reminder of this phone, even after a failed step. */
@RunWith(RobolectricTestRunner::class)
class LocalRemindersTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun clearAllRunsEveryStepEvenAfterAFailure() {
    val steps = mutableListOf<String>()
    val timer =
        object : ReminderTimer {
          override fun set(atMillis: Long) = Unit

          override fun cancel() {
            steps += "timer"
            throw IllegalStateException("Timer left")
          }
        }
    val store: ReminderCheckStore = mockk { coEvery { clear() } answers { steps += "store" } }
    val reminders = LocalReminders(timer, store) { steps += "shown" }

    val error =
        assertThrows(IllegalStateException::class.java) { runBlocking { reminders.clearAll() } }

    assertEquals("Timer left", error.message)
    assertEquals(listOf("timer", "store", "shown"), steps)
  }

  @Test
  fun theAndroidRemindersCancelTheTimerAndRemoveTheShownReminders() = runBlocking {
    shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    val notifications = context.getSystemService(NotificationManager::class.java)
    // the store the app's check uses, so the next user doesn't start from this time
    val store = DataStoreReminderCheckStore(context.reminderCheckDataStore)
    store.setLastCheckedAt("anna", 1_000L)
    AlarmManagerReminderTimer(context).set(1_800_000_000_000L)
    val today = LocalDate.of(2027, 1, 15)
    AndroidAppointmentReminderNotifier(context)
        .show(ReminderNotification("t1", "Doctor", today.atTime(14, 0), today, emptyList()))
    assertEquals(1_000L, store.lastCheckedAt("anna"))
    assertNotNull(alarms.peekNextScheduledAlarm())
    assertEquals(listOf("t1"), notifications.activeNotifications.map { it.tag })

    LocalReminders.android(context).clearAll()

    assertNull(store.lastCheckedAt("anna"))
    assertNull(alarms.peekNextScheduledAlarm())
    assertEquals(emptyList<String>(), notifications.activeNotifications.map { it.tag })
  }
}
