// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.consent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The version of the Terms of Service and Privacy Policy. Raise it when they change, so everyone is
 * asked to accept them again.
 */
const val TERMS_VERSION = 1

/** Remembers on this phone whether the user accepted the Terms of Service and Privacy Policy. */
interface TermsConsentRepository {
  /** Whether the current version of the terms was accepted on this phone. */
  val hasAccepted: Flow<Boolean>

  /** Remembers that the current version of the terms was accepted. */
  suspend fun accept()
}

/**
 * [TermsConsentRepository] in Preferences DataStore: the version last accepted. A read that fails
 * counts as not accepted, so the user is asked again rather than never.
 */
class DataStoreTermsConsentRepository(
    private val dataStore: DataStore<Preferences>,
) : TermsConsentRepository {

  override val hasAccepted: Flow<Boolean> =
      dataStore.data
          .catch { emit(emptyPreferences()) }
          .map { preferences -> (preferences[ACCEPTED_VERSION] ?: 0) >= TERMS_VERSION }
          .distinctUntilChanged()

  override suspend fun accept() {
    dataStore.edit { preferences -> preferences[ACCEPTED_VERSION] = TERMS_VERSION }
  }

  private companion object {
    val ACCEPTED_VERSION = intPreferencesKey("accepted_terms_version")
  }
}
