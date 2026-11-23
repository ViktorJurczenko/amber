// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks that the app's worker factory creates each known worker and nothing else. */
@RunWith(RobolectricTestRunner::class)
class AmberWorkerFactoryTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val params: WorkerParameters = mockk(relaxed = true)

  /** A factory that creates [worker] for [name] only, like the factory of each feature. */
  private fun factoryFor(name: String, worker: ListenableWorker) =
      object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = if (workerClassName == name) worker else null
      }

  @Test
  fun stillCreatesTheMediaSendWorker() {
    val worker =
        AmberWorkerFactory().createWorker(context, MediaSendWorker::class.java.name, params)

    // created without its sender: the repository is built only when the worker runs
    assertEquals(MediaSendWorker::class, worker!!::class)
  }

  @Test
  fun createsTheAppointmentReminderWorker() {
    val worker =
        AmberWorkerFactory()
            .createWorker(context, AppointmentReminderWorker::class.java.name, params)

    assertEquals(AppointmentReminderWorker::class, worker!!::class)
  }

  @Test
  fun givesNullForAnUnknownWorker() {
    assertNull(AmberWorkerFactory().createWorker(context, "com.example.UnknownWorker", params))
  }

  @Test
  fun asksEachFactoryUntilOneKnowsTheWorker() {
    val first: ListenableWorker = mockk()
    val second: ListenableWorker = mockk()
    val factory =
        AmberWorkerFactory(listOf(factoryFor("First", first), factoryFor("Second", second)))

    assertSame(first, factory.createWorker(context, "First", params))
    assertSame(second, factory.createWorker(context, "Second", params))
    assertNull(factory.createWorker(context, "Third", params))
  }
}
