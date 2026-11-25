// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.PlannedReminder
import com.github.se.amber.model.authentication.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.mockk.every
import io.mockk.mockk
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
  fun theDefaultFactoryBuildsAPosterThatNeedsASignedInUser() = runTest {
    // a fake: the app-wide auth repository is left for AuthRepositoryProviderTest to build
    val signedOut: AuthRepository = mockk { every { currentUser } returns null }
    val poster = AppointmentReminderProvider.firebasePoster(authRepository = signedOut)

    // nobody is signed in, so it shows nothing and reads no circle
    assertFalse(poster.post("c1", PlannedReminder(Appointment(id = "a1"), 60, 0L)))
    verify { signedOut.currentUser }
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
}
