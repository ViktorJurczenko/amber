// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.navigation

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class TopNavigationMenuRobolectricTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun defaultActionIsCancelWhenOmitted() {
    composeTestRule.setContent { AmberAppTheme { TopNavigationMenu() } }

    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.navigationCancel))
  }

  @Test
  fun defaultOnBackClickIsNoOp() {
    composeTestRule.setContent { AmberAppTheme { TopNavigationMenu() } }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun signOutActionIsDisplayedWhenProvided() {
    var backClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        TopNavigationMenu(
            action = TopNavigationAction.SIGN_OUT,
            onBackClick = { backClicks++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TITLE)
        .assertTextEquals(string(R.string.navigationSignOut))
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { TopNavigationMenuPreview() }

    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.navigationCancel))
  }

  @Test
  fun topBarWithoutAnActionShowsOnlyTheTitle() {
    composeTestRule.setContent {
      AmberAppTheme { AmberTopBar { Text("Screen", Modifier.testTag(SCREEN_TITLE)) } }
    }

    composeTestRule.onNodeWithTag(AmberTopBarTestTags.TOP_BAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SCREEN_TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun topBarWithAnActionShowsTheBackRowAboveTheTitle() {
    var backClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        AmberTopBar(action = TopNavigationAction.CANCEL, onBackClick = { backClicks++ }) {
          Text("Screen", Modifier.testTag(SCREEN_TITLE))
        }
      }
    }

    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TITLE)
        .assertTextEquals(string(R.string.navigationCancel))
    val backRowBottom =
        composeTestRule
            .onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU)
            .getUnclippedBoundsInRoot()
            .bottom
    val titleTop = composeTestRule.onNodeWithTag(SCREEN_TITLE).getUnclippedBoundsInRoot().top
    assertTrue("The title is drawn over the back row", titleTop >= backRowBottom)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    assertEquals(1, backClicks)
  }

  @Test
  fun topBarPreviewRendersWithoutCrashing() {
    composeTestRule.setContent { AmberTopBarPreview() }

    composeTestRule.onNodeWithTag(AmberTopBarTestTags.TOP_BAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun labelsAreInFrenchWhenThePhoneIsInFrench() {
    composeTestRule.setContent { AmberAppTheme { TopNavigationMenu() } }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals("Annuler")
  }

  @Test
  @Config(qualifiers = "de")
  fun labelsFallBackToEnglishForAnUnsupportedLanguage() {
    composeTestRule.setContent { AmberAppTheme { TopNavigationMenu() } }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals("Cancel")
  }

  private companion object {
    const val SCREEN_TITLE = "screen_title"
  }
}
