// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.data.appointment.AppointmentRepositoryProvider
import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Checks that [AppointmentReminderCheckProvider] builds its check once, and again after a failure,
 * and that the app's check uses the phone's timer.
 */
@RunWith(RobolectricTestRunner::class)
class AppointmentReminderCheckProviderTest {
  private val check: AppointmentReminderCheck = mockk()

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
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun checkIsBuiltOnlyOnce() {
    var builds = 0
    val provider = AppointmentReminderCheckProvider {
      builds++
      check
    }

    assertSame(check, provider.check)
    assertSame(check, provider.check)
    assertEquals(1, builds)
  }

  @Test
  fun checkIsBuiltAgainAfterAFactoryFailure() {
    var attempts = 0
    val provider = AppointmentReminderCheckProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      check
    }

    runCatching { provider.check }

    assertSame(check, provider.check)
  }

  @Test
  fun theAppsCheckSetsTheAlarmOfThisPhone() = runBlocking {
    // the app-wide providers are replaced, so nothing here builds them or opens Firebase
    val signedOut: AuthRepository = mockk { every { currentUser } returns null }
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns signedOut
    mockkObject(AppointmentRepositoryProvider.Companion)
    every { AppointmentRepositoryProvider.default } returns
        AppointmentRepositoryProvider { FakeAppointmentRepository() }
    mockkObject(ProfileRepositoryProvider.Companion)
    every { ProfileRepositoryProvider.default } returns ProfileRepositoryProvider { mockk() }
    mockkObject(ActiveCircleRepositoryProvider.Companion)
    every { ActiveCircleRepositoryProvider.default } returns
        ActiveCircleRepositoryProvider { mockk() }
    mockkObject(AppointmentReminderProvider.Companion)
    every { AppointmentReminderProvider.default } returns AppointmentReminderProvider { mockk() }
    val before = System.currentTimeMillis()

    AppointmentReminderCheckProvider.firebaseCheck().run()

    // signed out, it comes back in 6 hours
    // FirebaseApp is set up once per test run, so its context can be the app of an earlier test:
    // the code uses that context, and so does this test
    val appContext = Firebase.app.applicationContext
    val at =
        shadowOf(appContext.getSystemService(AlarmManager::class.java))
            .scheduledAlarms
            .single()
            .triggerAtMs
    val recheck = AppointmentReminderCheck.RECHECK_MILLIS
    assertTrue(at in before + recheck..System.currentTimeMillis() + recheck)
  }
}
