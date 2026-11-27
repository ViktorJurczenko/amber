// Written by Alaabrine, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor Agent.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.account

import com.github.se.amber.data.appointment.reminder.LocalReminders
import com.github.se.amber.data.directions.LAST_ROUTE_FILE_NAME
import com.github.se.amber.data.directions.LAST_ROUTE_TEMPORARY_SUFFIX
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.data.media.picture.PersonPhotoRepository
import com.github.se.amber.data.runEveryStep
import com.github.se.amber.model.media.MediaRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears the media, the profile photos, the last walking route and the appointment reminders this
 * device keeps for the signed-in user. Account deletion and sign-out share it, so the steps are not
 * copied into each flow.
 *
 * @param mediaRepository Holds the media kept on the phone and their pending sends.
 * @param imageMemory Holds the decoded pictures; clearLocalMedia doesn't clear these.
 * @param cacheDir The application cache, where the upload screens leave their temporary files.
 * @param photoRepository Holds the profile photos kept on the phone, including a pending one.
 * @param reminders Holds the appointment reminders of this phone. Without clearing them, the next
 *   user of the phone would get the old user's reminders.
 * @param ioDispatcher The dispatcher used for file operations.
 */
class DeviceLocalPhone(
    private val mediaRepository: MediaRepository,
    private val imageMemory: MediaImageMemory,
    private val cacheDir: File,
    private val photoRepository: PersonPhotoRepository,
    private val reminders: LocalReminders,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  /**
   * Cancels the send jobs and clears the outbox and media cache, then the profile photos kept on
   * the phone, then the decoded pictures, then the temporary upload files, then the appointment
   * reminders. Every step is still attempted when an earlier one fails. A cancellation stops at
   * once and is rethrown.
   *
   * @throws Exception if something couldn't be cleared: it may still be on the device. The first
   *   failure is thrown, and any later ones are added to it. Clearing can run again.
   */
  suspend fun clear() {
    runEveryStep(
        { mediaRepository.clearLocalMedia() },
        { photoRepository.clearLocalPhotos() },
        { imageMemory.clearAll() },
        { deleteTemporaryFiles() },
        { reminders.clearAll() },
    )
  }

  /**
   * Deletes the files SelectedPictureReaderAndroid copies picked pictures to, camera photos, voice
   * memos that weren't uploaded (the recorder and the audio reader leave them as .m4a files
   * anywhere in the cache), and the last walking route, which shows where the patient lives and
   * goes, with any temporary copy of it an app killed mid-save left behind.
   *
   * @throws IOException if some are still there afterwards, naming one of them.
   */
  private suspend fun deleteTemporaryFiles() =
      withContext(ioDispatcher) {
        val picked = cacheDir.listFiles { file ->
          file.name.startsWith(PICKED_PREFIX) && file.name.endsWith(PICTURE_SUFFIX)
        }
        val memos = cacheDir.walkTopDown().filter { it.isFile && it.extension == MEMO_EXTENSION }
        val captured = File(cacheDir, CAPTURED_DIR)
        val route = File(cacheDir, LAST_ROUTE_FILE_NAME)
        val routeLeftovers = cacheDir.listFiles { file ->
          file.name.startsWith(LAST_ROUTE_FILE_NAME) &&
              file.name.endsWith(LAST_ROUTE_TEMPORARY_SUFFIX)
        }
        // File.delete() also returns false when the file was already gone, which is fine.
        // walkBottomUp visits the files of captured_pictures before the folder itself.
        val files =
            picked.orEmpty().toList() +
                memos +
                captured.walkBottomUp() +
                route +
                routeLeftovers.orEmpty()
        val left = files.filter { !it.delete() && it.exists() }
        if (left.isNotEmpty()) {
          throw IOException("Could not delete ${left.size} temporary file(s), e.g. ${left.first()}")
        }
      }

  private companion object {
    const val PICKED_PREFIX = "picked_"
    const val PICTURE_SUFFIX = ".jpg"
    const val CAPTURED_DIR = "captured_pictures"
    const val MEMO_EXTENSION = "m4a"
  }
}
