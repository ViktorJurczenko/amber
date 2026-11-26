// Written by Alaabrine, with assistance from Cursor Agent.
// Modified by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import com.github.se.amber.model.circle.CareCircle
import java.io.File

/** Stores a person's profile photo, including one picked before the circle has a bucket. */
interface PersonPhotoRepository {

  /**
   * Prepares [file] as a JPEG, uploads it as [personId]'s photo in [circle], writes that entry, and
   * then deletes the previous photo.
   *
   * The entry is written only after the upload. If that write fails, the new file is deleted. Each
   * photo is uploaded to a new path, so changing one is a first upload. A permission denial is
   * success when that path already holds the file: the Storage client retried an upload that had
   * already landed. A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit>

  /**
   * Returns the cached photo at [storagePath], downloading it when it is not cached yet. Only the
   * image loader calls this. A file already cached is returned offline.
   *
   * A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun getPhotoFile(circle: CareCircle, storagePath: String): Result<File>

  /**
   * Keeps a prepared JPEG of [file] until [uploadPendingPhoto]. Captured pictures are deleted, so
   * this has to be a copy.
   *
   * A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun savePendingPhoto(file: File): Result<Unit>

  /**
   * Uploads the pending photo into [circle] and then deletes it. Does nothing when there is no
   * pending photo, [circle] has no bucket, or the signed-in user already has a photo there.
   *
   * A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun uploadPendingPhoto(circle: CareCircle): Result<Unit>

  /**
   * Deletes the cached photos and the pending photo. Nothing stored in a bucket is deleted.
   *
   * A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun clearLocalPhotos(): Result<Unit>
}

/** Typed failures returned by [PersonPhotoRepository]. */
sealed class PersonPhotoError(message: String) : Exception(message) {
  data object NotAuthenticated : PersonPhotoError("No user is signed in")

  data object NoStorageBucket : PersonPhotoError("The care circle has no storage bucket")

  data object CannotPreparePhoto : PersonPhotoError("The photo cannot be prepared")

  data object PermissionDenied : PersonPhotoError("You do not have permission to access this photo")

  data object Network : PersonPhotoError("Unable to reach photo storage")

  data class Unknown(val source: Throwable) : PersonPhotoError("The photo operation failed")
}
