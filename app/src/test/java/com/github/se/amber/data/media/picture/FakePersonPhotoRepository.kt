// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import com.github.se.amber.model.circle.CareCircle
import java.io.File

/**
 * An in-memory [PersonPhotoRepository] for unit tests. No Firebase.
 *
 * [calls] records every call, in order: "setPhoto {circleId}:{personId}" or "getPhotoFile
 * {circleId}:{storagePath}". A photo passed to [setPhoto] is kept under the path it records in
 * [photos] and [files], and [getPhotoFile] returns that file. A later [setPhoto] for the same
 * person drops the previous path, because the contract deletes the previous photo. Set [error] to
 * make every call return that failure and change nothing.
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

  private var nextPhotoId = 0

  override suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit> {
    calls += "setPhoto ${circle.id}:$personId"
    error?.let {
      return Result.failure(it)
    }
    val people = photos.getOrPut(circle.id) { mutableMapOf() }
    val previous = people[personId]
    val storagePath = "careCircles/${circle.id}/people/$personId/${++nextPhotoId}.jpg"
    people[personId] = storagePath
    if (previous != null) files.remove(previous)
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
}
