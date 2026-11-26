// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import com.github.se.amber.model.circle.CareCircle
import java.io.File

/**
 * An in-memory [PersonPhotoRepository] for unit tests. No Firebase.
 *
 * [calls] records every call, in order: "setPhoto {circleId}:{personId}", "getPhotoFile
 * {circleId}:{storagePath}", "savePendingPhoto", "uploadPendingPhoto {circleId}", or
 * "clearLocalPhotos". A photo passed to [setPhoto] is kept under the path it records in [photos]
 * and [files], and [getPhotoFile] returns that file. A later [setPhoto] for the same person drops
 * the previous path, because the contract deletes the previous photo. [savePendingPhoto] keeps its
 * file in [pendingPhoto] until [uploadPendingPhoto] or [clearLocalPhotos]. Set [error] to make
 * every call fail with it and change nothing: [clearLocalPhotos] throws it, and the others return
 * it.
 */
class FakePersonPhotoRepository : PersonPhotoRepository {

  /** Every call, in order. */
  val calls = mutableListOf<String>()

  /** When set, every call fails with this and stores nothing. */
  var error: PersonPhotoError? = null

  /** Circle id to the person ids and the storage paths written by [setPhoto]. */
  val photos = mutableMapOf<String, MutableMap<String, String>>()

  /** Storage path to the file [setPhoto] stored, or one a test put here for [getPhotoFile]. */
  val files = mutableMapOf<String, File>()

  /** The file from the last successful [savePendingPhoto], or null when there is none. */
  var pendingPhoto: File? = null

  private var nextPhotoId = 0

  override suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit> =
      call("setPhoto ${circle.id}:$personId") {
        val people = photos.getOrPut(circle.id) { mutableMapOf() }
        val previous = people[personId]
        val storagePath = "careCircles/${circle.id}/people/$personId/${++nextPhotoId}.jpg"
        people[personId] = storagePath
        if (previous != null) files.remove(previous)
        files[storagePath] = file
      }

  override suspend fun getPhotoFile(circle: CareCircle, storagePath: String): Result<File> =
      call("getPhotoFile ${circle.id}:$storagePath") {
        files[storagePath]
            ?: throw PersonPhotoError.Unknown(IllegalStateException("No photo at $storagePath"))
      }

  override suspend fun savePendingPhoto(file: File): Result<Unit> =
      call("savePendingPhoto") { pendingPhoto = file }

  override suspend fun uploadPendingPhoto(circle: CareCircle): Result<Unit> =
      call("uploadPendingPhoto ${circle.id}") { pendingPhoto = null }

  override suspend fun clearLocalPhotos() {
    record("clearLocalPhotos")
    photos.clear()
    files.clear()
    pendingPhoto = null
  }

  /** Records [name], then runs [body]. [error] fails the call before [body] changes anything. */
  private inline fun <T> call(name: String, body: () -> T): Result<T> =
      try {
        record(name)
        Result.success(body())
      } catch (failure: PersonPhotoError) {
        Result.failure(failure)
      }

  private fun record(name: String) {
    calls += name
    error?.let { throw it }
  }
}
