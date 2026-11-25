// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.util.Log
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorage
import com.github.se.amber.data.media.PHOTO_CONTENT_TYPE
import com.github.se.amber.data.media.requirePhotoUploadable
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Collection of care circles. An auto-id taken from it is generated on the device, with no network
 * call.
 */
private const val CARE_CIRCLES_COLLECTION = "careCircles"

/**
 * [PersonPhotoRepository] backed by Firebase Storage and Firestore.
 *
 * A photo file lives in the circle's bucket. Its path is one entry of `personPhotos` on the circle
 * document: [setPhoto] uploads first, then writes that entry, then deletes the previous file. The
 * entry therefore never points at a missing file. The write changes only that entry, in a
 * transaction, so a concurrent edit of another person is kept and an offline phone fails instead of
 * queuing the write. If the write fails, the new file is deleted and the old entry stays.
 *
 * The rules refuse a second upload to the same path. When an upload is refused and the file is
 * already there, the upload did land and [setPhoto] continues.
 *
 * @param fileStorage uploads, downloads and deletes photo files in the circle's bucket.
 * @param cache local copies of those files. A file it already has is returned without a download.
 * @param firestore the circle documents, where `personPhotos` is stored.
 * @param authRepository the signed-in user, read when a call needs one.
 * @param newPhotoId a new photo id. The default is a Firestore auto-id, generated on the device.
 */
