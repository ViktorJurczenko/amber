// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Checks that caregiver titles stay wrap-content and center-aligned so
 * [androidx.compose.material3.CenterAlignedTopAppBar] can center them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisplayCaregiverTitleTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun title_isCenteredWrapContent_notForcedToFillParentWidth() {
    val results = mutableListOf<TextLayoutResult>()
    composeTestRule.setContent {
      AmberAppTheme {
        Box(Modifier.width(300.dp)) {
          DisplayCaregiverTitle(title = "Home", modifier = Modifier.testTag(TITLE_TAG))
        }
      }
    }

    val title = composeTestRule.onNodeWithTag(TITLE_TAG)
    assertTrue(title.getUnclippedBoundsInRoot().width < 300.dp)
    title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    assertEquals(TextAlign.Center, results.first().layoutInput.style.textAlign)
  }

  private companion object {
    const val TITLE_TAG = "display_caregiver_title_test"
  }
}
