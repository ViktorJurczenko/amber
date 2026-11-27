// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

/** Checks which broadcasts run the reminder check, and that a slow check is stopped in time. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppointmentReminderReceiverTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val check: AppointmentReminderCheck = mockk(relaxed = true)
  private val scope = TestScope()
  private val receiver = AppointmentReminderReceiver({ check }, scope)

  @After
  fun tearDown() {
    unmockkAll()
  }

  private fun receive(action: String) {
    receiver.onReceive(context, Intent(action))
    scope.runCurrent()
  }

  @Test
  fun theTimerARestartAndAnUpdateEachRunTheCheck() {
    receive(AppointmentReminderReceiver.ACTION_TIMER)
    coVerify(exactly = 1) { check.run() }

    receive(Intent.ACTION_BOOT_COMPLETED)
    coVerify(exactly = 2) { check.run() }

    receive(Intent.ACTION_MY_PACKAGE_REPLACED)
    coVerify(exactly = 3) { check.run() }
  }

  @Test
  fun androidCanCreateTheReceiverWithNoArguments() {
    mockkObject(AppointmentReminderCheckProvider.Companion)
    every { AppointmentReminderCheckProvider.default } returns
        AppointmentReminderCheckProvider { check }

    // the constructor Android calls, so the app's check and scope are the defaults
    AppointmentReminderReceiver::class
        .java
        .getDeclaredConstructor()
        .newInstance()
        .onReceive(context, Intent(AppointmentReminderReceiver.ACTION_TIMER))
    coVerify(exactly = 1, timeout = 5_000) { check.run() }

    // a broadcast delivered by the system, which creates the receiver from the manifest
    context.sendBroadcast(
        Intent(AppointmentReminderReceiver.ACTION_TIMER)
            .setClass(context, AppointmentReminderReceiver::class.java)
    )
    shadowOf(Looper.getMainLooper()).idle()
    coVerify(exactly = 2, timeout = 5_000) { check.run() }
  }

  @Test
  fun anyOtherBroadcastIsIgnored() {
    receive(Intent.ACTION_TIME_CHANGED)

    coVerify(exactly = 0) { check.run() }
  }

  @Test
  fun aSlowCheckIsStoppedAfterNineSeconds() {
    var stopped = false
    coEvery { check.run() } coAnswers
        {
          try {
            awaitCancellation()
          } finally {
            stopped = true
          }
        }

    receive(AppointmentReminderReceiver.ACTION_TIMER)
    scope.advanceTimeBy(8_999)
    assertFalse(stopped)

    scope.advanceTimeBy(2)
    assertTrue(stopped)
  }

  @Test
  fun aFailedCheckIsOnlyLogged() {
    coEvery { check.run() } throws IllegalStateException("Boom")

    receive(AppointmentReminderReceiver.ACTION_TIMER)
    receive(AppointmentReminderReceiver.ACTION_TIMER)

    coVerify(exactly = 2) { check.run() }
    assertEquals(
        listOf("The reminder check failed", "The reminder check failed"),
        ShadowLog.getLogsForTag(AppointmentReminderReceiver.TAG).map { it.msg },
    )
  }

  @Test
  fun aStoppedCheckIsPassedOnAndNotLoggedAsAFailure() {
    coEvery { check.run() } throws CancellationException("stopped")

    receive(AppointmentReminderReceiver.ACTION_TIMER)

    coVerify(exactly = 1) { check.run() }
    assertTrue(ShadowLog.getLogsForTag(AppointmentReminderReceiver.TAG).isEmpty())
  }

  @Test
  fun theManifestRegistersItForARestartAndAnUpdate() {
    for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
      val receivers =
          context.packageManager.queryBroadcastReceivers(
              Intent(action).setPackage(context.packageName),
              0,
          )
      assertEquals(
          action,
          listOf(AppointmentReminderReceiver::class.java.name),
          receivers.map { it.activityInfo.name },
      )
    }
    val permissions =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()
    assertTrue(permissions.containsAll(listOf(USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED)))
  }

  private companion object {
    const val USE_EXACT_ALARM = "android.permission.USE_EXACT_ALARM"
    const val RECEIVE_BOOT_COMPLETED = "android.permission.RECEIVE_BOOT_COMPLETED"
  }
}
