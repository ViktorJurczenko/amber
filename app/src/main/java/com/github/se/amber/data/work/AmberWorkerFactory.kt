// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.work

import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkerFactory
import com.github.se.amber.model.media.MediaRepositoryProvider

/**
 * Creates every background job of the app. The app gives it to WorkManager at startup.
 *
 * WorkManager can only pass a worker its context and its parameters. Each feature has its own
 * factory that gives its workers what they need. This one asks each of them in turn, and the first
 * one that knows the worker creates it.
 *
 * Every new worker must be added here, through its factory. Otherwise WorkManager can't create it
 * with what it needs.
 *
 * @param factories the factories of each feature. By default, the media one,
 *   [MediaRepositoryProvider.workerFactory]. Tests can pass their own.
 */
class AmberWorkerFactory(
    factories: List<WorkerFactory> = listOf(MediaRepositoryProvider.workerFactory)
) : DelegatingWorkerFactory() {
  init {
    factories.forEach(::addFactory)
  }
}
