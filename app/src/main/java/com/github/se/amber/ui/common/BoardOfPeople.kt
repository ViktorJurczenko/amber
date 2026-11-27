// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL

/** Test tags used by the Board of people screen. */
object BoardOfPeopleTestTags {
  const val TITLE = "board_of_people_title"
  const val CARE_CIRCLE_NAME = "board_of_people_care_circle_name"
}

/**
 * Displays the board of all the people of the care circle.
 *
 * @param viewModel The view model that manages the screen state and actions.
 * @param goBack The callback invoked when the user cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardOfPeopleScreen(
    viewModel: BoardOfPeopleViewModel = viewModel(),
    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
                DisplayCaregiverTitle(
                    Modifier.testTag(BoardOfPeopleTestTags.TITLE),
                    stringResource(R.string.boardOfPeopleTitle),
                )
              }
            }
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(CAREGIVER_PADDING_MEDIUM)
        ) {
          Text(
              text = uiState.careCircleName,
              modifier = Modifier.testTag(BoardOfPeopleTestTags.CARE_CIRCLE_NAME),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onBackground,
          )
        }
      },
  )
}

/** Preview of [BoardOfPeopleScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun BoardOfPeopleScreenPreview() {
  AmberAppTheme(dynamicColor = false) { BoardOfPeopleScreen() }
}
