// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DontMemoize
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.amber.R
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaImage
import com.github.se.amber.ui.media.aspectRatioOf
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.DisplayAudioPlayback
import com.github.se.amber.ui.media.pinchToZoom
import com.github.se.amber.ui.media.rememberPictureZoomState
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMedias
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasConfig
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import com.github.se.amber.ui.theme.AmberAppTheme

/** Test tags of [SeeMediasSpecificPersonScreen]. */
object SeeMediasSpecificPersonTestTags {
  const val NO_MEDIAS = "see_medias_specific_person_no_medias"
  const val ERROR = "see_medias_specific_person_error"
  const val TITLE = "see_medias_specific_person_title"
  const val LOADING = "see_medias_specific_person_loading"

  fun media(id: String = "") = "see_medias_specific_person_media_$id"

  fun mediaCard(id: String = "") = "see_medias_specific_person_media_card_$id"
}

/** Texts and test tags of the screen, given to [SeeGeneralMedias]. */
private val specificPersonScreenConfig =
    SeeGeneralMediasConfig(
        titleRes = R.string.seeMediasSpecificPersonTitle,
        retryRes = R.string.seeMediasSpecificPersonRetry,
        emptyRes = R.string.seeMediasSpecificPersonEmpty,
        titleTag = SeeMediasSpecificPersonTestTags.TITLE,
        errorTag = SeeMediasSpecificPersonTestTags.ERROR,
        emptyTag = SeeMediasSpecificPersonTestTags.NO_MEDIAS,
        loadingTag = SeeMediasSpecificPersonTestTags.LOADING,
        cardTag = { id -> SeeMediasSpecificPersonTestTags.mediaCard(id) },
    )

/**
 * Displays the approved pictures and audios in which the given person is credited.
 *
 * @param personId id of the person whose medias are displayed.
 * @param viewModel provides the list and the playback state of each audio.
 * @param onBackClick called when the top navigation action is selected.
 * @param onTabSelected called when a bottom navigation tab is selected.
 * @param onPersonClick called with the id of a related person when its button is clicked.
 */
@Composable
fun SeeMediasSpecificPersonScreen(
    personId: String = "",
    viewModel: SeeMediasSpecificPersonViewModel =
        viewModel(
            key = personId,
            factory =
                viewModelFactory { initializer { SeeMediasSpecificPersonViewModel(personId) } },
        ),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val playbackStates by viewModel.playbackStates.collectAsStateWithLifecycle()
  val name by viewModel.personName.collectAsStateWithLifecycle()
  val personName = name ?: stringResource(R.string.seeMediasSpecificPersonUnknownName)

  SeeGeneralMedias(
      uiState = uiState,
      config = specificPersonScreenConfig,
      onRetry = viewModel::retry,
      onErrorShown = viewModel::clearErrorMsg,
      onBackClick = onBackClick,
      onTabSelected = onTabSelected,
      onPersonClick = { id -> if (id != personId) onPersonClick(id) },
      titleArgs = listOf(personName),
  ) { item, circle, modifier ->
    when (item) {
      is MediaItem.Audio ->
          DisplayAudioPlayback(
              modifier = modifier.testTag(SeeMediasSpecificPersonTestTags.media(item.id)),
              state =
                  playbackStates[item.id] ?: AudioPlaybackState.Idle(durationMs = item.durationMs),
              waveform = item.waveform,
              onPlayPause = @DontMemoize { viewModel.onPlayPause(item) },
              onRestart = @DontMemoize { viewModel.onRestart(item) },
              onRetry = @DontMemoize { viewModel.onPlayPause(item) },
          )
      is MediaItem.Picture ->
          MediaImage(
              circle = circle,
              picture = item,
              modifier =
                  modifier
                      .aspectRatioOf(item)
                      .pinchToZoom(rememberPictureZoomState())
                      .testTag(SeeMediasSpecificPersonTestTags.media(item.id)),
              loading = { CircularProgressIndicator() },
          )
      // TODO: display the text medias once their screen exists
      else -> Unit
    }
  }
}

@Preview(showBackground = true)
@Composable
internal fun SeeMediasSpecificPersonPreview() {
  AmberAppTheme(dynamicColor = false) {
    SeeGeneralMedias(
        uiState =
            SeeGeneralMediasUIState.Empty(
                UserProfile(uid = "patient", role = Role.PATIENT, person = Person()),
                CareCircle(id = "circle1"),
            ),
        config = specificPersonScreenConfig,
        onRetry = {},
        onErrorShown = {},
        onBackClick = {},
        onTabSelected = {},
        onPersonClick = {},
        titleArgs = listOf("Eva"),
        itemContent = { _, _, _ -> Unit },
    )
  }
}
