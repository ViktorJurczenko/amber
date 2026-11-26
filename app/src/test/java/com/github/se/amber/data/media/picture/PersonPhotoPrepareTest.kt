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
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
  fun writePreparedJpegAppliesExifBeforeScalingAndLeavesTheSourceUnchanged() {
    // 1200x600 with a quarter-turn becomes 600x1200, then the long side is scaled to 1024.
    val source = wideTurnedPicture()
    val sourceBytes = source.readBytes()
    val prepared = File(tmp.root, "turned-prepared.jpg")

    writePreparedJpeg(source, prepared)

    assertArrayEquals(sourceBytes, source.readBytes())
    val fitted = decode(prepared)
    try {
      assertEquals(512, fitted.width)
      assertEquals(MAX_SIDE, fitted.height)
      assertTrue(prepared.length() in 1..MAX_PHOTO_BYTES)
      // A quarter turn moves the left (red) half to the top and the right (blue) half to the
      // bottom.
      assertEquals("red", colorAt(fitted, fitted.height / 8))
      assertEquals("blue", colorAt(fitted, fitted.height * 7 / 8))
    } finally {
      fitted.recycle()
    }
    assertEquals(0, ExifInterface(prepared).rotationDegrees)
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

  private fun decode(file: File): Bitmap =
      BitmapFactory.decodeFile(file.absolutePath)
          ?: throw AssertionError("${file.path} is not a JPEG")

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

  private fun colorAt(bitmap: Bitmap, y: Int): String {
    val pixel = bitmap.getPixel(bitmap.width / 2, y)
    val red = Color.red(pixel)
    val blue = Color.blue(pixel)
    return when {
      red > 180 && blue < 80 -> "red"
      blue > 180 && red < 80 -> "blue"
      else -> "red=$red blue=$blue"
    }
  }

  private companion object {
    const val MAX_SIDE = 1024
    const val WIDE = 1200
    const val TALL = 600
  }
}
