// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media.picture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.github.se.amber.data.media.MAX_PHOTO_BYTES
import com.github.se.amber.model.circle.CareCircle
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Stores a person's profile photo. */
interface PersonPhotoRepository {

  /**
   * Prepares [file] as a JPEG, uploads it as [personId]'s photo in [circle], writes that entry, and
   * then deletes the previous photo.
   *
   * The entry is written only after the upload. If that write fails, the new file is deleted. A
   * permission denial is success when this photo is already stored, because that upload cannot be
   * repeated. A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun setPhoto(circle: CareCircle, personId: String, file: File): Result<Unit>

  /**
   * Returns the cached photo at [storagePath], downloading it when it is not cached yet. Only the
   * image loader calls this. A file already cached is returned offline.
   *
   * A failed [Result] contains a [PersonPhotoError]. Cancellation is rethrown.
   */
  suspend fun getPhotoFile(circle: CareCircle, storagePath: String): Result<File>
}

/** Typed failures returned by [PersonPhotoRepository]. */
sealed class PersonPhotoError(message: String) : Exception(message) {
  data object NotAuthenticated : PersonPhotoError("No user is signed in")

  data object NoStorageBucket : PersonPhotoError("The care circle has no storage bucket")

  data object CannotPreparePhoto : PersonPhotoError("The photo cannot be prepared")

  data object PermissionDenied : PersonPhotoError("You do not have permission to access this photo")

  data object Network : PersonPhotoError("Unable to reach photo storage")

  data class Unknown(val source: Throwable) : PersonPhotoError("The photo operation failed")
}

/**
 * Writes a new JPEG of [source] to [destination].
 *
 * Applies [source]'s EXIF rotation first, then scales so the longest side is at most 1024 px, then
 * lowers the JPEG quality until the file is within [MAX_PHOTO_BYTES]. [source] is only read.
 * [destination] is a different file, including when [source] was already small enough.
 *
 * @throws PersonPhotoError.CannotPreparePhoto if [source] is not a picture, or it still exceeds
 *   [MAX_PHOTO_BYTES] at the lowest quality.
 * @throws IOException if the new file cannot be written.
 */
internal fun writePreparedJpeg(source: File, destination: File): File {
  val sourcePath = source.absoluteFile.toPath().normalize()
  val destinationPath = destination.absoluteFile.toPath().normalize()
  require(sourcePath != destinationPath) { "A prepared photo is written to a new file" }

  val decoded = decodeSubsampled(source)
  try {
    val upright = rotateByExif(source, decoded)
    try {
      val fitted = scaleToLongestSide(upright)
      try {
        writeNewFile(destination, encodeWithinLimit(fitted))
      } finally {
        if (fitted !== upright) fitted.recycle()
      }
    } finally {
      if (upright !== decoded) upright.recycle()
    }
  } finally {
    decoded.recycle()
  }
  return destination
}

/** Longest side of a prepared profile photo, in pixels. */
private const val MAX_PHOTO_SIDE_PX = 1024

/** First JPEG quality tried. Lowered when the file is still over [MAX_PHOTO_BYTES]. */
private const val JPEG_QUALITY_START = 90

/** How far the JPEG quality drops on each attempt. */
private const val JPEG_QUALITY_STEP = 10

/**
 * Decodes [source] no larger than needed for [MAX_PHOTO_SIDE_PX]. The EXIF rotation and the exact
 * scale are applied to this bitmap afterwards.
 */
private fun decodeSubsampled(source: File): Bitmap {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(source.absolutePath, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
    throw PersonPhotoError.CannotPreparePhoto
  }
  val options =
      BitmapFactory.Options().apply {
        inSampleSize = subsample(bounds.outWidth, bounds.outHeight, MAX_PHOTO_SIDE_PX)
        inScaled = false
      }
  return BitmapFactory.decodeFile(source.absolutePath, options)
      ?: throw PersonPhotoError.CannotPreparePhoto
}

/** Power-of-two sample size that keeps the longest side at or above [maxSide] when it can. */
private fun subsample(width: Int, height: Int, maxSide: Int): Int {
  var sample = 1
  var longest = maxOf(width, height)
  while (longest / 2 >= maxSide) {
    longest /= 2
    sample *= 2
  }
  return sample
}

/** Rotation baked into the pixels. Unreadable EXIF counts as upright, and [bitmap] is unchanged. */
private fun rotateByExif(source: File, bitmap: Bitmap): Bitmap {
  val degrees =
      try {
        ExifInterface(source).rotationDegrees
      } catch (_: IOException) {
        0
      }
  if (degrees == 0) return bitmap
  val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
  return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
}

/** Scales [bitmap] so its longest side is at most [MAX_PHOTO_SIDE_PX], without enlarging it. */
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
  return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

/** Encodes [bitmap] as a JPEG, dropping quality until it fits in [MAX_PHOTO_BYTES]. */
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

/** Writes [bytes] to [destination] by renaming a complete temp file in the same directory. */
private fun writeNewFile(destination: File, bytes: ByteArray) {
  val parent =
      destination.parentFile
          ?: throw IOException("Prepared photo ${destination.path} has no directory")
  if (!parent.isDirectory && !parent.mkdirs()) {
    throw IOException("Could not create ${parent.path}")
  }
  val temporary = File.createTempFile("photo", ".part", parent)
  try {
    temporary.outputStream().use { it.write(bytes) }
    Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
  } finally {
    // The rename already removed it. A leftover that cannot be deleted is not the prepared photo.
    if (temporary.exists()) temporary.delete() // NOSONAR
  }
}
