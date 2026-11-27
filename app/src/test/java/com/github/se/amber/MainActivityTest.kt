// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.data.appointment.reminder.AppointmentReminderReceiver
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Checks that the app runs the reminder check each time it comes to the front.
 *
 * Signed out, the app opens the sign-in screen. Its repository is replaced, so the app-wide one
 * isn't built here.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

  @Before
  fun setUp() {
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns mockk(relaxed = true)
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
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun comingToTheFrontSetsTheReminderTimerForNow() {
    val before = System.currentTimeMillis()

    Robolectric.buildActivity(MainActivity::class.java).setup().use {
      val alarm = alarms.scheduledAlarms.single()
      assertTrue(alarm.triggerAtMs in before..System.currentTimeMillis())
      assertEquals(
          AppointmentReminderReceiver.ACTION_TIMER,
          shadowOf(alarm.operation).savedIntent.action,
      )
    }
  }

  @Test
  fun mainAndSecondActivitiesAreLockedToPortraitInManifestAndAtRuntime() {
    val packageManager = context.packageManager
    val mainInfo =
        packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA,
        )
    val secondInfo =
        packageManager.getActivityInfo(
            ComponentName(context, SecondActivity::class.java),
            PackageManager.GET_META_DATA,
        )
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, mainInfo.screenOrientation)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, secondInfo.screenOrientation)

    Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
      assertEquals(
          ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
          controller.get().requestedOrientation,
      )
    }
    Robolectric.buildActivity(SecondActivity::class.java).setup().use { controller ->
      assertEquals(
          ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
          controller.get().requestedOrientation,
      )
    }
  }
}
