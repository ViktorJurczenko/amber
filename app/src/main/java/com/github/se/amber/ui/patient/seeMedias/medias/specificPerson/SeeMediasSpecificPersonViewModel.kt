// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasErrors
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasViewModel

private val specificPersonErrors =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seeMediasSpecificPersonErrorLoadProfile,
        noProfile = R.string.seeMediasSpecificPersonErrorNoProfile,
        noCircle = R.string.seeMediasSpecificPersonErrorNoCircle,
        loadCircle = R.string.seeMediasSpecificPersonErrorLoadCircle,
        loadMedia = R.string.seeMediasSpecificPersonErrorLoadMedia,
    )

/**
 * Loads the approved medias (pictures, audios...) in which the person [personId] is credited.
 *
 * @param personId id of the person whose medias are displayed.
 * @param mediaRepository observes approved media and downloads audio files.
 * @param circleRepository loads the patient's care circle.
 * @param profileRepository loads the current patient profile and the profile of a member.
 */
class SeeMediasSpecificPersonViewModel(
    private val personId: String = "",
    mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) :
    SeeGeneralMediasViewModel<MediaItem>(
        mediaRepository = mediaRepository,
        circleRepository = circleRepository,
        profileRepository = profileRepository,
        errors = specificPersonErrors,
    ) {

  init {
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem> = media

  /**
   * Downloads [audio] on first play, then pauses or resumes it.
   *
   * @param audio the memory whose playback button was pressed.
   */
  fun onPlayPause(audio: MediaItem.Audio) {}

  /**
   * Seeks the loaded [audio] to the start.
   *
   * @param audio the memory whose restart button was pressed.
   */
  fun onRestart(audio: MediaItem.Audio) {}
}
