// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.texts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMedias
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasConfig
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.SEE_TEXTS_COLLAPSED_MAX_LINES

/** Test tags for the title, messages, and text cards on [SeeTextsScreen]. */
object SeeTextsTestTags {
  const val NO_TEXTS = "see_texts_no_texts"
  const val ERROR = "see_texts_error"
  const val TITLE = "see_texts_title"
  const val LOADING = "see_texts_loading"

  /** Test tag of the media card under a text story. */
  fun textCard(id: String = "") = "see_texts_text_card_$id"

  /** Test tag of the optional story title above the body. */
  fun storyTitle(id: String = "") = "see_texts_story_title_$id"

  /** Test tag of the story body text. */
  fun storyBody(id: String = "") = "see_texts_story_body_$id"

  /** Test tag of the Show more button for a long story. */
  fun showMore(id: String = "") = "see_texts_show_more_$id"

  /** Test tag of the Show less button for an expanded story. */
  fun showLess(id: String = "") = "see_texts_show_less_$id"
}

/** Texts and test tags of the texts screen, given to [SeeGeneralMedias]. */
private val textsScreenConfig =
    SeeGeneralMediasConfig(
        titleRes = R.string.seeTextsTitle,
        retryRes = R.string.seeTextsRetry,
        emptyRes = R.string.seeTextsEmpty,
        titleTag = SeeTextsTestTags.TITLE,
        errorTag = SeeTextsTestTags.ERROR,
        emptyTag = SeeTextsTestTags.NO_TEXTS,
        loadingTag = SeeTextsTestTags.LOADING,
        cardTag = { id -> SeeTextsTestTags.textCard(id) },
    )

/**
 * Displays approved text stories in a scrollable list. Each item shows the story title, a large
 * body with Show more / Show less, then the shared [com.github.se.amber.ui.media.MediaCard] for
 * people, date and place.
 *
 * @param textViewModel provides list states for text media only.
 * @param onBackClick called when the top navigation action is selected.
 * @param onTabSelected called when a bottom navigation tab is selected.
 * @param onPersonClick navigation function called with the id of a related person when its button
 *   is clicked.
 */
@Composable
fun SeeTextsScreen(
    textViewModel: SeeTextsViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
) {
  val uiState by textViewModel.uiState.collectAsStateWithLifecycle()

  SeeGeneralMedias(
      uiState = uiState,
      config = textsScreenConfig,
      onRetry = textViewModel::retry,
      onErrorShown = textViewModel::clearErrorMsg,
      onBackClick = onBackClick,
      onTabSelected = onTabSelected,
      onPersonClick = onPersonClick,
  ) { text, _, modifier ->
    DisplayTextStory(text = text, modifier = modifier)
  }
}

/**
 * Shows one text story: optional title, large body, and Show more / Show less when the body is
 * longer than [SEE_TEXTS_COLLAPSED_MAX_LINES] lines.
 *
 * @param text the approved text memory to display.
 * @param modifier width and layout constraints from [SeeGeneralMedias].
 */
@Composable
fun DisplayTextStory(text: MediaItem.Text, modifier: Modifier = Modifier) {
  var expanded by rememberSaveable(text.id) { mutableStateOf(false) }
  var canToggle by rememberSaveable(text.id) { mutableStateOf(false) }

  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
  ) {
    if (text.title.isNotBlank()) {
      Text(
          text = text.title,
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.fillMaxWidth().testTag(SeeTextsTestTags.storyTitle(text.id)),
      )
    }
    Text(
        text = text.text,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = if (expanded) Int.MAX_VALUE else SEE_TEXTS_COLLAPSED_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
          if (!expanded && layout.hasVisualOverflow) {
            canToggle = true
          }
        },
        modifier = Modifier.fillMaxWidth().testTag(SeeTextsTestTags.storyBody(text.id)),
    )
    if (canToggle || expanded) {
      val showMore = !expanded
      DisplayPatientButton(
          modifier = Modifier.padding(start = PATIENT_PADDING_MINI),
          buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
          buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
          fillWidth = false,
          text =
              stringResource(
                  if (showMore) R.string.seeTextsShowMore else R.string.seeTextsShowLess,
              ),
          textStyle = MaterialTheme.typography.titleMedium,
          onClick = { expanded = !expanded },
          testTag =
              if (showMore) SeeTextsTestTags.showMore(text.id)
              else SeeTextsTestTags.showLess(text.id),
      )
    }
  }
}
