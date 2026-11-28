// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.common

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BoardOfPeopleScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: BoardOfPeopleViewModel

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Before
  fun setUp() {
    viewModel = BoardOfPeopleViewModel()
  }

  @Test
  fun screen_displaysContent_andCancelTriggersGoBack() {
    var goBackClicks = 0
    composeTestRule.setContent {
      BoardOfPeopleScreen(viewModel = viewModel, goBack = { goBackClicks++ })
    }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BoardOfPeopleTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.boardOfPeopleTitle))
    composeTestRule
        .onNodeWithTag(BoardOfPeopleTestTags.CARE_CIRCLE_NAME)
        .assertExists()
        .assertTextEquals(BoardOfPeopleUIState().careCircleName)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, goBackClicks)
  }

  @Test
  fun screen_withDefaultParameters_doesNotCrashOnBack() {
    // Default goBack is a no-op, and the default viewModel() is provided by the activity.
    composeTestRule.setContent { BoardOfPeopleScreen() }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    composeTestRule.onNodeWithTag(BoardOfPeopleTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun screen_recomposesWithSameThenNewParameters() {
    var goBackClicks = 0
    var recompositionTrigger by mutableIntStateOf(0)
    val firstGoBack: () -> Unit = {}
    val secondGoBack: () -> Unit = { goBackClicks++ }

    composeTestRule.setContent {
      // Reading the trigger recomposes this lambda each time it changes
      val goBack = if (recompositionTrigger < 2) firstGoBack else secondGoBack
      BoardOfPeopleScreen(viewModel = viewModel, goBack = goBack)
    }

    // Same parameters: Compose skips the screen
    composeTestRule.runOnIdle { recompositionTrigger = 1 }
    // New goBack: the screen runs again with the new callback
    composeTestRule.runOnIdle { recompositionTrigger = 2 }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, goBackClicks)
  }

  @Test
  fun screen_updatesWhenTheCareCircleNameChanges() {
    val circleName = "Family circle"
    val stateFlow = MutableStateFlow(BoardOfPeopleUIState())
    val mockedViewModel = mockk<BoardOfPeopleViewModel>(relaxed = true)
    every { mockedViewModel.uiState } returns stateFlow
    composeTestRule.setContent { BoardOfPeopleScreen(viewModel = mockedViewModel) }

    stateFlow.value = BoardOfPeopleUIState(careCircleName = circleName)
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(BoardOfPeopleTestTags.CARE_CIRCLE_NAME)
        .assertIsDisplayed()
        .assertTextEquals(circleName)
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { BoardOfPeopleScreenPreview() }

    composeTestRule
        .onNodeWithTag(BoardOfPeopleTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.boardOfPeopleTitle))
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenIsShownInFrench() {
    composeTestRule.setContent { BoardOfPeopleScreen(viewModel = viewModel) }

    val context = ApplicationProvider.getApplicationContext<Context>()
    val englishConfig =
        Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    val englishTitle =
        context.createConfigurationContext(englishConfig).getString(R.string.boardOfPeopleTitle)

    composeTestRule
        .onNodeWithTag(BoardOfPeopleTestTags.TITLE)
        .assertTextEquals(string(R.string.boardOfPeopleTitle))
    assertNotEquals(englishTitle, string(R.string.boardOfPeopleTitle))
  }
}
