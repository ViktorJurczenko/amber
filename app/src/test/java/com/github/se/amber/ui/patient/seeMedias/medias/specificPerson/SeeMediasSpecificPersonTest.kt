// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeeMediasSpecificPersonScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
  private val circle = CareCircle(id = "circle1")

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  private fun setContent(
      state: SeeGeneralMediasUIState<MediaItem> = SeeGeneralMediasUIState.Empty(profile, circle),
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      onPersonClick: (String) -> Unit = {},
  ): SeeMediasSpecificPersonViewModel {
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        SeeMediasSpecificPersonScreen(
            personId = "person1",
            viewModel = viewModel,
            onBackClick = onBackClick,
            onTabSelected = onTabSelected,
            onPersonClick = onPersonClick,
        )
      }
    }
    return viewModel
  }

  // ---------------------------------------------------------------------------------------------
  // Static parts and title
  // ---------------------------------------------------------------------------------------------

  @Test
  fun title_and_navigation_menus_are_displayed() {
    setContent()

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun back_button_calls_onBackClick() {
    var clicked = false
    setContent(onBackClick = { clicked = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(clicked)
  }

  @Test
  fun bottom_tab_calls_onTabSelected() {
    var selected: Tab? = null
    setContent(onTabSelected = { selected = it })

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()

    assertEquals(PatientTab.Home, selected)
  }

  // ---------------------------------------------------------------------------------------------
  // Loading / Empty / Blocking error
  // ---------------------------------------------------------------------------------------------

  @Test
  fun loading_shows_the_loader_and_nothing_else() {
    setContent(state = SeeGeneralMediasUIState.Loading)

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertDoesNotExist()
  }

  @Test
  fun empty_shows_the_empty_message() {
    setContent()

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeMediasSpecificPersonEmpty))
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun blocking_error_shows_the_message_and_retry_calls_the_view_model() {
    val viewModel =
        setContent(
            state =
                SeeGeneralMediasUIState.BlockingError(
                    R.string.seeMediasSpecificPersonErrorLoadMedia
                )
        )

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonErrorLoadMedia))
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertDoesNotExist()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.retry() }
  }

  // ---------------------------------------------------------------------------------------------
  // Non-blocking errors (Toast) and language
  // ---------------------------------------------------------------------------------------------

  @Test
  fun media_error_is_shown_in_a_toast_without_hiding_the_screen() {
    val viewModel =
        setContent(
            state =
                SeeGeneralMediasUIState.Empty(
                    profile,
                    circle,
                    errorMsgRes = R.string.seeMediasSpecificPersonErrorLoadMedia,
                )
        )

    composeTestRule.waitForIdle()

    assertEquals(
        string(R.string.seeMediasSpecificPersonErrorLoadMedia),
        ShadowToast.getTextOfLatestToast(),
    )
    verify(exactly = 1) { viewModel.clearErrorMsg() }
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun screen_is_shown_in_french() {
    setContent()

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonTitle))
    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonEmpty))
  }
}
