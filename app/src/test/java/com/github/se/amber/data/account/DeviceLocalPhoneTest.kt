// Written by Alaabrine, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor Agent.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.account

import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.github.se.amber.data.directions.LAST_ROUTE_FILE_NAME
import com.github.se.amber.data.directions.LAST_ROUTE_TEMPORARY_SUFFIX
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.data.media.picture.PersonPhotoRepository
import com.github.se.amber.model.media.MediaRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceLocalPhoneTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val mediaRepository: MediaRepository = mockk(relaxUnitFun = true)
  private val photoRepository: PersonPhotoRepository = mockk(relaxUnitFun = true)
  private val memoryCache: MemoryCache = mockk(relaxUnitFun = true)
  private lateinit var cacheDir: File
  private lateinit var capturedDir: File
  private lateinit var picked: File
  private lateinit var captured: File
  private lateinit var otherFile: File
  private lateinit var pickedNotAPicture: File
  private lateinit var recorded: File
  private lateinit var pickedMemo: File
  private lateinit var lastRoute: File
  private lateinit var routeLeftover: File
  private lateinit var phone: DeviceLocalPhone
  private val calls = mutableListOf<String>()
  private var clearReminders: suspend () -> Unit = { calls += "clearReminders" }

  @Before
  fun setUp() {
    coEvery { mediaRepository.clearLocalMedia() } answers { calls += "clearMedia" }
    coEvery { photoRepository.clearLocalPhotos() } answers { calls += "clearPhotos" }
    every { memoryCache.clear() } answers { calls += "clearMemory" }
    val imageLoader =
        mockk<ImageLoader> { every { memoryCache } returns this@DeviceLocalPhoneTest.memoryCache }
    cacheDir = tempFolder.newFolder("cache")
    picked = File(cacheDir, "picked_123.jpg").apply { writeText("photo") }
    capturedDir = File(cacheDir, "captured_pictures")
    captured =
        File(capturedDir, "captured_123.jpg").apply {
          parentFile!!.mkdirs()
          writeText("photo")
        }
    otherFile = File(cacheDir, "other.txt").apply { writeText("kept") }
    pickedNotAPicture = File(cacheDir, "picked_notes.txt").apply { writeText("kept") }
    recorded = File(cacheDir, "recording_123.m4a").apply { writeText("memo") }
    pickedMemo =
        File(cacheDir, "audio/picked_123.m4a").apply {
          parentFile!!.mkdirs()
          writeText("memo")
        }
    lastRoute = File(cacheDir, LAST_ROUTE_FILE_NAME).apply { writeText("{}") }
    // a temporary copy an app killed mid-save left, named like RouteFile names them
    routeLeftover =
        File(cacheDir, "${LAST_ROUTE_FILE_NAME}123$LAST_ROUTE_TEMPORARY_SUFFIX").apply {
          writeText("{}")
        }
    phone =
        DeviceLocalPhone(
            mediaRepository,
            MediaImageMemory(imageLoader),
            cacheDir,
            photoRepository,
            reminders = mockk { coEvery { clearAll() } coAnswers { clearReminders() } },
            ioDispatcher = Dispatchers.Unconfined,
        )
  }

  @Test
  fun clearRunsMediaThenPhotosThenMemoryThenTemporaryFilesThenReminders() = runBlocking {
    var filesStillThere = false
    var filesGoneBeforeReminders = false
    clearReminders = {
      filesGoneBeforeReminders = !picked.exists()
      calls += "clearReminders"
    }
    every { memoryCache.clear() } answers
        {
          filesStillThere =
              listOf(picked, captured, recorded, pickedMemo, lastRoute, routeLeftover).all {
                it.exists()
              }
          calls += "clearMemory"
        }

    phone.clear()

    assertTrue(filesStillThere)
    assertTemporaryFilesDeleted()
    assertTrue(filesGoneBeforeReminders)
    assertEquals(listOf("clearMedia", "clearPhotos", "clearMemory", "clearReminders"), calls)
  }

  @Test
  fun clearRunsEveryStepAndNamesAFileThatStays() {
    val mediaFailure = IOException("Media left")
    val photoFailure = IOException("Photos left")
    val memoryFailure = IllegalStateException("Memory left")
    coEvery { mediaRepository.clearLocalMedia() } throws mediaFailure
    coEvery { photoRepository.clearLocalPhotos() } answers
        {
          calls += "clearPhotos"
          throw photoFailure
        }
    every { memoryCache.clear() } throws memoryFailure
    // a folder with a file in it can't be deleted, so the temporary-file step fails too
    val stuck = File(cacheDir, "picked_0.jpg").apply { mkdir() }
    File(stuck, "inside").writeText("photo")

    val error = assertThrows(IOException::class.java) { runBlocking { phone.clear() } }

    assertEquals(mediaFailure.message, error.message)
    val suppressed =
        generateSequence<Throwable>(error) { it.cause }.flatMap { it.suppressed.asList() }
    assertEquals(
        listOf(photoFailure.message, memoryFailure.message),
        suppressed.take(2).map { it.message }.toList(),
    )
    val fileFailure = suppressed.last().message.orEmpty()
    assertTrue(fileFailure, fileFailure.startsWith("Could not delete 1 temporary file(s), e.g. "))
    assertTrue(fileFailure, fileFailure.contains("picked_0.jpg"))
    assertTemporaryFilesDeleted()
    assertTrue(stuck.exists())
    // the reminders are still cleared after the earlier failures
    assertEquals(listOf("clearPhotos", "clearReminders"), calls)
  }

  @Test
  fun aFileThatCannotBeDeletedNamesThatFile() {
    val stuck = File(cacheDir, "picked_0.jpg").apply { mkdir() }
    File(stuck, "inside").writeText("photo")
    val other = File(cacheDir, "picked_9.jpg").apply { writeText("photo") }

    val error = assertThrows(IOException::class.java) { runBlocking { phone.clear() } }

    assertEquals("Could not delete 1 temporary file(s), e.g. $stuck", error.message)
    assertFalse(picked.exists())
    assertFalse(other.exists())
    assertTrue(stuck.exists())
  }

  @Test
  fun aCancelledStepStopsAndIsRethrown() {
    coEvery { mediaRepository.clearLocalMedia() } throws CancellationException("stopped")

    val error = runCatching { runBlocking { phone.clear() } }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertEquals("stopped", error?.message)
    assertEquals(emptyList<String>(), calls)
    assertTrue(picked.exists())
  }

  @Test
  fun aCancellationAfterAnEarlierFailureIsRethrown() {
    coEvery { mediaRepository.clearLocalMedia() } throws IOException("Media left")
    every { memoryCache.clear() } throws CancellationException("stopped")

    val error = runCatching { runBlocking { phone.clear() } }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertEquals("stopped", error?.message)
    assertTrue(error?.suppressed?.isEmpty() == true)
    assertTrue(picked.exists())
  }

  @Test
  fun aFailedReminderStepIsThrownAfterTheOtherSteps() {
    val reminderFailure = IllegalStateException("Timer left")
    clearReminders = { throw reminderFailure }

    val error = assertThrows(IllegalStateException::class.java) { runBlocking { phone.clear() } }

    assertEquals(reminderFailure, error)
    assertEquals(listOf("clearMedia", "clearPhotos", "clearMemory"), calls)
    assertFalse(picked.exists())
  }

  @Test
  fun aCancelledPhotoClearStopsAtOnce() {
    coEvery { photoRepository.clearLocalPhotos() } throws CancellationException("stopped")

    val error = runCatching { runBlocking { phone.clear() } }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertEquals("stopped", error?.message)
    assertEquals(listOf("clearMedia"), calls)
    assertTrue(picked.exists())
    assertTrue(captured.exists())
    assertTrue(recorded.exists())
    assertTrue(pickedMemo.exists())
  }

  /** The copies of picked pictures go. A file that only shares their prefix stays. */
  private fun assertTemporaryFilesDeleted() {
    assertFalse(picked.exists())
    assertFalse(capturedDir.exists())
    assertFalse(recorded.exists())
    assertFalse(pickedMemo.exists())
    assertFalse(lastRoute.exists())
    assertFalse(routeLeftover.exists())
    assertTrue(otherFile.exists())
    assertTrue(pickedNotAPicture.exists())
  }
}
