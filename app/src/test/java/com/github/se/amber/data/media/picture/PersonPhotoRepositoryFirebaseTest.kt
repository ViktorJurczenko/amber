// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.data.media.FakeMediaFileStorage
import com.github.se.amber.data.media.MAX_PHOTO_BYTES
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorage
import com.github.se.amber.data.media.PHOTO_CONTENT_TYPE
import com.github.se.amber.data.media.requirePhotoUploadable
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.google.firebase.storage.StorageException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * [PersonPhotoRepositoryFirebase] with Firestore mocked, files in a [FakeMediaFileStorage], and a
 * real [MediaFileCache] in a temporary folder. Robolectric decodes the JPEGs.
 */
@OptIn(ExperimentalCoroutinesApi::class) // StandardTestDispatcher
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonPhotoRepositoryFirebaseTest {

  @get:Rule val tmp = TemporaryFolder()

  /**
   * What reached storage or Firestore, in order. Uploads and downloads are recorded by
   * [fileStorage]; "write {path}" is the personPhotos field write.
   */
  private val events = mutableListOf<String>()

  private val fileStorage = PhotoStorage(FakeMediaFileStorage(events))
  private val firestore = mockk<FirebaseFirestore>()
  private val circles = mockk<CollectionReference>()
  private val circleDocument = mockk<DocumentReference>()
  private val transaction = mockk<Transaction>()
  private val writtenField = slot<FieldPath>()
  private val writtenPath = slot<String>()
  private val snapshot = mockk<DocumentSnapshot>()

  private val photos = mutableMapOf<String, String>()
  private val writes = mutableListOf<Pair<FieldPath, String>>()
  private var duringWrite: () -> Unit = {}

  private val cacheDir by lazy { File(tmp.root, "people") }
  private val pendingPhoto by lazy { File(tmp.root, "pending-person-photo.jpg") }
  private val circle = CareCircle(id = CIRCLE_ID, storageBucket = BUCKET)

  @Before
  fun setUp() {
    events.clear()
    photos.clear()
    writes.clear()
    duringWrite = {}
    fileStorage.downloadError = null
    fileStorage.deleteError = null
    fileStorage.onUpload = {}
    every { firestore.collection(CARE_CIRCLES) } returns circles
    every { circles.document(CIRCLE_ID) } returns circleDocument
    every { firestore.runTransaction<Any?>(any()) } answers
        {
          try {
            Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
          } catch (e: Exception) {
            Tasks.forException(e)
          }
        }
    every { snapshot.get(any<String>()) } answers
        {
          if (firstArg<String>() == "personPhotos") photos.toMap() else null
        }
    every { transaction.get(circleDocument) } returns snapshot
    every {
      transaction.update(circleDocument, capture(writtenField), capture(writtenPath))
    } answers
        {
          recordWrite(writtenField.captured, writtenPath.captured)
          duringWrite()
          transaction
        }
  }

  @Test
  fun setPhotoAppliesExifBeforeScalingAndLeavesTheSourceUnchanged() = runTest {
    // 1200x600 with a quarter-turn becomes 600x1200, then the long side is scaled to 1024.
    val source = wideTurnedPicture()
    val sourceBytes = source.readBytes()
    val raw = BitmapFactory.decodeFile(source.absolutePath)
    assertEquals(WIDE, raw.width)
    assertEquals(TALL, raw.height)
    raw.recycle()
    var prepared = ByteArray(0)
    fileStorage.onUpload = { prepared = it.readBytes() }

    val result = repository().setPhoto(circle, PERSON, source)

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertArrayEquals(sourceBytes, source.readBytes())
    val fitted = decode(prepared)
    try {
      assertEquals(512, fitted.width)
      assertEquals(MAX_SIDE, fitted.height)
      assertTrue(fitted.width <= MAX_SIDE && fitted.height <= MAX_SIDE)
      assertTrue(prepared.size.toLong() in 1..MAX_PHOTO_BYTES)
      // A quarter turn moves the left (red) half to the top and the right (blue) half to the
      // bottom.
      assertEquals("red", colorAt(fitted, fitted.height / 8))
      assertEquals("blue", colorAt(fitted, fitted.height * 7 / 8))
    } finally {
      fitted.recycle()
    }
    val preparedFile = File(tmp.root, "prepared.jpg").apply { writeBytes(prepared) }
    assertEquals(0, ExifInterface(preparedFile).rotationDegrees)
  }

  @Test
  fun setPhotoUploadsThenWritesTheEntryThenDeletesTheOldPath() = runTest {
    photos[PERSON] = OLD_PATH
    var pathFromRules = ""
    fileStorage.onUpload = { file ->
      pathFromRules = requirePhotoUploadable(circle, PERSON, PHOTO_ID, file)
    }

    val result = repository().setPhoto(circle, PERSON, picture())

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals(
        listOf(
            "upload $BUCKET:$pathFromRules",
            "write $pathFromRules",
            "delete $CIRCLE_ID:$OLD_PATH",
        ),
        events,
    )
    assertEquals(listOf(FieldPath.of("personPhotos", PERSON) to pathFromRules), writes)
    assertEquals(pathFromRules, photos[PERSON])
    assertEquals(PHOTO_CONTENT_TYPE, fileStorage.lastUploadedContentType)
    assertEquals(UID, fileStorage.lastUploadedAuthorId)
  }

  @Test
  fun setPhotoDoesNotWriteOrDeleteWhenTheUploadFails() = runTest {
    val failure = IOException("offline")
    fileStorage.onUpload = { throw failure }

    val result = repository().setPhoto(circle, PERSON, picture())

    val error = result.exceptionOrNull()
    assertTrue(error is PersonPhotoError.Unknown)
    assertSame(failure, (error as PersonPhotoError.Unknown).source)
    assertEquals(emptyList<String>(), events)
    assertTrue(writes.isEmpty())
    assertTrue(photos.isEmpty())
  }

  @Test
  fun setPhotoDeletesTheNewObjectAndKeepsTheOldEntryWhenTheWriteFails() = runTest {
    photos[PERSON] = OLD_PATH
    var uploadedPath = ""
    fileStorage.onUpload = { file ->
      uploadedPath = requirePhotoUploadable(circle, PERSON, PHOTO_ID, file)
    }
    every { transaction.update(circleDocument, any<FieldPath>(), any()) } throws
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSame(PersonPhotoError.Network, result.exceptionOrNull())
    assertEquals(OLD_PATH, photos[PERSON])
    assertTrue(writes.isEmpty())
    assertEquals(
        listOf("upload $BUCKET:$uploadedPath", "delete $CIRCLE_ID:$uploadedPath"),
        events,
    )
  }

  @Test
  fun setPhotoWritesTheEntryWhenUploadIsRefusedButTheObjectExists() = runTest {
    val denied = storageError(403)
    var uploadedPath = ""
    fileStorage.onUpload = { file ->
      uploadedPath = requirePhotoUploadable(circle, PERSON, PHOTO_ID, file)
      events += "upload $BUCKET:$uploadedPath"
      throw denied
    }

    val result = repository().setPhoto(circle, PERSON, picture())

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals(
        listOf(
            "upload $BUCKET:$uploadedPath",
            "download $CIRCLE_ID:$uploadedPath",
            "write $uploadedPath",
        ),
        events,
    )
    assertEquals(listOf(FieldPath.of("personPhotos", PERSON) to uploadedPath), writes)
    assertEquals(uploadedPath, photos[PERSON])
  }

  @Test
  fun setPhotoWritesNothingWhenUploadIsRefusedAndTheObjectIsMissing() = runTest {
    photos[PERSON] = OLD_PATH
    fileStorage.downloadError = storageError(404)
    var uploadedPath = ""
    fileStorage.onUpload = { file ->
      uploadedPath = requirePhotoUploadable(circle, PERSON, PHOTO_ID, file)
      events += "upload $BUCKET:$uploadedPath"
      throw storageError(403)
    }

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSame(PersonPhotoError.PermissionDenied, result.exceptionOrNull())
    assertEquals(OLD_PATH, photos[PERSON])
    assertTrue(writes.isEmpty())
    assertEquals(
        listOf("upload $BUCKET:$uploadedPath", "download $CIRCLE_ID:$uploadedPath"),
        events,
    )
  }

  @Test
  fun setPhotoFailsBeforeUploadWhenSignedOutOrTheBucketIsBlank() = runTest {
    val source = picture()
    val sourceBytes = source.readBytes()

    val signedOut = repository(signedInAs(null)).setPhoto(circle, PERSON, source)
    val blankUid = repository(signedInAs("")).setPhoto(circle, PERSON, source)
    val noBucket = repository().setPhoto(circle.copy(storageBucket = ""), PERSON, source)

    assertSame(PersonPhotoError.NotAuthenticated, signedOut.exceptionOrNull())
    assertSame(PersonPhotoError.NotAuthenticated, blankUid.exceptionOrNull())
    assertSame(PersonPhotoError.NoStorageBucket, noBucket.exceptionOrNull())
    assertEquals(emptyList<String>(), events)
    assertTrue(writes.isEmpty())
    assertArrayEquals(sourceBytes, source.readBytes())
  }

  @Test
  fun getPhotoFileDownloadsOnceThenReturnsTheCachedFileWhenDownloadWouldFail() = runTest {
    val path = "careCircles/$CIRCLE_ID/people/$PERSON/cached.jpg"
    val repo = repository()

    val first = repo.getPhotoFile(circle, path)

    assertTrue(first.exceptionOrNull()?.toString(), first.isSuccess)
    assertEquals("content of $path", first.getOrThrow().readText())
    assertEquals(listOf("download $CIRCLE_ID:$path"), events)
    fileStorage.downloadError = IOException("offline")

    val second = repo.getPhotoFile(circle, path)

    assertTrue(second.exceptionOrNull()?.toString(), second.isSuccess)
    assertEquals(first.getOrThrow().absolutePath, second.getOrThrow().absolutePath)
    assertEquals("content of $path", second.getOrThrow().readText())
    assertEquals(listOf("download $CIRCLE_ID:$path"), events)
  }

  @Test
  fun getPhotoFileRejectsACircleWithNoBucket() = runTest {
    val result = repository().getPhotoFile(circle.copy(storageBucket = ""), OLD_PATH)

    assertSame(PersonPhotoError.NoStorageBucket, result.exceptionOrNull())
    assertEquals(emptyList<String>(), events)
  }

  @Test
  fun setPhotoDoesNotDeleteWhenThePreviousPathIsTheNewPath() = runTest {
    val path = "careCircles/$CIRCLE_ID/people/$PERSON/$PHOTO_ID.jpg"
    photos[PERSON] = path

    val result = repository().setPhoto(circle, PERSON, picture())

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals(listOf("upload $BUCKET:$path", "write $path"), events)
    assertEquals(path, photos[PERSON])
  }

  @Test
  fun setPhotoSucceedsWhenTheCacheCannotKeepThePhoto() = runTest {
    val blocked = File(tmp.root, "not-a-directory").apply { writeBytes(byteArrayOf(1)) }

    val result = repository(directory = blocked).setPhoto(circle, PERSON, picture())

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals(1, writes.size)
  }

  @Test
  fun setPhotoUsesAFirestoreIdWhenNoneIsGiven() = runTest {
    val generated = mockk<DocumentReference>()
    every { generated.id } returns PHOTO_ID
    every { circles.document() } returns generated
    val repo =
        PersonPhotoRepositoryFirebase(
            fileStorage,
            MediaFileCache(cacheDir, ioDispatcher = StandardTestDispatcher(testScheduler)),
            firestore,
            signedInAs(UID),
            pendingPhoto,
        )

    val result = repo.setPhoto(circle, PERSON, picture())

    val path = "careCircles/$CIRCLE_ID/people/$PERSON/$PHOTO_ID.jpg"
    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals(listOf("upload $BUCKET:$path", "write $path"), events)
  }

  @Test
  fun setPhotoTreatsANonStringPhotoEntryAsMissing() = runTest {
    var stored: Any? = "nope"
    every { snapshot.get(any<String>()) } answers
        {
          if (firstArg<String>() == "personPhotos") stored else null
        }
    val repo = repository()

    assertTrue(repo.setPhoto(circle, PERSON, picture()).isSuccess)
    assertTrue(events.none { it.startsWith("delete") })

    stored = mapOf(PERSON to 1)
    events.clear()

    assertTrue(repo.setPhoto(circle, PERSON, picture()).isSuccess)
    assertTrue(events.none { it.startsWith("delete") })
  }

  @Test
  fun setPhotoReturnsTheMappedStorageAndFirestoreErrors() = runTest {
    val repo = repository()
    val firestoreFailures =
        listOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED to PersonPhotoError.PermissionDenied,
            FirebaseFirestoreException.Code.UNAUTHENTICATED to PersonPhotoError.NotAuthenticated,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT to null,
        )
    for ((code, expected) in firestoreFailures) {
      val failure = FirebaseFirestoreException(code.name, code)
      every { firestore.runTransaction<Any?>(any()) } throws failure

      val result = repo.setPhoto(circle, PERSON, picture())

      if (expected == null) {
        val error = result.exceptionOrNull()
        assertTrue(error is PersonPhotoError.Unknown)
        assertSame(failure, (error as PersonPhotoError.Unknown).source)
      } else {
        assertSame(expected, result.exceptionOrNull())
      }
    }

    fileStorage.downloadError = storageError(403)
    assertSame(
        PersonPhotoError.PermissionDenied,
        repo.getPhotoFile(circle, OLD_PATH).exceptionOrNull(),
    )

    val storageFailures =
        listOf(
            storageError(401) to PersonPhotoError.NotAuthenticated,
            storageError(RETRY_LIMIT_HTTP) to PersonPhotoError.Network,
            storageError(500) to null,
        )
    for ((failure, expected) in storageFailures) {
      fileStorage.onUpload = { throw failure }
      val result = repo.setPhoto(circle, PERSON, picture())
      if (expected == null) {
        val error = result.exceptionOrNull()
        assertTrue(error is PersonPhotoError.Unknown)
        assertSame(failure, (error as PersonPhotoError.Unknown).source)
      } else {
        assertSame(expected, result.exceptionOrNull())
      }
    }
  }

  @Test
  fun setPhotoMapsConnectionFailuresToNetwork() = runTest {
    val repo = repository()
    val failures =
        listOf(
            UnknownHostException("down"),
            SocketException("reset"),
            SocketTimeoutException("slow"),
            requireNotNull(
                StorageException.fromExceptionAndHttpCode(UnknownHostException("down"), 500)
            ),
        )
    for (failure in failures) {
      fileStorage.onUpload = { throw failure }

      val result = repo.setPhoto(circle, PERSON, picture())

      assertSame(failure.toString(), PersonPhotoError.Network, result.exceptionOrNull())
    }
  }

  @Test
  fun aRefusedUploadWhoseProbeFailsForAnotherReasonIsThatFailure() = runTest {
    val missing = IOException("missing")
    fileStorage.downloadError = missing
    fileStorage.onUpload = { throw storageError(403) }
    val repo = repository()

    val other = repo.setPhoto(circle, PERSON, picture())

    val error = other.exceptionOrNull()
    assertTrue(error is PersonPhotoError.Unknown)
    assertSame(missing, (error as PersonPhotoError.Unknown).source)
    assertTrue(writes.isEmpty())

    fileStorage.downloadError = UnknownHostException("down")
    assertSame(PersonPhotoError.Network, repo.setPhoto(circle, PERSON, picture()).exceptionOrNull())

    fileStorage.downloadError = storageError(401)
    assertSame(
        PersonPhotoError.PermissionDenied,
        repo.setPhoto(circle, PERSON, picture()).exceptionOrNull(),
    )

    fileStorage.downloadError = storageError(403)
    assertSame(
        PersonPhotoError.PermissionDenied,
        repo.setPhoto(circle, PERSON, picture()).exceptionOrNull(),
    )
  }

  @Test
  fun setPhotoReturnsAFailureWhenThePreparedFileCannotBeCreated() = runTest {
    mockkStatic(File::class)
    try {
      every { File.createTempFile(any<String>(), any<String>()) } throws IOException("full")
      val result = repository().setPhoto(circle, PERSON, picture())
      val error = result.exceptionOrNull()
      assertTrue(error is PersonPhotoError.Unknown)
      assertTrue((error as PersonPhotoError.Unknown).source is IOException)
      assertEquals(emptyList<String>(), events)
    } finally {
      unmockkStatic(File::class)
    }
  }

  @Test
  fun setPhotoKeepsTheWriteFailureWhenDeletingTheNewFileFails() = runTest {
    val stuck = IOException("stuck")
    fileStorage.deleteError = stuck
    val failure = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    every { firestore.runTransaction<Any?>(any()) } throws failure

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSame(PersonPhotoError.Network, result.exceptionOrNull())
    assertTrue(writes.isEmpty())
  }

  @Test
  fun cancellationsAreRethrown() = runTest {
    mockkStatic(File::class)
    try {
      every { File.createTempFile(any<String>(), any<String>()) } throws
          CancellationException("stopped")
      val creating = runCatching {
        repository().setPhoto(circle, PERSON, picture())
      }
          .exceptionOrNull()
      assertTrue(creating is CancellationException)
      assertEquals("stopped", creating?.message)
    } finally {
      unmockkStatic(File::class)
    }

    every { firestore.runTransaction<Any?>(any()) } throws CancellationException("stopped")
    val writing = runCatching { repository().setPhoto(circle, PERSON, picture()) }.exceptionOrNull()
    assertTrue(writing is CancellationException)

    restoreTransaction()
    fileStorage.onUpload = { throw CancellationException("stopped") }
    val uploading = runCatching {
      repository().setPhoto(circle, PERSON, picture())
    }
        .exceptionOrNull()
    assertTrue(uploading is CancellationException)

    fileStorage.onUpload = { throw storageError(403) }
    fileStorage.downloadError = CancellationException("stopped")
    val probing = runCatching { repository().setPhoto(circle, PERSON, picture()) }.exceptionOrNull()
    assertTrue(probing is CancellationException)

    fileStorage.downloadError = null
    fileStorage.onUpload = {}
    photos[PERSON] = OLD_PATH
    fileStorage.deleteError = CancellationException("stopped")
    val deleting = runCatching {
      repository().setPhoto(circle, PERSON, picture())
    }
        .exceptionOrNull()
    assertTrue(deleting is CancellationException)

    fileStorage.deleteError = null
    photos.clear()
    val dispatcher =
        object : CoroutineDispatcher() {
          override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            context[Job]?.cancel(CancellationException("stopped"))
            block.run()
          }
        }
    val caching = runCatching {
      repository(cacheDispatcher = dispatcher).setPhoto(circle, PERSON, picture())
    }
        .exceptionOrNull()
    assertTrue(caching is CancellationException)
  }

  @Test
  fun savePendingPhotoReplacesThePendingFileAndDoesNotKeepTheCaptureFile() = runTest {
    val firstCapture = picture(color = Color.RED)
    val secondCapture = picture(color = Color.BLUE)
    val repo = repository()

    assertTrue(repo.savePendingPhoto(firstCapture).isSuccess)
    firstCapture.delete()
    assertTrue(pendingPhoto.isFile)
    assertEquals("red", colorAt(decode(pendingPhoto)))

    assertTrue(repo.savePendingPhoto(secondCapture).isSuccess)
    assertFalse(pendingPhoto.absolutePath == secondCapture.absolutePath)
    secondCapture.delete()

    val kept = decode(pendingPhoto)
    try {
      assertEquals("blue", colorAt(kept))
    } finally {
      kept.recycle()
    }
    assertTrue(pendingPhoto.isFile)
    assertFalse(firstCapture.exists())
    assertFalse(secondCapture.exists())
  }

  @Test
  fun uploadPendingPhotoUploadsThenDeletesThePendingFile() = runTest {
    val repo = repository()
    assertTrue(repo.savePendingPhoto(picture()).isSuccess)
    events.clear()
    var pathFromRules = ""
    var pendingAtUpload = false
    var pendingAtWrite = false
    fileStorage.onUpload = { file ->
      pendingAtUpload = pendingPhoto.isFile
      pathFromRules = requirePhotoUploadable(circle, UID, PHOTO_ID, file)
    }
    duringWrite = { pendingAtWrite = pendingPhoto.isFile }

    val result = repo.uploadPendingPhoto(circle)

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertTrue(pendingAtUpload)
    assertTrue(pendingAtWrite)
    assertFalse(pendingPhoto.exists())
    assertEquals(listOf("upload $BUCKET:$pathFromRules", "write $pathFromRules"), events)
    assertEquals(listOf(FieldPath.of("personPhotos", UID) to pathFromRules), writes)
  }

  @Test
  fun uploadPendingPhotoDoesNothingInTheNoOpCasesAndKeepsThePendingFile() = runTest {
    val repo = repository()
    var uploads = 0
    fileStorage.onUpload = { uploads += 1 }

    val missing = repo.uploadPendingPhoto(circle)

    assertTrue(missing.isSuccess)
    assertFalse(pendingPhoto.exists())
    assertEquals(0, uploads)
    assertEquals(emptyList<String>(), events)

    assertTrue(repo.savePendingPhoto(picture()).isSuccess)
    val kept = pendingPhoto.readBytes()
    events.clear()

    val noBucket = repo.uploadPendingPhoto(circle.copy(storageBucket = ""))

    assertTrue(noBucket.isSuccess)
    assertArrayEquals(kept, pendingPhoto.readBytes())
    assertEquals(0, uploads)
    assertEquals(emptyList<String>(), events)

    val alreadyThere = repo.uploadPendingPhoto(circle.copy(personPhotos = mapOf(UID to OLD_PATH)))

    assertTrue(alreadyThere.isSuccess)
    assertArrayEquals(kept, pendingPhoto.readBytes())
    assertEquals(0, uploads)
    assertEquals(emptyList<String>(), events)
  }

  @Test
  fun clearLocalPhotosRemovesTheCacheAndPendingFileWhenOneStepThrows() = runTest {
    val path = "careCircles/$CIRCLE_ID/people/$PERSON/cached.jpg"
    val repo = repository()
    val cached = repo.getPhotoFile(circle, path).getOrThrow()
    pendingPhoto.writeBytes(byteArrayOf(1, 2, 3))
    val stuck =
        File(cacheDir, "stuck").apply {
          mkdirs()
          File(this, "inside").writeText("x")
        }

    val cacheFailure = repo.clearLocalPhotos()

    val cacheError = cacheFailure.exceptionOrNull()
    assertTrue(cacheError is PersonPhotoError.Unknown)
    assertTrue((cacheError as PersonPhotoError.Unknown).source is IOException)
    assertTrue(cacheError.source.message.orEmpty().startsWith("Could not delete"))
    assertFalse(cached.exists())
    assertFalse(pendingPhoto.exists())
    assertTrue(stuck.exists())

    val again = repository(directory = File(tmp.root, "people-b"))
    val cachedAgain = again.getPhotoFile(circle, path).getOrThrow()
    pendingPhoto.mkdirs()
    File(pendingPhoto, "inside").writeText("x")

    val pendingFailure = again.clearLocalPhotos()

    val pendingError = pendingFailure.exceptionOrNull()
    assertTrue(pendingError is PersonPhotoError.Unknown)
    assertTrue(
        (pendingError as PersonPhotoError.Unknown)
            .source
            .message
            .orEmpty()
            .startsWith("Could not delete")
    )
    assertFalse(cachedAgain.exists())
    assertTrue(pendingPhoto.exists())
  }

  @Test
  fun clearLocalPhotosRethrowsCancellation() = runTest {
    val dispatcher =
        object : CoroutineDispatcher() {
          override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            context[Job]?.cancel(CancellationException("stopped"))
            block.run()
          }
        }
    val repo = repository(cacheDispatcher = dispatcher)
    cacheDir.mkdirs()
    val cached = File(cacheDir, "careCircles_${CIRCLE_ID}_people_${PERSON}_cached.jpg")
    cached.writeBytes(byteArrayOf(1))
    pendingPhoto.writeBytes(byteArrayOf(2))

    val error = runCatching { repo.clearLocalPhotos() }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertEquals("stopped", error?.message)
    assertTrue(cached.exists())
    assertTrue(pendingPhoto.exists())
  }

  @Test
  fun uploadPendingPhotoFailsWhenSignedOutOrTheUploadFailsAndKeepsTheFile() = runTest {
    val signedOut = repository(signedInAs(null))
    assertTrue(signedOut.savePendingPhoto(picture()).isSuccess)

    val notSignedIn = signedOut.uploadPendingPhoto(circle)

    assertSame(PersonPhotoError.NotAuthenticated, notSignedIn.exceptionOrNull())
    assertTrue(pendingPhoto.isFile)
    val kept = pendingPhoto.readBytes()

    val repo = repository()
    fileStorage.onUpload = { throw IOException("offline") }
    val failed = repo.uploadPendingPhoto(circle)

    assertTrue(failed.exceptionOrNull() is PersonPhotoError.Unknown)
    assertArrayEquals(kept, pendingPhoto.readBytes())
  }

  @Test
  fun clearLocalPhotosSucceedsWhenNothingIsPending() = runTest {
    val result = repository().clearLocalPhotos()

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertFalse(pendingPhoto.exists())
  }

  private fun TestScope.repository(
      authRepository: AuthRepository = signedInAs(UID),
      directory: File = cacheDir,
      cacheDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
  ) =
      PersonPhotoRepositoryFirebase(
          fileStorage,
          MediaFileCache(directory, ioDispatcher = cacheDispatcher),
          firestore,
          authRepository,
          pendingPhoto,
          newPhotoId = { PHOTO_ID },
      )

  /** A signed-in user with [uid], or nobody when [uid] is null. */
  private fun signedInAs(uid: String?): AuthRepository = mockk {
    every { currentUser } returns uid?.let { AuthUser(it) }
  }

  /** Puts back the transaction stub after a test replaced it. */
  private fun restoreTransaction() {
    every { firestore.runTransaction<Any?>(any()) } answers
        {
          try {
            Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
          } catch (e: Exception) {
            Tasks.forException(e)
          }
        }
  }

  private fun recordWrite(field: FieldPath, path: String) {
    writes += field to path
    events += "write $path"
    when (field) {
      FieldPath.of("personPhotos", PERSON) -> photos[PERSON] = path
      FieldPath.of("personPhotos", UID) -> photos[UID] = path
    }
  }

  private fun picture(width: Int = 32, height: Int = 24, color: Int = Color.RED): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    val file = File.createTempFile("picture", ".jpg", tmp.root)
    try {
      file.outputStream().use { stream ->
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
      }
    } finally {
      bitmap.recycle()
    }
    return file
  }

  /** Landscape JPEG whose EXIF says to turn it a quarter clockwise. Left half red, right blue. */
  private fun wideTurnedPicture(): File {
    val bitmap = Bitmap.createBitmap(WIDE, TALL, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(WIDE * TALL)
    for (y in 0 until TALL) {
      for (x in 0 until WIDE) {
        pixels[y * WIDE + x] = if (x < WIDE / 2) Color.RED else Color.BLUE
      }
    }
    bitmap.setPixels(pixels, 0, WIDE, 0, 0, WIDE, TALL)
    val file = File.createTempFile("turned", ".jpg", tmp.root)
    try {
      file.outputStream().use { stream ->
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
      }
    } finally {
      bitmap.recycle()
    }
    ExifInterface(file).apply {
      setAttribute(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_ROTATE_90.toString(),
      )
      saveAttributes()
    }
    return file
  }

  private fun decode(bytes: ByteArray): Bitmap =
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          ?: throw AssertionError("Prepared photo is not a JPEG")

  private fun decode(file: File): Bitmap =
      BitmapFactory.decodeFile(file.absolutePath)
          ?: throw AssertionError("${file.path} is not a JPEG")

  private fun colorAt(bitmap: Bitmap, y: Int = bitmap.height / 2): String {
    val pixel = bitmap.getPixel(bitmap.width / 2, y)
    val red = Color.red(pixel)
    val blue = Color.blue(pixel)
    return when {
      red > 180 && blue < 80 -> "red"
      blue > 180 && red < 80 -> "blue"
      else -> "red=$red blue=$blue"
    }
  }

  private fun storageError(httpCode: Int): StorageException =
      requireNotNull(StorageException.fromExceptionAndHttpCode(null, httpCode))

  /**
   * [FakeMediaFileStorage] whose download can fail. A refused upload is only success when the
   * object is already stored, and a cached photo is returned when a later download would fail.
   */
  private class PhotoStorage(private val files: FakeMediaFileStorage) : MediaFileStorage by files {
    var downloadError: Exception? = null

    var deleteError: Exception?
      get() = files.deleteError
      set(value) {
        files.deleteError = value
      }

    var onUpload: suspend (File) -> Unit
      get() = files.onUpload
      set(value) {
        files.onUpload = value
      }

    val lastUploadedContentType: String?
      get() = files.lastUploadedContentType

    val lastUploadedAuthorId: String?
      get() = files.lastUploadedAuthorId

    override suspend fun download(circle: CareCircle, storagePath: String, destination: File) {
      val error = downloadError
      if (error != null) {
        files.calls += "download ${circle.id}:$storagePath"
        throw error
      }
      files.download(circle, storagePath, destination)
    }
  }

  private companion object {
    const val CARE_CIRCLES = "careCircles"
    const val CIRCLE_ID = "c1"
    const val BUCKET = "bucket-c1"
    const val UID = "u1"
    const val PERSON = "tom"
    const val PHOTO_ID = "p1"
    const val OLD_PATH = "careCircles/c1/people/tom/old.jpg"
    const val WIDE = 1200
    const val TALL = 600
    const val MAX_SIDE = 1024

    /** HTTP code [StorageException] maps to a retry-limit failure. */
    const val RETRY_LIMIT_HTTP = -2
  }
}
