// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.github.se.amber.data.appointment.reminder.AppointmentReminderPoster
import com.github.se.amber.data.appointment.reminder.DueReminder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks that [AppointmentReminderWorker] hands its reminder to the poster and never retries. */
@RunWith(RobolectricTestRunner::class)
class AppointmentReminderWorkerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val poster: AppointmentReminderPoster = mockk()
  private val reminder = DueReminder("c1", "a1", 1_800_000_000_000L, 60, "tag")

  private suspend fun runWorker(input: Data = AppointmentReminderWorker.inputData(reminder)) =
      TestListenableWorkerBuilder.from(context, AppointmentReminderWorker::class.java)
          .setWorkerFactory(AppointmentReminderWorkerFactory { poster })
          .setInputData(input)
          .build()
          .doWork()

  @Test
  fun postsTheReminderOfItsInput() = runTest {
    coEvery { poster.post(any()) } returns true

    assertEquals(Result.success(), runWorker())
    coVerify(exactly = 1) { poster.post(reminder) }
  }

  @Test
  fun aReminderNoLongerWantedStillSucceeds() = runTest {
    coEvery { poster.post(any()) } returns false

    assertEquals(Result.success(), runWorker())
  }

  @Test
  fun aFailedCheckSucceedsWithoutRetrying() = runTest {
    coEvery { poster.post(any()) } throws IllegalStateException("left the circle")

    assertEquals(Result.success(), runWorker())
  }

  @Test
  fun anIncompleteInputFailsWithoutPosting() = runTest {
    assertEquals(Result.failure(), runWorker(workDataOf("circleId" to "c1")))
    coVerify(exactly = 0) { poster.post(any()) }
  }
}
