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
 * and [files], and [getPhotoFile] returns that file. [savePendingPhoto] keeps its file in
 * [pendingPhoto] until [uploadPendingPhoto] or [clearLocalPhotos]. Set [error] to make every call
 * return that failure and change nothing.
 */
class FakePersonPhotoRepository : PersonPhotoRepository {

  /** Every call, in order. */
  val calls = mutableListOf<String>()

  /** When set, every call returns this failure and stores nothing. */
  var error: PersonPhotoError? = null

  /** Circle id to the person ids and the storage paths written by [setPhoto]. */
  val photos = mutableMapOf<String, MutableMap<String, String>>()

  /** Storage path to the file [setPhoto] stored, or one a test put here for [getPhotoFile]. */
  val files = mutableMapOf<String, File>()

  /** The file from the last successful [savePendingPhoto], or null when there is none. */
  var pendingPhoto: File? = null

  private var nextPhotoId = 0

  override suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit> {
    calls += "setPhoto ${circle.id}:$personId"
    error?.let {
      return Result.failure(it)
    }
    val storagePath = "careCircles/${circle.id}/people/$personId/${++nextPhotoId}.jpg"
    photos.getOrPut(circle.id) { mutableMapOf() }[personId] = storagePath
    files[storagePath] = file
    return Result.success(Unit)
  }

  override suspend fun getPhotoFile(circle: CareCircle, storagePath: String): Result<File> {
    calls += "getPhotoFile ${circle.id}:$storagePath"
    error?.let {
      return Result.failure(it)
    }
    val file = files[storagePath]
    return if (file == null) {
      Result.failure(PersonPhotoError.Unknown(IllegalStateException("No photo at $storagePath")))
    } else {
      Result.success(file)
    }
  }

  override suspend fun savePendingPhoto(file: File): Result<Unit> {
    calls += "savePendingPhoto"
    error?.let {
      return Result.failure(it)
    }
    pendingPhoto = file
    return Result.success(Unit)
  }

  override suspend fun uploadPendingPhoto(circle: CareCircle): Result<Unit> {
    calls += "uploadPendingPhoto ${circle.id}"
    error?.let {
      return Result.failure(it)
    }
    pendingPhoto = null
    return Result.success(Unit)
  }

  override suspend fun clearLocalPhotos(): Result<Unit> {
    calls += "clearLocalPhotos"
    error?.let {
      return Result.failure(it)
    }
    photos.clear()
    files.clear()
    pendingPhoto = null
    return Result.success(Unit)
  }
}
