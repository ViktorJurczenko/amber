// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.consent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.app

private val Context.termsConsentDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "terms_consent")

/**
 * Holds a single [TermsConsentRepository], built lazily by `factory` on first access.
 *
 * ViewModels receive the repository as a constructor parameter (their default reads it from
 * [default]), so tests can pass a fake instead. With the default factory, the default Firebase app
 * must be initialized before [repository] is first accessed.
 */
class TermsConsentRepositoryProvider(
    factory: () -> TermsConsentRepository = { dataStoreRepository() },
) {
  val repository: TermsConsentRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = TermsConsentRepositoryProvider()

    /** Builds the DataStore repository for [context]. Tests pass their own context. */
    internal fun dataStoreRepository(
        context: Context = Firebase.app.applicationContext,
    ): TermsConsentRepository = DataStoreTermsConsentRepository(context.termsConsentDataStore)
  }
}
