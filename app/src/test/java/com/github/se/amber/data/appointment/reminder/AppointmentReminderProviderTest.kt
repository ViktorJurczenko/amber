// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import androidx.work.testing.TestListenableWorkerBuilder
import com.github.se.amber.data.work.AppointmentReminderWorker
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Checks how [AppointmentReminderProvider] builds its poster, on Robolectric, where Firebase is
 * initialized by hand like the app does at startup. Nothing here contacts Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class AppointmentReminderProviderTest {
  private val context: Context = RuntimeEnvironment.getApplication()
  private val poster: AppointmentReminderPoster = mockk()

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
  fun theDefaultPosterIsBuiltFromTheAppRepositoriesAndNeedsASignedInUser() = runTest {
    // a fake, so the app-wide auth repository is never built: AuthRepositoryProviderTest checks
    // how it is built the first time, and it is built only once per test run
    val signedOut: AuthRepository = mockk { every { currentUser } returns null }
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns signedOut
    try {
      val provider = AppointmentReminderProvider()

      // nobody is signed in, so it stops before reading any appointment
      assertFalse(provider.poster.post(DueReminder("c1", "a1", 1_800_000_000_000L, 60, "tag")))
      assertSame(provider.poster, provider.poster)
      verify { signedOut.currentUser }
    } finally {
      unmockkObject(AuthRepositoryProvider)
    }
  }

  @Test
  fun posterIsBuiltOnlyOnce() {
    var builds = 0
    val provider = AppointmentReminderProvider {
      builds++
      poster
    }

    assertSame(poster, provider.poster)
    assertSame(poster, provider.poster)
    assertEquals(1, builds)
  }

  @Test
  fun posterIsBuiltAgainAfterAFactoryFailure() {
    var attempts = 0
    val provider = AppointmentReminderProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      poster
    }

    runCatching { provider.poster }

    assertSame(poster, provider.poster)
  }

  @Test
  fun theWorkerFactoryBuildsThePosterOnlyWhenAJobRuns() = runTest {
    coEvery { poster.post(any()) } returns true
    var builds = 0
    val provider = AppointmentReminderProvider {
      builds++
      poster
    }
    val reminder = DueReminder("c1", "a1", 1_800_000_000_000L, 60, "tag")

    val job =
        TestListenableWorkerBuilder.from(context, AppointmentReminderWorker::class.java)
            .setWorkerFactory(provider.workerFactory)
            .setInputData(AppointmentReminderWorker.inputData(reminder))
            .build()
    assertEquals(0, builds)

    job.doWork()
    assertEquals(1, builds)
    coVerify { poster.post(reminder) }
  }
}
