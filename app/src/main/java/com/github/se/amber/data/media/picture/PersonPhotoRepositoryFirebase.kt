// Written by Alaabrine, with assistance from Cursor Agent.
// Modified by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.util.Log
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorage
import com.github.se.amber.data.media.PHOTO_CONTENT_TYPE
import com.github.se.amber.data.media.causedByConnection
import com.github.se.amber.data.media.requirePersonPhotoOf
import com.github.se.amber.data.media.requirePhotoUploadable
import com.github.se.amber.data.runEveryStep
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.circle.hasStorageBucket
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * [PersonPhotoRepository] backed by Firebase Storage and Firestore.
 *
 * A photo file lives in the circle's bucket. Its path is one entry of `personPhotos` on the circle
 * document: [setPhoto] uploads first, then writes that entry, then deletes the previous file. The
 * entry therefore never points at a missing file. The write changes only that entry, in a
 * transaction, so a concurrent edit of another person is kept and an offline phone fails instead of
 * queuing the write. If the write fails, the new file is deleted and the old entry stays.
 *
 * Each photo is uploaded to a new path, so changing a photo is always a first upload. The rules
 * only refuse a second upload to a path that already has a file. That refusal is the Storage
 * client's own retry of an upload whose reply was lost after the file landed: the path is new, so
 * nothing else uploads it twice. When that happens and the file is there, the first upload did land
 * and [setPhoto] continues. The pending photo is [pendingPhoto], a file chosen by the caller.
 *
 * @param fileStorage uploads, downloads and deletes photo files in the circle's bucket.
 * @param cache local copies of those files. A file it already has is returned without a download.
 * @param firestore the circle documents, where `personPhotos` is stored.
 * @param authRepository the signed-in user, read when a call needs one.
 * @param pendingPhoto where [savePendingPhoto] keeps a JPEG until [uploadPendingPhoto].
 * @param ioDispatcher where the JPEG is prepared and temporary files are created. Tests pass their
 *   own.
 */
