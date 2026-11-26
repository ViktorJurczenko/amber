// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.github.se.amber.data.media.MAX_PHOTO_BYTES
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Writes a new JPEG of [source] to [destination].
 *
 * Applies [source]'s EXIF orientation first (a horizontal mirror, then the turn), then scales so
 * the longest side is at most [MAX_PHOTO_SIDE_PX], then lowers the JPEG quality until the file is
 * within [MAX_PHOTO_BYTES]. [source] is only read. [destination] is a different file, including
 * when [source] was already small enough. A missing parent directory of [destination] is created.
 *
 * @param source The picture to read. It is not modified.
 * @param destination Where the prepared JPEG is written. It must not be [source].
 * @return [destination], once it holds the prepared JPEG.
 * @throws PersonPhotoError.CannotPreparePhoto if [source] is not a picture, or it still exceeds
 *   [MAX_PHOTO_BYTES] at the lowest quality.
 * @throws IOException if the new file cannot be written.
 */
internal fun writePreparedJpeg(source: File, destination: File): File {
  val sourcePath = source.absoluteFile.toPath().normalize()
  val destinationPath = destination.absoluteFile.toPath().normalize()
  require(sourcePath != destinationPath) { "A prepared photo is written to a new file" }

  val bitmaps = mutableListOf(decodeSubsampled(source))
  try {
    bitmaps += rotateByExif(source, bitmaps.last())
    bitmaps += scaleToLongestSide(bitmaps.last())
    writeNewFile(destination, encodeWithinLimit(bitmaps.last()))
  } finally {
    bitmaps.distinct().forEach(Bitmap::recycle)
  }
  return destination
}

/**
 * Decodes [source] no larger than needed for [MAX_PHOTO_SIDE_PX]. The EXIF orientation and the
 * exact scale are applied to this bitmap afterwards.
 *
 * @param source The picture file.
 * @return The decoded bitmap. Its longest side stays at least [MAX_PHOTO_SIDE_PX] when the picture
 *   is larger than that.
 * @throws PersonPhotoError.CannotPreparePhoto if [source] is not a picture.
 */
private fun decodeSubsampled(source: File): Bitmap {
  val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(source.absolutePath, options)
  if (options.outWidth <= 0 || options.outHeight <= 0) {
    throw PersonPhotoError.CannotPreparePhoto
  }
  options.inJustDecodeBounds = false
  // Keep the pixel dimensions the sample size describes. Density scaling would change them.
  options.inScaled = false
  options.inSampleSize = subsample(options.outWidth, options.outHeight, MAX_PHOTO_SIDE_PX)
  return BitmapFactory.decodeFile(source.absolutePath, options)
      ?: throw PersonPhotoError.CannotPreparePhoto
}

/**
 * Power-of-two sample size that keeps the longest side at or above [maxSide] when it can.
 *
 * @param width The picture width in pixels.
 * @param height The picture height in pixels.
 * @param maxSide The longest side the later scale step targets, in pixels.
 * @return The [BitmapFactory.Options.inSampleSize] to use. 1 when the picture is already small
 *   enough.
 */
internal fun subsample(width: Int, height: Int, maxSide: Int): Int {
  var sample = 1
  var longest = maxOf(width, height)
  while (longest / 2 >= maxSide) {
    longest /= 2
    sample *= 2
  }
  return sample
}

/**
 * Bakes [source]'s EXIF orientation into the pixels. Unreadable EXIF counts as upright, and
 * [bitmap] is returned unchanged.
 *
 * A flipped photo is mirrored horizontally and then turned: that is the order
 * [ExifInterface.getRotationDegrees] documents. [Matrix.postScale] and [Matrix.postRotate]
 * premultiply, so the mirror is posted first.
 *
 * @param source The file whose EXIF orientation is read.
 * @param bitmap The decoded pixels, still in stored orientation.
 * @return [bitmap] when it is already upright, otherwise a new bitmap in display orientation.
 */
private fun rotateByExif(source: File, bitmap: Bitmap): Bitmap {
  val degrees: Int
  val flipped: Boolean
  try {
    val exif = ExifInterface(source)
    degrees = exif.rotationDegrees
    flipped = exif.isFlipped
  } catch (_: IOException) {
    return bitmap
  }
  if (degrees == 0 && !flipped) return bitmap
  val matrix =
      Matrix().apply {
        if (flipped) postScale(-1f, 1f)
        postRotate(degrees.toFloat())
      }
  return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
}

/**
 * Scales [bitmap] so its longest side is at most [MAX_PHOTO_SIDE_PX], without enlarging it.
 *
 * @param bitmap The upright bitmap.
 * @return [bitmap] when it is already small enough, otherwise a new scaled bitmap.
 */
private fun scaleToLongestSide(bitmap: Bitmap): Bitmap {
  val longest = maxOf(bitmap.width, bitmap.height)
  if (longest <= MAX_PHOTO_SIDE_PX) return bitmap
  val width: Int
  val height: Int
  if (bitmap.width >= bitmap.height) {
    width = MAX_PHOTO_SIDE_PX
    height = (bitmap.height.toLong() * MAX_PHOTO_SIDE_PX / bitmap.width).toInt().coerceAtLeast(1)
  } else {
    height = MAX_PHOTO_SIDE_PX
    width = (bitmap.width.toLong() * MAX_PHOTO_SIDE_PX / bitmap.height).toInt().coerceAtLeast(1)
  }
  return bitmap.scale(width, height)
}

/**
 * Encodes [bitmap] as a JPEG, dropping the quality until it fits in [MAX_PHOTO_BYTES].
 *
 * @param bitmap The fitted bitmap.
 * @return The JPEG bytes. The length is at least 1 and at most [MAX_PHOTO_BYTES].
 * @throws PersonPhotoError.CannotPreparePhoto if [bitmap] cannot be compressed, or it is still over
 *   [MAX_PHOTO_BYTES] at quality 0.
 */
private fun encodeWithinLimit(bitmap: Bitmap): ByteArray {
  var quality = JPEG_QUALITY_START
  while (true) {
    val encoded =
        ByteArrayOutputStream().use { buffer ->
          if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)) {
            throw PersonPhotoError.CannotPreparePhoto
          }
          buffer.toByteArray()
        }
    if (encoded.size.toLong() in 1..MAX_PHOTO_BYTES) return encoded
    if (quality == 0) throw PersonPhotoError.CannotPreparePhoto
    quality = (quality - JPEG_QUALITY_STEP).coerceAtLeast(0)
  }
}

/**
 * Writes [bytes] to [destination] by renaming a complete temp file in the same directory.
 *
 * @param destination The file to replace with [bytes]. Its parent directory is created when
 *   missing.
 * @param bytes The prepared JPEG.
 * @throws IOException if the temp file or the rename fails.
 */
private fun writeNewFile(destination: File, bytes: ByteArray) {
  val parent = destination.absoluteFile.parentFile
  parent?.mkdirs()
  val temporary = File.createTempFile("photo", ".part", parent)
  try {
    temporary.outputStream().use { it.write(bytes) }
    Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
  } finally {
    // already gone once renamed
    if (!temporary.delete() && temporary.exists()) Log.w(TAG, "Couldn't delete ${temporary.name}")
  }
}

private const val TAG = "PreparedJpeg"

/** Longest side of a prepared profile photo, in pixels. */
private const val MAX_PHOTO_SIDE_PX = 1024

/** First JPEG quality tried. Lowered when the file is still over [MAX_PHOTO_BYTES]. */
private const val JPEG_QUALITY_START = 90

/** How far the JPEG quality drops on each attempt. */
private const val JPEG_QUALITY_STEP = 10
