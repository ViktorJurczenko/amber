// Written by Alaabrine, with assistance from Cursor Agent.
// Modified by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.data.media.MAX_PHOTO_BYTES
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Unit tests for [writePreparedJpeg]: EXIF, subsample, scale, and write failures. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonPhotoPrepareTest {

  @get:Rule val tmp = TemporaryFolder()

  @Test
  fun writePreparedJpegRejectsAnUnusablePhoto() {
    val text = File(tmp.root, "notes.txt").apply { writeText("hello") }
    assertThrows(PersonPhotoError.CannotPreparePhoto::class.java) {
      writePreparedJpeg(text, File(tmp.root, "out.jpg"))
    }

    val photo = picture()
    val sameFile =
        assertThrows(IllegalArgumentException::class.java) { writePreparedJpeg(photo, photo) }
    assertEquals("A prepared photo is written to a new file", sameFile.message)

    val noParent =
        assertThrows(IOException::class.java) { writePreparedJpeg(picture(), File("prepared.jpg")) }
    assertTrue(noParent.message.orEmpty().contains("has no directory"))

    val parentFile = File(tmp.root, "parent-file").apply { writeBytes(byteArrayOf(1)) }
    val blocked =
        assertThrows(IOException::class.java) {
          writePreparedJpeg(picture(), File(parentFile, "photo.jpg"))
        }
    assertTrue(blocked.message.orEmpty().contains("Could not create"))

    val created = File(tmp.root, "new-dir/photo.jpg")
    writePreparedJpeg(picture(), created)
    assertTrue(created.isFile)
    assertTrue(created.parentFile!!.isDirectory)
  }

  @Test
  fun writePreparedJpegSubsamplesAVeryWidePhotoAndScalesALandscapePhoto() {
    val subsampled = File(tmp.root, "subsampled.jpg")
    writePreparedJpeg(picture(width = 2048, height = 32), subsampled)
    val reduced = decode(subsampled)
    try {
      assertEquals(MAX_SIDE, reduced.width)
      assertTrue(reduced.height < 32)
    } finally {
      reduced.recycle()
    }

    val landscape = File(tmp.root, "landscape.jpg")
    writePreparedJpeg(picture(width = 1500, height = 400), landscape)
    val fitted = decode(landscape)
    try {
      assertEquals(MAX_SIDE, fitted.width)
      assertEquals((400L * MAX_SIDE / 1500).toInt(), fitted.height)
    } finally {
      fitted.recycle()
    }
  }

  @Test
  fun everyPersonPhotoErrorExists() {
    val source = IllegalStateException("missing")
    val unknown = PersonPhotoError.Unknown(source)
    assertEquals("No user is signed in", PersonPhotoError.NotAuthenticated.message)
    assertEquals("The care circle has no storage bucket", PersonPhotoError.NoStorageBucket.message)
    assertEquals(
        "You do not have permission to access this photo",
        PersonPhotoError.PermissionDenied.message,
    )
    assertEquals("Unable to reach photo storage", PersonPhotoError.Network.message)
    assertEquals("The photo operation failed", unknown.message)
    assertSame(source, unknown.source)
  }

  @Test
  fun aQuarterTurnAndATallPhotoAreFitted() {
    assertSize(
        picture(width = 40, height = 20, orientation = ExifInterface.ORIENTATION_ROTATE_90),
        width = 20,
        height = 40,
    )
    assertSize(
        picture(width = 400, height = 1500),
        width = (400L * MAX_SIDE / 1500).toInt(),
        height = MAX_SIDE,
    )
  }

  @Test
  fun aFailedRenameDeletesTheTemporaryFile() {
    val directory = File(tmp.root, "as-directory").apply { mkdirs() }
    assertThrows(IOException::class.java) { writePreparedJpeg(picture(), directory) }
    assertTrue(tmp.root.list().orEmpty().none { it.startsWith("photo") && it.endsWith(".part") })
  }

  @Test
  fun aBitmapThatCannotBeStoredAsASmallJpegIsRejected() {
    val bitmap = mockk<Bitmap>()
    every { bitmap.width } returns 4
    every { bitmap.height } returns 4
    every { bitmap.recycle() } returns Unit
    mockkStatic(BitmapFactory::class)
    try {
      stubDecodedBitmap(bitmap)
      every { bitmap.compress(any(), any(), any()) } returns false
      assertThrows(PersonPhotoError.CannotPreparePhoto::class.java) {
        writePreparedJpeg(picture(), File(tmp.root, "compress.jpg"))
      }

      var attempt = 0
      every { bitmap.compress(any(), any(), any()) } answers
          {
            if (attempt++ == 0)
                thirdArg<OutputStream>().write(ByteArray((MAX_PHOTO_BYTES + 1).toInt()))
            true
          }
      assertThrows(PersonPhotoError.CannotPreparePhoto::class.java) {
        writePreparedJpeg(picture(), File(tmp.root, "over-limit.jpg"))
      }
    } finally {
      unmockkStatic(BitmapFactory::class)
    }
  }

  @Test
  fun anUnreadableExifOrientationIsKeptUpright() {
    val source = picture()
    mockkConstructor(ExifInterface::class)
    try {
      every { anyConstructed<ExifInterface>().rotationDegrees } throws IOException("unreadable")
      val prepared = File(tmp.root, "upright.jpg")
      writePreparedJpeg(source, prepared)
      val decoded = decode(prepared)
      try {
        assertEquals(sourceWidth(source), decoded.width)
        assertTrue(decoded.height > 0)
      } finally {
        decoded.recycle()
      }
    } finally {
      unmockkConstructor(ExifInterface::class)
    }
  }

  private fun sourceWidth(file: File): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    return bounds.outWidth
  }

  private fun picture(
      width: Int = 32,
      height: Int = 24,
      color: Int = Color.RED,
      orientation: Int? = null,
  ): File {
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
    if (orientation != null) {
      ExifInterface(file).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
      }
    }
    return file
  }

  private fun assertSize(source: File, width: Int, height: Int) {
    val prepared = File(tmp.root, "fitted-${source.name}")
    writePreparedJpeg(source, prepared)
    val decoded = decode(prepared)
    try {
      assertEquals(width, decoded.width)
      assertEquals(height, decoded.height)
    } finally {
      decoded.recycle()
    }
  }

  /** Returns [bitmap] from the second decode. The bounds pass reports a tiny upright image. */
  private fun stubDecodedBitmap(bitmap: Bitmap) {
    every { BitmapFactory.decodeFile(any<String>(), any()) } answers
        {
          val options = secondArg<BitmapFactory.Options>()
          if (options.inJustDecodeBounds) {
            options.outWidth = 4
            options.outHeight = 4
            null
          } else {
            bitmap
          }
        }
  }

  private fun decode(file: File): Bitmap =
      BitmapFactory.decodeFile(file.absolutePath)
          ?: throw AssertionError("${file.path} is not a JPEG")

  private companion object {
    const val MAX_SIDE = 1024
  }
}
