// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.consent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreTermsConsentRepositoryTest {

  @Test
  fun theTerms_areNotAcceptedAtFirst_thenStayAcceptedOnThisPhone() = runTest {
    val store = newDataStore()
    val repository = DataStoreTermsConsentRepository(store)
    assertFalse(repository.hasAccepted.first())

    repository.accept()

    assertTrue(repository.hasAccepted.first())
    assertTrue(DataStoreTermsConsentRepository(store).hasAccepted.first())
  }

  @Test
  fun anOlderVersionOfTheTerms_isAskedAgain() = runTest {
    val store = newDataStore()
    store.edit { it[intPreferencesKey("accepted_terms_version")] = TERMS_VERSION - 1 }

    assertFalse(DataStoreTermsConsentRepository(store).hasAccepted.first())
  }

  @Test
  fun aStoreThatCantBeRead_asksAgainRatherThanNever() = runTest {
    val broken =
        object : DataStore<Preferences> {
          override val data: Flow<Preferences> = flow { throw IOException("disk") }

          override suspend fun updateData(
              transform: suspend (t: Preferences) -> Preferences
          ): Preferences = error("unused")
        }

    assertFalse(DataStoreTermsConsentRepository(broken).hasAccepted.first())
  }

  private fun TestScope.newDataStore(): DataStore<Preferences> {
    val directory = createTempDirectory(prefix = "terms-consent")
    return PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { directory.resolve("terms_consent.preferences_pb").toFile() },
    )
  }
}
