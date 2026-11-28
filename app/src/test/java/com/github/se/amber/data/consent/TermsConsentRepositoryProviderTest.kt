// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.consent

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TermsConsentRepositoryProviderTest {

  @Test
  fun theRepository_isBuiltByTheFactory() {
    val fake = FakeTermsConsentRepository()

    assertSame(fake, TermsConsentRepositoryProvider { fake }.repository)
  }

  @Test
  fun termsAcceptedOnThisPhone_stayAccepted_whenTheRepositoryIsBuiltAgain() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    TermsConsentRepositoryProvider.dataStoreRepository(context).accept()

    assertTrue(TermsConsentRepositoryProvider.dataStoreRepository(context).hasAccepted.first())
  }

  @Test
  fun theDefaultRepository_keepsTheTermsInTheAppsStorage() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = mockk<FirebaseApp>()
    mockkStatic(FirebaseApp::class)
    every { FirebaseApp.getInstance() } returns app
    every { app.applicationContext } returns context
    try {
      TermsConsentRepositoryProvider().repository

      verify(exactly = 1) { app.applicationContext }
    } finally {
      unmockkStatic(FirebaseApp::class)
    }
  }
}
