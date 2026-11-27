// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Checks the one alarm of the reminder timer, on Robolectric's alarm manager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmManagerReminderTimerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
  private val timer = AlarmManagerReminderTimer(context)

  @Before
  fun initializeFirebase() {
    val context: Context = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
              .setApplicationId("1:123456789012:android:abcdef123456")
              .setApiKey("test-api-key")
              .setProjectId("amber-test")
              .build(),
      )
    }
  }

  @After
  fun allowExactAlarmsAgain() {
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
  }

  @Test
  fun setsOneExactWakeUpAlarmAtTheTimeThatRunsTheReceiver() {
    ShadowAlarmManager.setCanScheduleExactAlarms(true)

    timer.set(AT)

    val alarm = alarms.scheduledAlarms.single()
    assertEquals(AT, alarm.triggerAtMs)
    assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
    assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
    assertTrue(alarm.isAllowWhileIdle)
    val intent = shadowOf(alarm.operation).savedIntent
    assertEquals(AppointmentReminderReceiver.ACTION_TIMER, intent.action)
    assertEquals(AppointmentReminderReceiver::class.java.name, intent.component?.className)
  }

  @Test
  fun settingItAgainReplacesThePreviousOne() {
    timer.set(AT)
    timer.set(AT + 60_000)

    assertEquals(AT + 60_000, alarms.scheduledAlarms.single().triggerAtMs)
  }

  @Test
  fun cancelRemovesIt() {
    timer.set(AT)

    timer.cancel()

    assertNull(alarms.peekNextScheduledAlarm())
  }

  @Test
  fun checkRemindersNowSetsTheTimerForNow() {
    val before = System.currentTimeMillis()

    checkRemindersNow()

    // FirebaseApp is set up once per test run, so its context can be the app of an earlier test:
    // the code uses that context, and so does this test
    val appContext = Firebase.app.applicationContext
    val at =
        shadowOf(appContext.getSystemService(AlarmManager::class.java))
            .scheduledAlarms
            .single()
            .triggerAtMs
    assertTrue(at in before..System.currentTimeMillis())
  }

  @Test
  fun fallsBackToAnInexactAlarmThatStillGoesOffInDozeWhenExactOnesArentAllowed() {
    ShadowAlarmManager.setCanScheduleExactAlarms(false)

    timer.set(AT)

    val alarm = alarms.scheduledAlarms.single()
    assertEquals(AT, alarm.triggerAtMs)
    assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
    assertTrue(alarm.isAllowWhileIdle)
  }

  private companion object {
    const val AT = 1_800_000_000_000L
  }
}