class PersonPhotoRepositoryFirebase(
    private val fileStorage: MediaFileStorage,
    private val cache: MediaFileCache,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val pendingPhoto: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PersonPhotoRepository {

  override suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit> {
    val uid =
        authRepository.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.failure(PersonPhotoError.NotAuthenticated)
    if (!circle.hasStorageBucket) return Result.failure(PersonPhotoError.NoStorageBucket)

    return try {
      val photoId = newPhotoId()
      // A name only. writePreparedJpeg creates the file by renaming its own temp file onto it, so
      // this call does not also leave an empty file behind.
      val prepared =
          withContext(ioDispatcher) {
            val destination = File(TEMP_DIR, "$PREPARED_PREFIX$photoId$JPEG_SUFFIX")
            writePreparedJpeg(file, destination)
          }
      try {
        storePreparedPhoto(circle, personId, uid, photoId, prepared)
      } finally {
        // cache.put moves this file into the cache. What is left is an unused temporary file.
        deleteQuietly(prepared)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e.toPersonPhotoError())
    }
  }

  override suspend fun getPhotoFile(circle: CareCircle, storagePath: String): Result<File> {
    if (!circle.hasStorageBucket) return Result.failure(PersonPhotoError.NoStorageBucket)
    // getFile downloads only on a miss, and a cached file is returned offline.
    return try {
      requirePersonPhotoOf(circle.id, storagePath)
      Result.success(
          cache.getFile(storagePath) { destination ->
            fileStorage.download(circle, storagePath, destination)
          }
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e.toPersonPhotoError())
    }
  }

  override suspend fun savePendingPhoto(file: File): Result<Unit> = photoCall {
    withContext(ioDispatcher) {
      writePreparedJpeg(file, pendingPhoto)
      Unit
    }
  }

  override suspend fun uploadPendingPhoto(circle: CareCircle): Result<Unit> {
    if (!pendingPhoto.isFile) return Result.success(Unit)
    if (!circle.hasStorageBucket) return Result.success(Unit)
    val uid =
        authRepository.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.failure(PersonPhotoError.NotAuthenticated)
    if (uid in circle.personPhotos) return Result.success(Unit)
    val uploaded = setPhoto(circle, uid, pendingPhoto)
    if (uploaded.isFailure) return uploaded
    return photoCall { deletePendingPhoto() }
  }

  override suspend fun clearLocalPhotos(): Result<Unit> = photoCall {
    runEveryStep({ cache.clear() }, { deletePendingPhoto() })
  }

  /**
   * Uploads [prepared], writes [personId]'s entry, then drops the previous file. Throws the failure
   * of a step that must abort the call. Cancellation is rethrown.
   */
  private suspend fun storePreparedPhoto(
      circle: CareCircle,
      personId: String,
      uid: String,
      photoId: String,
      prepared: File,
  ) {
    // The path comes from the same check the Storage rules apply. This class builds none of its
    // own.
    val storagePath = requirePhotoUploadable(circle, personId, photoId, prepared)
    uploadPhoto(circle, storagePath, prepared, uid)
    val previous =
        try {
          writePersonPhoto(circle.id, personId, storagePath)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // The entry was not written, so the new file would be left with nothing pointing at it.
          deleteIgnoringFailure(circle, storagePath)
          throw e
        }
    if (previous.isNotBlank() && previous != storagePath) {
      // The entry already names the new file, so failing to delete the previous one leaves it
      // behind.
      deleteIgnoringFailure(circle, previous)
    }
    try {
      cache.put(storagePath, prepared)
    } catch (e: IOException) {
      // The photo is stored. A later open downloads it.
      Log.w(TAG, "Couldn't keep the photo $storagePath in the cache", e)
    }
  }

  /**
   * Uploads [file] as [authorId]. A refusal is not a failure when [storagePath] already holds the
   * file: the rules reject every later upload of that path, including the Storage client's retry
   * after a lost reply.
   */
  private suspend fun uploadPhoto(
      circle: CareCircle,
      storagePath: String,
      file: File,
      authorId: String,
  ) {
    try {
      fileStorage.upload(circle, storagePath, file, PHOTO_CONTENT_TYPE, authorId)
    } catch (e: StorageException) {
      if (e.errorCode != StorageException.ERROR_NOT_AUTHORIZED) throw e
      // Completing the download means the refused upload had already stored the photo, so the
      // caller can write the entry. The downloaded copy is thrown away: the prepared file is the
      // one kept in the cache.
      val probe = withContext(ioDispatcher) { File.createTempFile(PROBE_PREFIX, PART_SUFFIX) }
      try {
        fileStorage.download(circle, storagePath, probe)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw e.refusedUpload()
      } finally {
        deleteQuietly(probe)
      }
    }
  }

  /**
   * Writes [storagePath] to `personPhotos.[personId]` and returns the path that entry had before
   * this write, or `""` when it had none.
   *
   * An `update` of that one field leaves the rest of the document as it is. In a transaction, so
   * the previous path is the one this write replaces, and so the call fails offline instead of
   * keeping the write on the phone.
   */
  private suspend fun writePersonPhoto(
      circleId: String,
      personId: String,
      storagePath: String,
  ): String {
    val document = firestore.collection(CARE_CIRCLES_COLLECTION).document(circleId)
    return firestore
        .runTransaction { transaction ->
          val previous = transaction.get(document).storedPersonPhoto(personId)
          transaction.update(document, FieldPath.of(PERSON_PHOTOS_FIELD, personId), storagePath)
          previous
        }
        .await()
  }

  /**
   * Deletes [storagePath] and its cached copy. A failure is logged: the call that asked for it
   * still has its result. Same order as deleting a media file.
   */
  private suspend fun deleteIgnoringFailure(circle: CareCircle, storagePath: String) {
    try {
      fileStorage.delete(circle, storagePath)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't delete the photo $storagePath", e)
      return
    }
    try {
      cache.evict(storagePath)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't drop the cached photo $storagePath", e)
    }
  }

  /**
   * Deletes [pendingPhoto]. A file that is already gone is the result this wants.
   *
   * @throws IOException if the file is still there afterwards.
   */
  private suspend fun deletePendingPhoto() {
    withContext(ioDispatcher) {
      if (!pendingPhoto.delete() && pendingPhoto.exists()) {
        throw IOException("Could not delete the pending photo ${pendingPhoto.path}")
      }
    }
  }

  /** A Firestore auto-id, generated on the device, with no network call. */
  private fun newPhotoId(): String = firestore.collection(CARE_CIRCLES_COLLECTION).document().id

  /** The path stored for [personId], or `""` when the entry is missing or not a string. */
  private fun DocumentSnapshot.storedPersonPhoto(personId: String): String {
    val photos = get(PERSON_PHOTOS_FIELD) as? Map<*, *> ?: return ""
    return photos[personId] as? String ?: ""
  }

  private fun deleteQuietly(file: File) {
    if (!file.delete() && file.exists()) Log.w(TAG, "Couldn't delete ${file.path}")
  }

  private suspend fun <T> photoCall(block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e.toPersonPhotoError())
      }

  private fun Exception.toPersonPhotoError(): PersonPhotoError =
      when (this) {
        is PersonPhotoError -> this
        is FirebaseFirestoreException -> toPhotoError()
        is StorageException -> toPhotoError()
        else ->
            if (causedByConnection()) PersonPhotoError.Network else PersonPhotoError.Unknown(this)
      }

  private fun FirebaseFirestoreException.toPhotoError(): PersonPhotoError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> PersonPhotoError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> PersonPhotoError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> PersonPhotoError.Network
        else -> PersonPhotoError.Unknown(this)
      }

  private fun StorageException.toPhotoError(): PersonPhotoError =
      when (errorCode) {
        StorageException.ERROR_NOT_AUTHORIZED -> PersonPhotoError.PermissionDenied
        StorageException.ERROR_NOT_AUTHENTICATED -> PersonPhotoError.NotAuthenticated
        else ->
            if (causedByConnection()) PersonPhotoError.Network else PersonPhotoError.Unknown(this)
      }

  /**
   * The upload was refused. A download that cannot reach storage can be retried. A missing file, or
   * another refusal, means the photo was not stored. A 401 is not authenticated, as on any other
   * call.
   */
  private fun Exception.refusedUpload(): PersonPhotoError =
      when {
        causedByConnection() -> PersonPhotoError.Network
        this is StorageException && errorCode in PROBE_NOT_STORED ->
            PersonPhotoError.PermissionDenied
        else -> toPersonPhotoError()
      }

  private companion object {
    const val TAG = "PersonPhotoRepository"
    const val CARE_CIRCLES_COLLECTION = "careCircles"
    const val PERSON_PHOTOS_FIELD = "personPhotos"
    const val PREPARED_PREFIX = "person-photo-"
    const val PROBE_PREFIX = "person-photo-probe-"
    const val JPEG_SUFFIX = ".jpg"
    const val PART_SUFFIX = ".part"
    val TEMP_DIR = File(requireNotNull(System.getProperty("java.io.tmpdir")))

    /** A probe that found no stored photo: the rules refused it, or the object is missing. */
    val PROBE_NOT_STORED =
        setOf(
            StorageException.ERROR_NOT_AUTHORIZED,
            StorageException.ERROR_OBJECT_NOT_FOUND,
        )
  }
}
