// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppointmentReminderProviderTest {
  private val poster: AppointmentReminderPoster = mockk()

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
  fun buildingTheWorkerFactoryDoesNotBuildThePoster() {
    var builds = 0
    val provider = AppointmentReminderProvider {
      builds++
      poster
    }

    provider.workerFactory

    assertEquals(0, builds)
  }
}
