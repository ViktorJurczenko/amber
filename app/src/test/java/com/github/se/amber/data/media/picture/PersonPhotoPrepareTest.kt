// Written by Alaabrine, with assistance from Cursor Agent.

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
import kotlin.math.abs
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
  fun aFileThatIsNotAPictureIsRejected() {
    val text = File(tmp.root, "notes.txt").apply { writeText("hello") }
    assertThrows(PersonPhotoError.CannotPreparePhoto::class.java) {
      writePreparedJpeg(text, File(tmp.root, "out.jpg"))
    }
  }

  @Test
  fun theSameFileIsRejected() {
    val photo = picture()
    val sameFile =
        assertThrows(IllegalArgumentException::class.java) { writePreparedJpeg(photo, photo) }
    assertEquals("A prepared photo is written to a new file", sameFile.message)
  }

  @Test
  fun aMissingFolderIsCreated() {
    val created = File(tmp.root, "new-dir/photo.jpg")
    writePreparedJpeg(picture(), created)
    assertTrue(created.isFile)
    assertTrue(created.parentFile!!.isDirectory)
  }

  @Test
  fun subsampleUsesTheLargestPowerOfTwoThatStaysLargeEnough() {
    assertEquals(1, subsample(32, 24, MAX_SIDE))
    assertEquals(1, subsample(2047, 10, MAX_SIDE))
    assertEquals(2, subsample(2048, 10, MAX_SIDE))
    assertEquals(2, subsample(3000, 4000, MAX_SIDE))
    assertEquals(8, subsample(8192, 6144, MAX_SIDE))
  }

  @Test
  fun aLandscapePhotoIsScaledDownToTheLongestSide() {
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
  fun aTallPhotoIsScaledDownToTheLongestSide() {
    val tall = File(tmp.root, "tall.jpg")
    writePreparedJpeg(picture(width = 400, height = 1500), tall)
    val fitted = decode(tall)
    try {
      assertEquals((400L * MAX_SIDE / 1500).toInt(), fitted.width)
      assertEquals(MAX_SIDE, fitted.height)
    } finally {
      fitted.recycle()
    }
  }

  @Test
  fun aQuarterTurnMovesTheTopHalfToTheRight() {
    assertColors(
        halves(
            top = Color.RED,
            bottom = Color.BLUE,
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
        ),
        left = Color.BLUE,
        right = Color.RED,
    )
  }

  @Test
  fun aHalfTurnMovesTheTopHalfToTheBottom() {
    assertColors(
        halves(
            top = Color.RED,
            bottom = Color.BLUE,
            orientation = ExifInterface.ORIENTATION_ROTATE_180,
        ),
        top = Color.BLUE,
        bottom = Color.RED,
    )
  }

  @Test
  fun aThreeQuarterTurnMovesTheTopHalfToTheLeft() {
    assertColors(
        halves(
            top = Color.RED,
            bottom = Color.BLUE,
            orientation = ExifInterface.ORIENTATION_ROTATE_270,
        ),
        left = Color.RED,
        right = Color.BLUE,
    )
  }

  @Test
  fun aHorizontalFlipSwapsTheTwoHalves() {
    assertColors(
        halves(
            left = Color.RED,
            right = Color.BLUE,
            orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
        ),
        left = Color.BLUE,
        right = Color.RED,
    )
  }

  @Test
  fun aTransposedPhotoIsMirroredAndThenTurned() {
    assertColors(
        halves(
            top = Color.RED,
            bottom = Color.BLUE,
            orientation = ExifInterface.ORIENTATION_TRANSPOSE,
        ),
        left = Color.RED,
        right = Color.BLUE,
    )
  }

  @Test
  fun aFailedRenameDeletesTheTemporaryFile() {
    val directory = File(tmp.root, "as-directory").apply { mkdirs() }
    assertThrows(IOException::class.java) { writePreparedJpeg(picture(), directory) }
    assertTrue(tmp.root.list().orEmpty().none { it.startsWith("photo") && it.endsWith(".part") })
  }

  @Test
  fun aBitmapThatCannotBeCompressedIsRejected() {
    withDecodedBitmap { bitmap ->
      every { bitmap.compress(any(), any(), any()) } returns false
      assertThrows(PersonPhotoError.CannotPreparePhoto::class.java) {
        writePreparedJpeg(picture(), File(tmp.root, "compress.jpg"))
      }
    }
  }

  @Test
  fun aLowerQualityThatFitsIsKept() {
    val qualities = mutableListOf<Int>()
    withDecodedBitmap { bitmap ->
      every { bitmap.compress(any(), any(), any()) } answers
          {
            val quality = secondArg<Int>()
            qualities += quality
            val size = if (quality > 50) MAX_PHOTO_BYTES + 1 else 1L
            thirdArg<OutputStream>().write(ByteArray(size.toInt()))
            true
          }
      writePreparedJpeg(picture(), File(tmp.root, "lowered.jpg"))
    }
    assertEquals(listOf(90, 80, 70, 60, 50), qualities)
  }

  @Test
  fun aBitmapThatNeverFitsIsRejected() {
    val qualities = mutableListOf<Int>()
    withDecodedBitmap { bitmap ->
      every { bitmap.compress(any(), any(), any()) } answers
          {
            qualities += secondArg<Int>()
            thirdArg<OutputStream>().write(ByteArray((MAX_PHOTO_BYTES + 1).toInt()))
            true
          }
      assertThrows(PersonPhotoError.CannotPreparePhoto::class.java) {
        writePreparedJpeg(picture(), File(tmp.root, "over-limit.jpg"))
      }
    }
    assertEquals(listOf(90, 80, 70, 60, 50, 40, 30, 20, 10, 0), qualities)
  }

  @Test
  fun anUnreadableExifOrientationIsKeptUpright() {
    val source = picture()
    mockkConstructor(ExifInterface::class)
    try {
      every { anyConstructed<ExifInterface>().rotationDegrees } throws IOException("unreadable")
      every { anyConstructed<ExifInterface>().isFlipped } throws IOException("unreadable")
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

  @Test
  fun theSourceFileIsLeftUnchanged() {
    val source =
        halves(
            top = Color.RED,
            bottom = Color.BLUE,
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
        )
    val before = source.readBytes()
    writePreparedJpeg(source, File(tmp.root, "prepared.jpg"))
    assertTrue(before.contentEquals(source.readBytes()))
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
      otherHalf: Int? = null,
      splitLeftRight: Boolean = false,
      orientation: Int? = null,
  ): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (otherHalf == null) {
      bitmap.eraseColor(color)
    } else {
      val pixels =
          IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val first = if (splitLeftRight) x < width / 2 else y < height / 2
            if (first) color else otherHalf
          }
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
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

  private fun halves(
      top: Int? = null,
      bottom: Int? = null,
      left: Int? = null,
      right: Int? = null,
      orientation: Int? = null,
  ): File =
      if (left != null) {
        picture(
            width = 64,
            height = 48,
            color = left,
            otherHalf = right,
            splitLeftRight = true,
            orientation = orientation,
        )
      } else {
        picture(
            width = 64,
            height = 48,
            color = top ?: Color.RED,
            otherHalf = bottom,
            orientation = orientation,
        )
      }

  private fun assertColors(
      source: File,
      left: Int? = null,
      right: Int? = null,
      top: Int? = null,
      bottom: Int? = null,
  ) {
    val prepared = File(tmp.root, "orient-${source.name}")
    writePreparedJpeg(source, prepared)
    val decoded = decode(prepared)
    try {
      if (left != null) assertNear(left, decoded.getPixel(decoded.width / 4, decoded.height / 2))
      if (right != null) {
        assertNear(right, decoded.getPixel(decoded.width * 3 / 4, decoded.height / 2))
      }
      if (top != null) assertNear(top, decoded.getPixel(decoded.width / 2, decoded.height / 4))
      if (bottom != null) {
        assertNear(bottom, decoded.getPixel(decoded.width / 2, decoded.height * 3 / 4))
      }
    } finally {
      decoded.recycle()
    }
  }

  private fun assertNear(expected: Int, actual: Int) {
    fun channel(name: String, expectedChannel: Int, actualChannel: Int) {
      assertTrue(
          "$name of ${hex(expected)} vs ${hex(actual)}",
          abs(expectedChannel - actualChannel) <= COLOR_TOLERANCE,
      )
    }
    channel("red", Color.red(expected), Color.red(actual))
    channel("green", Color.green(expected), Color.green(actual))
    channel("blue", Color.blue(expected), Color.blue(actual))
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

  private fun withDecodedBitmap(block: (Bitmap) -> Unit) {
    val bitmap = mockk<Bitmap>()
    every { bitmap.width } returns 4
    every { bitmap.height } returns 4
    every { bitmap.recycle() } returns Unit
    mockkStatic(BitmapFactory::class)
    try {
      stubDecodedBitmap(bitmap)
      block(bitmap)
    } finally {
      unmockkStatic(BitmapFactory::class)
    }
  }

  private fun decode(file: File): Bitmap =
      BitmapFactory.decodeFile(file.absolutePath)
          ?: throw AssertionError("${file.path} is not a JPEG")

  private fun hex(color: Int) = "#%06X".format(0xFFFFFF and color)

  private companion object {
    const val MAX_SIDE = 1024
    const val COLOR_TOLERANCE = 40
  }
}
