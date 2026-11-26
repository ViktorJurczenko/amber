// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks that the app's worker factory asks each feature's factory for a worker, and gives null for
 * a worker none of them knows. That the app's own factory creates the media worker is checked in
 * `AmberApplicationTest`.
 */
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
