// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioPlayerProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.patient.seeMedias.audios.AudioPlaybackController
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasErrors
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasViewModel
import kotlinx.coroutines.flow.StateFlow

private val specificPersonErrors =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seeMediasSpecificPersonErrorLoadProfile,
        noProfile = R.string.seeMediasSpecificPersonErrorNoProfile,
        noCircle = R.string.seeMediasSpecificPersonErrorNoCircle,
        loadCircle = R.string.seeMediasSpecificPersonErrorLoadCircle,
        loadMedia = R.string.seeMediasSpecificPersonErrorLoadMedia,
    )

class SeeMediasSpecificPersonViewModel(
    private val personId: String,
    mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    audioPlayer: AudioPlayer = AudioPlayerProvider.player,
) :
    SeeGeneralMediasViewModel<MediaItem>(
        mediaRepository = mediaRepository,
        circleRepository = circleRepository,
        profileRepository = profileRepository,
        errors = specificPersonErrors,
    ) {

  // Declared before the init block: the load started there may call onItemsChanged
  private val playback =
      AudioPlaybackController(audioPlayer, mediaRepository, viewModelScope) {
        (uiState.value as? SeeGeneralMediasUIState.Content<*>)?.careCircle
      }

  val playbackStates: StateFlow<Map<String, AudioPlaybackState>> = playback.playbackStates




  init {
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem> = media.filter {
    personId in it.shownIds
  }

  override fun onItemsChanged(items: List<MediaItem>) =
      playback.onItemsChanged(items.filterIsInstance<MediaItem.Audio>())

  fun onPlayPause(audio: MediaItem.Audio) = playback.onPlayPause(audio)

  fun onRestart(audio: MediaItem.Audio) = playback.onRestart(audio)

  override fun onCleared() {
    playback.release()
    super.onCleared()
  }

    /** Nickname of the person if there is one, otherwise their first name. Null if not in the circle. */
    private fun CareCircle.displayNameOf(personId: String): String? {
        val person = people[personId] ?: return null
        return person.nickname?.takeIf { it.isNotBlank() } ?: person.firstName
    }
}
