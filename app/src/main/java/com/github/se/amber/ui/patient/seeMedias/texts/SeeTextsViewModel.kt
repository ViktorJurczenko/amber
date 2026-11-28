// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.texts

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

/** Error message resources used by the texts ViewModel, passed to [SeeGeneralMediasViewModel]. */
private val textsErrors =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seeTextsErrorLoadProfile,
        noProfile = R.string.seeTextsErrorNoProfile,
        noCircle = R.string.seeTextsErrorNoCircle,
        loadCircle = R.string.seeTextsErrorLoadCircle,
        loadMedia = R.string.seeTextsErrorLoadMedia,
    )

/**
 * Loads the patient's approved text stories. All the loading logic is in
 * [SeeGeneralMediasViewModel].
 */
class SeeTextsViewModel(
    mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) :
    SeeGeneralMediasViewModel<MediaItem.Text>(
        mediaRepository = mediaRepository,
        circleRepository = circleRepository,
        profileRepository = profileRepository,
        errors = textsErrors,
    ) {

  init {
    load()
  }

  /** Keeps only approved [MediaItem.Text] items, in repository order. */
  override fun filterMedia(media: List<MediaItem>): List<MediaItem.Text> =
      media.filterIsInstance<MediaItem.Text>()
}
