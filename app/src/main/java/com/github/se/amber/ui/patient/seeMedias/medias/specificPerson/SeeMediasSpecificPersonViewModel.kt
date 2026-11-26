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

  // this function will be implemented in the next pull request that implement the logic of the
  // viewmodel. So for now it doesn't filter the media but return all the medias that belong to the
  // carecircle.
  override fun filterMedia(media: List<MediaItem>): List<MediaItem> = media
}