class PersonPhotoRepositoryFirebase(
    private val fileStorage: MediaFileStorage,
    private val cache: MediaFileCache,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val newPhotoId: () -> String = {
      firestore.collection(CARE_CIRCLES_COLLECTION).document().id
    },
) : PersonPhotoRepository {

  override suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit> {
    val uid = signedInUid() ?: return Result.failure(PersonPhotoError.NotAuthenticated)
    if (circle.storageBucket.isBlank()) return Result.failure(PersonPhotoError.NoStorageBucket)

    // Created before the attempt so a failure while preparing it is still cleaned up below.
    val prepared =
        try {
          withContext(Dispatchers.IO) { File.createTempFile(PREPARED_PREFIX, JPEG_SUFFIX) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          return Result.failure(e.toPersonPhotoError())
        }
    return try {
      photoCall { storePreparedPhoto(circle, personId, uid, file, prepared) }
    } finally {
      // cache.put moves this file into the cache. What is left is an unused temporary file.
      deleteQuietly(prepared)
    }
  }

  override suspend fun getPhotoFile(circle: CareCircle, storagePath: String): Result<File> {
    if (circle.storageBucket.isBlank()) return Result.failure(PersonPhotoError.NoStorageBucket)
    // getFile downloads only on a miss, and a cached file is returned offline.
    return photoCall {
      cache.getFile(storagePath) { destination ->
        fileStorage.download(circle, storagePath, destination)
      }
    }
  }

  /**
   * Prepares [file], uploads it under a new id, writes [personId]'s entry, then drops the previous
   * file. Throws the failure of a step that must abort the call. Cancellation is rethrown.
   */
  private suspend fun storePreparedPhoto(
      circle: CareCircle,
      personId: String,
      uid: String,
      file: File,
      prepared: File,
  ) {
    prepareJpeg(file, prepared)
    val photoId = newPhotoId()
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
          deleteStoredPhoto(circle, storagePath)
          throw e
        }
    if (previous.isNotBlank() && previous != storagePath) {
      // The entry already names the new file, so failing to delete the previous one leaves it
      // behind.
      deleteStoredPhoto(circle, previous)
    }
    try {
      cache.put(storagePath, prepared)
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      // The photo is stored. A later open downloads it.
      Log.w(TAG, "Couldn't keep the photo $storagePath in the cache", e)
    }
  }

  /** Writes a prepared JPEG of [source] to [destination], replacing it. [source] is only read. */
  private suspend fun prepareJpeg(source: File, destination: File) {
    withContext(Dispatchers.IO) { writePreparedJpeg(source, destination) }
  }

  /**
   * Uploads [file] as [authorId]. A refusal is not a failure when [storagePath] already holds the
   * file: the rules reject every later upload of that path, including one whose reply was lost.
   */
  private suspend fun uploadPhoto(
      circle: CareCircle,
      storagePath: String,
      file: File,
      authorId: String,
  ) {
    try {
      fileStorage.upload(circle, storagePath, file, PHOTO_CONTENT_TYPE, authorId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: StorageException) {
      if (e.errorCode != StorageException.ERROR_NOT_AUTHORIZED) throw e
      confirmStoredPhoto(circle, storagePath)
    }
  }

  /**
   * Downloads [storagePath]. Completing means the refused upload had already stored the photo, so
   * the caller can write the entry. The downloaded copy is thrown away: the prepared file is the
   * one kept in the cache.
   */
  private suspend fun confirmStoredPhoto(circle: CareCircle, storagePath: String) {
    val probe = File.createTempFile(PROBE_PREFIX, PART_SUFFIX)
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
    val document = circleDocument(circleId)
    return firestore
        .runTransaction { transaction ->
          val previous = transaction.get(document).storedPersonPhoto(personId)
          transaction.update(document, FieldPath.of(PERSON_PHOTOS_FIELD, personId), storagePath)
          previous
        }
        .await()
  }

  /**
   * Deletes [storagePath]. A failure is logged: the call that asked for it still has its result.
   */
  private suspend fun deleteStoredPhoto(circle: CareCircle, storagePath: String) {
    try {
      fileStorage.delete(circle, storagePath)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't delete the photo $storagePath", e)
    }
  }

  private fun circleDocument(circleId: String): DocumentReference =
      firestore.collection(CARE_CIRCLES_COLLECTION).document(circleId)

  /** The signed-in user's uid, or null when nobody is signed in. A blank uid counts as nobody. */
  private fun signedInUid(): String? = authRepository.currentUser?.uid?.takeIf { it.isNotBlank() }

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
            if (isConnectionFailure()) PersonPhotoError.Network else PersonPhotoError.Unknown(this)
      }

  private fun FirebaseFirestoreException.toPhotoError(): PersonPhotoError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> PersonPhotoError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> PersonPhotoError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED -> PersonPhotoError.Network
        else -> PersonPhotoError.Unknown(this)
      }

  private fun StorageException.toPhotoError(): PersonPhotoError =
      when (errorCode) {
        StorageException.ERROR_NOT_AUTHORIZED -> PersonPhotoError.PermissionDenied
        StorageException.ERROR_NOT_AUTHENTICATED -> PersonPhotoError.NotAuthenticated
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> PersonPhotoError.Network
        else ->
            if (isConnectionFailure()) PersonPhotoError.Network else PersonPhotoError.Unknown(this)
      }

  /**
   * The upload was refused. A download that cannot reach storage can be retried. A missing file, or
   * another refusal, means the photo was not stored.
   */
  private fun Exception.refusedUpload(): PersonPhotoError =
      when {
        isConnectionFailure() -> PersonPhotoError.Network
        this is StorageException &&
            errorCode in
                setOf(
                    StorageException.ERROR_NOT_AUTHORIZED,
                    StorageException.ERROR_NOT_AUTHENTICATED,
                    StorageException.ERROR_OBJECT_NOT_FOUND,
                ) -> PersonPhotoError.PermissionDenied
        else -> toPersonPhotoError()
      }

  /**
   * Whether this error, or one of its causes, is a connection that a later attempt can get past.
   */
  private fun Throwable.isConnectionFailure(): Boolean =
      generateSequence(this) { it.cause }
          .any { error ->
            when (error) {
              // Firebase Storage gave up retrying a request that got no HTTP answer.
              is StorageException -> error.isRecoverableException
              is UnknownHostException,
              is SocketException,
              is SocketTimeoutException -> true
              else -> false
            }
          }

  private companion object {
    const val TAG = "PersonPhotoRepository"
    const val PERSON_PHOTOS_FIELD = "personPhotos"
    const val PREPARED_PREFIX = "person-photo-"
    const val PROBE_PREFIX = "person-photo-probe-"
    const val JPEG_SUFFIX = ".jpg"
    const val PART_SUFFIX = ".part"
  }
}
