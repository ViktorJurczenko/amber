// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.patient.seeMedias.SeeMediasPreviewCircles
import com.github.se.amber.ui.patient.seeMedias.SeeMediasPreviewMediaRepository
import com.github.se.amber.ui.patient.seeMedias.SeeMediasPreviewProfiles
import com.github.se.amber.ui.patient.seeMedias.seeMediasPreviewActiveCircles
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.WEIGHT_ONE
import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

private enum class PreviewAudioState {
  Loading,
  Empty,
  Error,
  Content,
}

private val previewWaveformPattern = listOf(HALF, WEIGHT_ONE, HALF, AUDIO_WAVEFORM_MIN_LEVEL)
private val previewWaveform =
    List(WAVEFORM_BARS) { previewWaveformPattern[it % previewWaveformPattern.size] }

private class SeeAudiosPreviewOwner(state: PreviewAudioState) :
    ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
  override val viewModelStore = ViewModelStore()
  private val profile =
      UserProfile(
          uid = "preview",
          role = Role.PATIENT,
          person = Person(firstName = "Circee"),
          circleIds = listOf("preview"),
      )
  private val circle =
      CareCircle(id = "preview", people = mapOf("relative" to Person(firstName = "Evangeline")))

  private val profiles = SeeMediasPreviewProfiles(profile, state == PreviewAudioState.Error)
  private val circles = SeeMediasPreviewCircles(circle)
  private val activeCircles = seeMediasPreviewActiveCircles()

  private val media =
      SeeMediasPreviewMediaRepository(
          when (state) {
            PreviewAudioState.Loading -> flow { awaitCancellation() }
            PreviewAudioState.Content ->
                flowOf(
                    listOf(
                        MediaItem.Audio(
                            id = "sample1",
                            authorId = profile.uid,
                            description = "I have the best gossip...",
                            shownIds = listOf("relative"),
                            waveform = previewWaveform,
                        ),
                        MediaItem.Audio(
                            id = "sample2",
                            authorId = profile.uid,
                            description = "I saw otters at the zoo today!",
                            shownIds = listOf("relative"),
                            waveform = previewWaveform.asReversed(),
                        ),
                    )
                )
            PreviewAudioState.Empty,
            PreviewAudioState.Error -> flowOf(emptyList())
          }
      )

  private val previewPlayer =
      object : AudioPlayer {
        override val state: StateFlow<AudioState> = MutableStateFlow(AudioState())

        override suspend fun play(file: File) = Unit

        override fun pause() = Unit

        override fun resume() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun stop(file: File) = Unit
      }
  private val audiosViewModel = SeeAudiosViewModel(media, circles, profiles, previewPlayer)

  override val defaultViewModelProviderFactory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when (modelClass) {
              SeeAudiosViewModel::class.java -> audiosViewModel as T
              MediaCardViewModel::class.java ->
                  MediaCardViewModel(profiles, circles, activeCircles) as T
              else -> error("Unexpected ViewModel: $modelClass")
            }
      }
}

@Composable
private fun PreviewSeeAudios(state: PreviewAudioState = PreviewAudioState.Empty) {
  val owner = remember(state) { SeeAudiosPreviewOwner(state) }
  CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
    AmberAppTheme { SeeAudiosScreen() }
  }
}

@Preview(showBackground = true, name = "Empty audios")
@Composable
fun SeeAudiosScreenPreview() {
  PreviewSeeAudios(PreviewAudioState.Empty)
}

@Preview(showBackground = true, name = "Loading audios")
@Composable
fun SeeAudiosLoadingPreview() {
  PreviewSeeAudios(PreviewAudioState.Loading)
}

@Preview(showBackground = true, name = "Audio error")
@Composable
fun SeeAudiosErrorPreview() {
  PreviewSeeAudios(PreviewAudioState.Error)
}

@Preview(showBackground = true, name = "Audios")
@Composable
fun SeeAudiosContentPreview() {
  PreviewSeeAudios(PreviewAudioState.Content)
}
