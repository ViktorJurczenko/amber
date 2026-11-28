// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.width
import com.github.se.amber.R
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.every
import io.mockk.mockk
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

/** Checks that the caregiver home screen shows its text and errors in French. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaregiverHomeTranslationTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun setScreen(state: CaregiverHomeUIState) {
    val viewModel = mockk<CaregiverHomeViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    composeTestRule.setContent { CaregiverHomeScreen(viewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun titleAndWelcomeWithThePatientNameAreInFrench() {
    setScreen(
        CaregiverHomeUIState(
            patient =
                UserProfile(uid = "p1", role = Role.PATIENT, person = Person(firstName = "Arthur"))
        )
    )

    composeTestRule.onNodeWithTag(CaregiverHomeTestTags.TITLE).assertTextEquals("Accueil")
    composeTestRule
        .onNodeWithTag(CaregiverHomeTestTags.WELCOME_TEXT)
        .assertTextEquals("Bienvenue\u00A0! Vous êtes avec Arthur\u00A0!")
  }

  @Test
  fun welcomeWithoutAPatientIsInFrench() {
    setScreen(CaregiverHomeUIState())

    composeTestRule
        .onNodeWithTag(CaregiverHomeTestTags.WELCOME_TEXT)
        .assertTextEquals("Bienvenue\u00A0! Aucun patient n'est encore lié à ce cercle de soins.")
  }

  @Test
  fun errorToastIsInFrench() {
    setScreen(CaregiverHomeUIState(errorMsgRes = R.string.caregiverHomeErrorLoadCircle))

    assertEquals(
        "Impossible de charger le cercle de soins.",
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun title_isCenteredWrapContentUnderCenterAlignedTopAppBar() {
    val results = mutableListOf<TextLayoutResult>()
    setScreen(CaregiverHomeUIState())

    val title = composeTestRule.onNodeWithTag(CaregiverHomeTestTags.TITLE)
    val rootWidth = composeTestRule.onRoot().getUnclippedBoundsInRoot().width
    val titleBounds = title.getUnclippedBoundsInRoot()
    assertTrue(titleBounds.width < rootWidth)
    // Geometric center of the title should sit near the screen midpoint.
    val titleMid = (titleBounds.left + titleBounds.right) / 2f
    val rootMid = rootWidth / 2f
    assertTrue(kotlin.math.abs(titleMid.value - rootMid.value) < 8f)

    title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    assertEquals(TextAlign.Center, results.first().layoutInput.style.textAlign)
  }
}
