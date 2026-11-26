// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.content.Context
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PersonPhotoRepositoryProviderTest {
  private val fake: PersonPhotoRepository = mockk()

  @After
  fun tearDown() {
    unmockkStatic(FirebaseApp::class, FirebaseFirestore::class)
    unmockkObject(AuthRepositoryProvider)
  }

  @Test
  fun repositoryIsBuiltOnlyOnce() {
    var builds = 0
    val provider = PersonPhotoRepositoryProvider {
      builds++
      fake
    }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(1, builds)
  }

  @Test
  fun repositoryIsBuiltAgainAfterAFactoryFailure() {
    var attempts = 0
    val provider = PersonPhotoRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
  }

  @Test
  fun defaultFactoryBuildsTheFirebaseRepository() {
    val context = mockk<Context>()
    val app = mockk<FirebaseApp>()
    every { app.applicationContext } returns context
    every { context.cacheDir } returns File("people-cache")
    mockkStatic(FirebaseApp::class, FirebaseFirestore::class)
    every { FirebaseApp.getInstance() } returns app
    every { FirebaseFirestore.getInstance() } returns mockk()
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns mockk()

    val provider = PersonPhotoRepositoryProvider()

    assertEquals(PersonPhotoRepositoryFirebase::class.java, provider.repository.javaClass)
  }
}
