// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.session

import android.app.AlarmManager
import android.content.Context
import com.github.se.amber.data.appointment.reminder.AlarmManagerReminderTimer
import com.github.se.amber.model.authentication.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Checks the session repository the app builds, on Robolectric, where Firebase is initialized by
 * hand like the app does at startup. Nothing here contacts Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryProviderTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  @Before
  fun initializeFirebase() {
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

  @Test
  fun signingOutCancelsTheReminderTimerOfThisPhone() = runBlocking {
    val auth: AuthRepository = mockk { coEvery { signOut() } returns Result.success(Unit) }
    // fakes: the app-wide media and auth repositories are left for their own tests to build
    val repository =
        SessionRepositoryProvider.deviceRepository(
            mediaRepository = mockk(relaxed = true),
            authRepository = auth,
        )
    // FirebaseApp is set up once per test run, so its context can be the app of an earlier test:
    // the code uses that context, and so does this test
    val appContext = Firebase.app.applicationContext
    val alarms = shadowOf(appContext.getSystemService(AlarmManager::class.java))
    AlarmManagerReminderTimer(appContext).set(1_800_000_000_000L)

    assertTrue(repository.signOut().isSuccess)

    assertNull(alarms.peekNextScheduledAlarm())
    coVerify(exactly = 1) { auth.signOut() }
  }
}
