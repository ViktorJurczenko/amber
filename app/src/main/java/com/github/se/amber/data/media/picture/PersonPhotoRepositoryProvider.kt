// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorageFirebase
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.firestore.firestore
import java.io.File

/**
 * Holds a single [PersonPhotoRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. With the default factory, the default Firebase app
 * must be initialized before [repository] is first accessed; if building fails, the next access
 * tries again.
 */
class PersonPhotoRepositoryProvider(
    factory: () -> PersonPhotoRepository = {
      val context = Firebase.app.applicationContext
      PersonPhotoRepositoryFirebase(
          fileStorage = MediaFileStorageFirebase(),
          cache = MediaFileCache(File(context.cacheDir, "people")),
          firestore = Firebase.firestore,
          authRepository = AuthRepositoryProvider.repository,
      )
    },
) {
  val repository: PersonPhotoRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = PersonPhotoRepositoryProvider()
  }
}
