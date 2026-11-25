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
  @After
  fun tearDown() {
    unmockkStatic(FirebaseApp::class, FirebaseFirestore::class)
    unmockkObject(AuthRepositoryProvider)
  }

  @Test
  fun defaultIsSharedAndItsFactoryBuildsTheFirebaseRepository() {
    assertSame(PersonPhotoRepositoryProvider.default, PersonPhotoRepositoryProvider.default)

    val context = mockk<Context>()
    val app = mockk<FirebaseApp>()
    every { app.applicationContext } returns context
    every { context.cacheDir } returns File("people-cache")
    mockkStatic(FirebaseApp::class, FirebaseFirestore::class)
    every { FirebaseApp.getInstance() } returns app
    every { FirebaseFirestore.getInstance() } returns mockk()
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns mockk()

    val repository = PersonPhotoRepositoryProvider().repository

    assertEquals(PersonPhotoRepositoryFirebase::class.java, repository.javaClass)
  }
}
