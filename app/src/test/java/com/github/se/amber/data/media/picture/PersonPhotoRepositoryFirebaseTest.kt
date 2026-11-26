// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.data.media.FakeMediaFileStorage
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.PHOTO_CONTENT_TYPE
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

  private val fileStorage = FakeMediaFileStorage(events)
  private val firestore = mockk<FirebaseFirestore>()
  private val circles = mockk<CollectionReference>()
  private val circleDocument = mockk<DocumentReference>()
  private val generatedId = mockk<DocumentReference>()
  private val transaction = mockk<Transaction>()
  private val writtenField = slot<FieldPath>()
  private val writtenPath = slot<String>()
  private val snapshot = mockk<DocumentSnapshot>()

  private val photos = mutableMapOf<String, String>()
  private val writes = mutableListOf<Pair<FieldPath, String>>()

  private val cacheDir by lazy { File(tmp.root, "people") }
  private val circle = CareCircle(id = CIRCLE_ID, storageBucket = BUCKET)

  @Before
  fun setUp() {
    every { generatedId.id } returns PHOTO_ID
    every { circles.document() } returns generatedId
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
          transaction
        }
  }

  @Test
  fun setPhotoUploadsThenWritesTheEntryThenDeletesTheOldPath() = runTest {
    photos[PERSON] = OLD_PATH

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSucceeded(result)
    assertEquals(
        listOf(
            "upload $BUCKET:$PHOTO_PATH",
            "write $PHOTO_PATH",
            "delete $CIRCLE_ID:$OLD_PATH",
        ),
        events,
    )
    assertEquals(listOf(FieldPath.of("personPhotos", PERSON) to PHOTO_PATH), writes)
    assertEquals(PHOTO_PATH, photos[PERSON])
    assertEquals(PHOTO_CONTENT_TYPE, fileStorage.lastUploadedContentType)
    assertEquals(UID, fileStorage.lastUploadedAuthorId)
  }

  @Test
  fun setPhotoDropsTheCachedCopyOfThePreviousPhoto() = runTest {
    val repo = repository()
    val cached = repo.getPhotoFile(circle, OLD_PATH).getOrThrow()
    photos[PERSON] = OLD_PATH

    assertSucceeded(repo.setPhoto(circle, PERSON, picture()))

    assertTrue(!cached.exists())
  }

  @Test
  fun setPhotoDoesNotWriteOrDeleteWhenTheUploadFails() = runTest {
    fileStorage.onUpload = { throw storageError(RETRY_LIMIT_HTTP) }

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSame(PersonPhotoError.Network, result.exceptionOrNull())
    assertEquals(emptyList<String>(), events)
    assertTrue(writes.isEmpty())
    assertTrue(photos.isEmpty())
  }

  @Test
  fun setPhotoDeletesTheNewObjectAndKeepsTheOldEntryWhenTheWriteFails() = runTest {
    photos[PERSON] = OLD_PATH
    every { transaction.update(circleDocument, any<FieldPath>(), any()) } throws
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSame(PersonPhotoError.Network, result.exceptionOrNull())
    assertEquals(OLD_PATH, photos[PERSON])
    assertTrue(writes.isEmpty())
    assertEquals(
        listOf("upload $BUCKET:$PHOTO_PATH", "delete $CIRCLE_ID:$PHOTO_PATH"),
        events,
    )
  }

  @Test
  fun aRefusedUploadFollowsTheProbe() = runTest {
    val repo = repository()
    val cases =
        listOf(
            Probe(null, stored = true),
            Probe(storageError(404), PersonPhotoError.PermissionDenied),
            Probe(IOException("missing")),
            Probe(UnknownHostException("down"), PersonPhotoError.Network),
            Probe(storageError(401), PersonPhotoError.NotAuthenticated),
            Probe(storageError(403), PersonPhotoError.PermissionDenied),
            Probe(storageError(500)),
        )
    for (probe in cases) {
      events.clear()
      writes.clear()
      photos.clear()
      photos[PERSON] = OLD_PATH
      fileStorage.downloadError = probe.error
      fileStorage.onUpload = {
        events += "upload $BUCKET:$PHOTO_PATH"
        throw storageError(403)
      }

      val result = repo.setPhoto(circle, PERSON, picture())

      val label = probe.error?.toString() ?: "file already stored"
      if (probe.stored) {
        assertSucceeded(result)
        assertEquals(PHOTO_PATH, photos[PERSON])
        assertEquals(
            listOf(
                "upload $BUCKET:$PHOTO_PATH",
                "download $CIRCLE_ID:$PHOTO_PATH",
                "write $PHOTO_PATH",
                "delete $CIRCLE_ID:$OLD_PATH",
            ),
            events,
        )
      } else if (probe.expected == null) {
        assertUnknown(result, requireNotNull(probe.error))
        assertEquals(label, OLD_PATH, photos[PERSON])
        assertTrue(label, writes.isEmpty())
      } else {
        assertSame(label, probe.expected, result.exceptionOrNull())
        assertEquals(label, OLD_PATH, photos[PERSON])
        assertTrue(label, writes.isEmpty())
      }
    }
  }

  @Test
  fun setPhotoAndGetPhotoFileFailBeforeStorageWhenGuardsRejectTheCall() = runTest {
    val source = picture()
    val sourceBytes = source.readBytes()
    val noBucket = circle.copy(storageBucket = "")

    val signedOut = repository(signedInAs(null)).setPhoto(circle, PERSON, source)
    val blankUid = repository(signedInAs("")).setPhoto(circle, PERSON, source)
    val setNoBucket = repository().setPhoto(noBucket, PERSON, source)
    val getNoBucket = repository().getPhotoFile(noBucket, PHOTO_PATH)

    assertSame(PersonPhotoError.NotAuthenticated, signedOut.exceptionOrNull())
    assertSame(PersonPhotoError.NotAuthenticated, blankUid.exceptionOrNull())
    assertSame(PersonPhotoError.NoStorageBucket, setNoBucket.exceptionOrNull())
    assertSame(PersonPhotoError.NoStorageBucket, getNoBucket.exceptionOrNull())
    assertEquals(emptyList<String>(), events)
    assertTrue(writes.isEmpty())
    assertArrayEquals(sourceBytes, source.readBytes())
  }

  @Test
  fun getPhotoFileDownloadsOnceThenReturnsTheCachedFileWhenDownloadWouldFail() = runTest {
    val path = "careCircles/$CIRCLE_ID/people/$PERSON/cached.jpg"
    val repo = repository()

    val first = repo.getPhotoFile(circle, path)

    assertSucceeded(first)
    assertEquals("content of $path", first.getOrThrow().readText())
    assertEquals(listOf("download $CIRCLE_ID:$path"), events)
    fileStorage.downloadError = IOException("offline")

    val second = repo.getPhotoFile(circle, path)

    assertSucceeded(second)
    assertEquals(first.getOrThrow().absolutePath, second.getOrThrow().absolutePath)
    assertEquals("content of $path", second.getOrThrow().readText())
    assertEquals(listOf("download $CIRCLE_ID:$path"), events)
  }

  @Test
  fun getPhotoFileRejectsAPathOutsideTheCircle() = runTest {
    val outside =
        listOf(
            "careCircles/other/people/$PERSON/$PHOTO_ID.jpg",
            "careCircles/$CIRCLE_ID/people/${PERSON}_2/$PHOTO_ID.jpg",
            "photos/$CIRCLE_ID/people/$PERSON/$PHOTO_ID.jpg",
            "careCircles/$CIRCLE_ID/people/$PERSON/$PHOTO_ID.png",
            "careCircles/$CIRCLE_ID/people/$PERSON.jpg", // too few segments
            "careCircles/$CIRCLE_ID/places/$PERSON/$PHOTO_ID.jpg", // not under people
            "careCircles/$CIRCLE_ID/people/$PERSON/${PHOTO_ID}_1.jpg", // invalid photo id
        )
    for (path in outside) {
      val result = repository().getPhotoFile(circle, path)
      val error = result.exceptionOrNull()
      assertTrue(path, error is PersonPhotoError.Unknown)
      assertTrue((error as PersonPhotoError.Unknown).source is IllegalArgumentException)
    }
    assertEquals(emptyList<String>(), events)
  }

  @Test
  fun getPhotoFileMapsARefusedDownloadToPermissionDenied() = runTest {
    fileStorage.downloadError = storageError(403)

    val result = repository().getPhotoFile(circle, PHOTO_PATH)

    assertSame(PersonPhotoError.PermissionDenied, result.exceptionOrNull())
  }

  @Test
  fun setPhotoDoesNotDeleteWhenThePreviousPathIsTheNewPath() = runTest {
    photos[PERSON] = PHOTO_PATH

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSucceeded(result)
    assertEquals(listOf("upload $BUCKET:$PHOTO_PATH", "write $PHOTO_PATH"), events)
    assertEquals(PHOTO_PATH, photos[PERSON])
  }

  @Test
  fun setPhotoSucceedsWhenTheCacheCannotKeepThePhoto() = runTest {
    val blocked = File(tmp.root, "not-a-directory").apply { writeBytes(byteArrayOf(1)) }

    val result = repository(directory = blocked).setPhoto(circle, PERSON, picture())

    assertSucceeded(result)
    assertEquals(1, writes.size)
  }

  @Test
  fun setPhotoTreatsANonStringPhotoEntryAsMissing() = runTest {
    var stored: Any? = "nope"
    every { snapshot.get(any<String>()) } answers
        {
          if (firstArg<String>() == "personPhotos") stored else null
        }
    val repo = repository()

    assertSucceeded(repo.setPhoto(circle, PERSON, picture()))
    assertTrue(events.none { it.startsWith("delete") })

    stored = mapOf(PERSON to 1)
    events.clear()

    assertSucceeded(repo.setPhoto(circle, PERSON, picture()))
    assertTrue(events.none { it.startsWith("delete") })
  }

  @Test
  fun failuresMapToTheExpectedPhotoError() = runTest {
    val repo = repository()
    val firestoreFailures =
        listOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED to PersonPhotoError.PermissionDenied,
            FirebaseFirestoreException.Code.UNAUTHENTICATED to PersonPhotoError.NotAuthenticated,
            FirebaseFirestoreException.Code.UNAVAILABLE to PersonPhotoError.Network,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED to PersonPhotoError.Network,
            FirebaseFirestoreException.Code.ABORTED to null,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT to null,
        )
    for ((code, expected) in firestoreFailures) {
      val failure = FirebaseFirestoreException(code.name, code)
      every { firestore.runTransaction<Any?>(any()) } throws failure

      val result = repo.setPhoto(circle, PERSON, picture())

      if (expected == null) assertUnknown(result, failure)
      else assertSame(code.name, expected, result.exceptionOrNull())
    }

    val storageFailures =
        listOf(
            storageError(401) to PersonPhotoError.NotAuthenticated,
            storageError(RETRY_LIMIT_HTTP) to PersonPhotoError.Network,
            storageError(500) to null,
        )
    for ((failure, expected) in storageFailures) {
      fileStorage.onUpload = { throw failure }

      val result = repo.setPhoto(circle, PERSON, picture())

      if (expected == null) assertUnknown(result, failure)
      else assertSame(failure.toString(), expected, result.exceptionOrNull())
    }

    val connections =
        listOf(
            UnknownHostException("down"),
            SocketException("reset"),
            SocketTimeoutException("slow"),
            requireNotNull(
                StorageException.fromExceptionAndHttpCode(UnknownHostException("down"), 500)
            ),
        )
    for (failure in connections) {
      fileStorage.onUpload = { throw failure }

      assertSame(
          failure.toString(),
          PersonPhotoError.Network,
          repo.setPhoto(circle, PERSON, picture()).exceptionOrNull(),
      )
    }
  }

  @Test
  fun setPhotoReturnsAFailureWhenThePreparedFileCannotBeCreated() = runTest {
    val source = picture()
    mockkStatic(File::class)
    try {
      // writePreparedJpeg creates this file in writeNewFile, with the prefix "photo".
      every { File.createTempFile(any<String>(), any<String>(), any<File>()) } answers
          {
            if (firstArg<String>() == "photo") throw IOException("full")
            callOriginal()
          }

      val result = repository().setPhoto(circle, PERSON, source)

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
    fileStorage.deleteError = IOException("stuck")
    every { firestore.runTransaction<Any?>(any()) } throws
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

    val result = repository().setPhoto(circle, PERSON, picture())

    assertSame(PersonPhotoError.Network, result.exceptionOrNull())
    assertTrue(writes.isEmpty())
  }

  @Test
  fun setPhotoRethrowsCancellationWhileCreatingThePreparedFile() = runTest {
    val source = picture()
    mockkStatic(File::class)
    try {
      // writePreparedJpeg creates this file in writeNewFile, with the prefix "photo".
      every { File.createTempFile(any<String>(), any<String>(), any<File>()) } answers
          {
            if (firstArg<String>() == "photo") throw CancellationException("stopped")
            callOriginal()
          }

      val error = runCatching { repository().setPhoto(circle, PERSON, source) }.exceptionOrNull()

      assertTrue(error is CancellationException)
      assertEquals("stopped", error?.message)
    } finally {
      unmockkStatic(File::class)
    }
  }

  @Test
  fun setPhotoRethrowsCancellationWhileWritingAndDoesNotDeleteTheNewFile() = runTest {
    every { firestore.runTransaction<Any?>(any()) } throws CancellationException("stopped")

    val error = runCatching { repository().setPhoto(circle, PERSON, picture()) }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertTrue(events.none { it.startsWith("delete") })
  }

  @Test
  fun setPhotoRethrowsCancellationWhileProbingARefusedUpload() = runTest {
    fileStorage.onUpload = { throw storageError(403) }
    fileStorage.downloadError = CancellationException("stopped")

    val error = runCatching { repository().setPhoto(circle, PERSON, picture()) }.exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  @Test
  fun setPhotoRethrowsCancellationWhileDeletingThePreviousPhoto() = runTest {
    photos[PERSON] = OLD_PATH
    fileStorage.deleteError = CancellationException("stopped")

    val error = runCatching { repository().setPhoto(circle, PERSON, picture()) }.exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  private fun TestScope.repository(
      authRepository: AuthRepository = signedInAs(UID),
      directory: File = cacheDir,
      ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
  ) =
      PersonPhotoRepositoryFirebase(
          fileStorage,
          MediaFileCache(directory, ioDispatcher = ioDispatcher),
          firestore,
          authRepository,
          ioDispatcher = ioDispatcher,
      )

  /** A signed-in user with [uid], or nobody when [uid] is null. */
  private fun signedInAs(uid: String?): AuthRepository = mockk {
    every { currentUser } returns uid?.let { AuthUser(it) }
  }

  private fun recordWrite(field: FieldPath, path: String) {
    writes += field to path
    events += "write $path"
    if (field == FieldPath.of("personPhotos", PERSON)) photos[PERSON] = path
  }

  private fun assertSucceeded(result: Result<*>) {
    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
  }

  private fun assertUnknown(result: Result<*>, source: Throwable) {
    val error = result.exceptionOrNull()
    assertTrue(error?.toString(), error is PersonPhotoError.Unknown)
    assertSame(source, (error as PersonPhotoError.Unknown).source)
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

  private fun storageError(httpCode: Int): StorageException =
      requireNotNull(StorageException.fromExceptionAndHttpCode(null, httpCode))

  /** What a refused upload's probe did, and the [Result] [setPhoto] should return. */
  private class Probe(
      val error: Exception?,
      val expected: PersonPhotoError? = null,
      val stored: Boolean = false,
  )

  private companion object {
    const val CARE_CIRCLES = "careCircles"
    const val CIRCLE_ID = "c1"
    const val BUCKET = "bucket-c1"
    const val UID = "u1"
    const val PERSON = "tom"
    const val PHOTO_ID = "p1"
    const val PHOTO_PATH = "careCircles/$CIRCLE_ID/people/$PERSON/$PHOTO_ID.jpg"
    const val OLD_PATH = "careCircles/$CIRCLE_ID/people/$PERSON/old.jpg"

    /** HTTP code [StorageException] maps to a retry-limit failure. */
    const val RETRY_LIMIT_HTTP = -2
  }
}
