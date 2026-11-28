// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.se.amber.model.user.Person
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.PICTURE_DOUBLE_TAP_ZOOM
import com.github.se.amber.ui.theme.PICTURE_MAX_ZOOM
import com.github.se.amber.ui.theme.PICTURE_NO_ZOOM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaUtilsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testTag = "related_person_test_tag"

  private fun setContent(
      person: Person,
      onClick: (String) -> Unit = {},
      tag: String? = testTag,
      modifier: Modifier = Modifier,
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayRelatedPersonButton(
            modifier = modifier,
            person = person,
            onClick = onClick,
            testTag = tag,
        )
      }
    }
  }

  // ---------- DisplayRelatedPersonButton ----------

  @Test
  fun displaysNickname_whenNicknameIsSet() {
    setContent(Person(id = "1", firstName = "Alice", nickname = "Mamie"))

    composeTestRule.onNodeWithTag(testTag).assertTextEquals("Mamie")
  }

  @Test
  fun displaysFirstName_whenNicknameIsNull() {
    setContent(Person(id = "1", firstName = "Alice", nickname = null))

    composeTestRule.onNodeWithTag(testTag).assertTextEquals("Alice")
  }

  @Test
  fun displaysFirstName_whenNicknameIsBlank() {
    setContent(Person(id = "1", firstName = "Alice", nickname = "   "))

    composeTestRule.onNodeWithTag(testTag).assertTextEquals("Alice")
  }

  @Test
  fun click_callsOnClickWithPersonId() {
    var clickedId: String? = null
    setContent(Person(id = "person-42", firstName = "Alice"), onClick = { clickedId = it })

    composeTestRule.onNodeWithTag(testTag).performClick()

    assertEquals("person-42", clickedId)
  }

  @Test
  fun usesDefaultButtonTestTag_whenNoTestTagIsGiven() {
    setContent(Person(id = "1", firstName = "Alice"), tag = null)

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("Alice")
  }

  @Test
  fun button_withCustomModifier_appliesItsWidth() {
    setContent(Person(id = "1", firstName = "Alice"), modifier = Modifier.width(150.dp))

    // the custom width is applied outside the button's own padding
    composeTestRule.onNodeWithTag(testTag).assertWidthIsEqualTo(150.dp - PADDING_MINI * 2)
  }

  @Test
  fun button_withAllDefaults_usesDefaultTagAndClickDoesNothing() {
    composeTestRule.setContent { AmberAppTheme { DisplayRelatedPersonButton() } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists().performClick()
  }

  @Test
  fun preview_showsTheButton() {
    composeTestRule.setContent { DisplayRelatedPersonButtonPreview() }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists()
  }

  // ---------- DisplayBox ----------

  @Test
  fun displayBox_displaysContent_andAppliesCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayBox(modifier = Modifier.testTag("box")) {
          Text("Content", modifier = Modifier.testTag("content"))
        }
      }
    }

    composeTestRule.onNodeWithTag("box").assertIsDisplayed()
    composeTestRule.onNodeWithTag("content").assertTextEquals("Content")
  }

  // ---------- Picture zoom ----------

  private val card = IntSize(CARD_PX, CARD_PX)

  @Test
  fun zoom_staysBetweenTheNormalSizeAndTheLimit_andNeverMovesPastTheCard() {
    val state = PictureZoomState()

    state.transform(zoom = PICTURE_MAX_ZOOM * 2, size = card)
    assertEquals(PICTURE_MAX_ZOOM, state.scale)

    // Zoomed 4 times, the picture goes 1.5 cards past each edge, so it moves at most that far
    state.transform(pan = Offset(CARD_PX * 10f, -CARD_PX * 10f), size = card)
    val maxOffset = CARD_PX * (PICTURE_MAX_ZOOM - PICTURE_NO_ZOOM) / 2
    assertEquals(Offset(maxOffset, -maxOffset), state.offset)

    // Back to the normal size, it isn't moved any more
    state.transform(zoom = PICTURE_NO_ZOOM / (PICTURE_MAX_ZOOM * 2), size = card)
    assertEquals(PICTURE_NO_ZOOM, state.scale)
    assertEquals(Offset.Zero, state.offset)
    assertFalse(state.isZoomed)
  }

  @Test
  fun toggle_zoomsIn_thenBackToTheNormalSize() {
    val state = PictureZoomState()

    state.toggle()
    assertEquals(PICTURE_DOUBLE_TAP_ZOOM, state.scale)

    state.transform(pan = Offset(CARD_PX.toFloat(), 0f), size = card)
    state.toggle()
    assertEquals(PICTURE_NO_ZOOM, state.scale)
    assertEquals(Offset.Zero, state.offset)
  }

  @Test
  fun gestures_pinchZooms_oneFingerScrollsTheListUnlessZoomed_andDoubleTapZoomsInAndOut() {
    val state = PictureZoomState()
    val scroll = ScrollState(0)
    composeTestRule.setContent {
      Column(Modifier.size(CARD_DP).verticalScroll(scroll)) {
        Box(Modifier.size(CARD_DP).pinchToZoom(state).testTag(PICTURE))
        Box(Modifier.size(CARD_DP))
      }
    }

    // At the normal size, one finger scrolls the list
    composeTestRule.onNodeWithTag(PICTURE).performTouchInput { swipeUp() }
    assertTrue(scroll.value > 0)
    assertFalse(state.isZoomed)
    composeTestRule.runOnIdle { runBlocking { scroll.scrollTo(0) } }

    composeTestRule.onNodeWithTag(PICTURE).performTouchInput {
      pinch(
          start0 = center - Offset(FINGER_GAP_PX, 0f),
          end0 = centerLeft,
          start1 = center + Offset(FINGER_GAP_PX, 0f),
          end1 = centerRight,
      )
    }
    assertTrue(state.isZoomed)

    // Zoomed, one finger moves the picture, not the list
    composeTestRule.onNodeWithTag(PICTURE).performTouchInput { swipeLeft() }
    assertNotEquals(Offset.Zero, state.offset)
    assertEquals(0, scroll.value)

    composeTestRule.onNodeWithTag(PICTURE).performTouchInput { doubleClick() }
    assertFalse(state.isZoomed)
    composeTestRule.onNodeWithTag(PICTURE).performTouchInput { doubleClick() }
    assertEquals(PICTURE_DOUBLE_TAP_ZOOM, state.scale)
  }

  private companion object {
    const val PICTURE = "test_picture"
    const val CARD_PX = 100
    const val FINGER_GAP_PX = 10f
    val CARD_DP = 200.dp
  }
}
