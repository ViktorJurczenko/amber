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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
 * @param audioPlayer plays audio and reports playback progress.
 */
class SeeMediasSpecificPersonViewModel(
    private val personId: String = "",
    mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
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

  /** Playback state of each audio card, by media ID. */
  val playbackStates: StateFlow<Map<String, AudioPlaybackState>> = playback.playbackStates

  /** Name of the person shown in the title; null until it is known. */
  val personName: StateFlow<String?> =
      uiState
          .map { state ->
            when (state) {
              is SeeGeneralMediasUIState.Content -> state.careCircle
              is SeeGeneralMediasUIState.Empty -> state.careCircle
              else -> null
            }
          }
          .filterNotNull()
          // The circle is republished at each media update: look the name up once per circle
          .distinctUntilChangedBy { it.id }
          .map { circle -> loadPersonName(circle) }
          .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  init {
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem> = media.filter {
    personId in it.shownIds
  }

  /** Stops the playback if the active audio is no longer in the list. */
  override fun onItemsChanged(items: List<MediaItem>) =
      playback.onItemsChanged(items.filterIsInstance<MediaItem.Audio>())

  /**
   * Downloads [audio] on first play, then pauses or resumes it.
   *
   * @param audio the memory whose playback button was pressed.
   */
  fun onPlayPause(audio: MediaItem.Audio) = playback.onPlayPause(audio)

  /**
   * Seeks the loaded [audio] to the start.
   *
   * @param audio the memory whose restart button was pressed.
   */
  fun onRestart(audio: MediaItem.Audio) = playback.onRestart(audio)

  /**
   * Nickname of the person if there is one, otherwise their first name. A person without an account
   * is in the circle; the Person of a member is in their profile.
   */
  private suspend fun loadPersonName(circle: CareCircle): String? {
    val person =
        circle.people[personId] ?: profileRepository.getProfile(personId).getOrNull()?.person
    return person?.let { it.nickname?.takeIf(String::isNotBlank) ?: it.firstName }
  }

  override fun onCleared() {
    playback.release()
    super.onCleared()
  }
}
