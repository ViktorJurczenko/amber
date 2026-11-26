// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.circle

/**
 * Holds a single [SavedPlacesRepository], built lazily by `factory` on first access, like
 * [CareCircleRepositoryProvider].
 *
 * There must be one repository for the app: its [SavedPlacesRepository.writeFailures] only reports
 * the writes made through it. So the app writes places and collects their failures through
 * [default] only.
 */
class SavedPlacesRepositoryProvider(
    factory: () -> SavedPlacesRepository = { FirebaseCareCircleRepository() },
) {
  val repository: SavedPlacesRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = SavedPlacesRepositoryProvider()
  }
}
