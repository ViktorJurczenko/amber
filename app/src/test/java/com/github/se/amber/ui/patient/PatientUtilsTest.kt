// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PatientUtilsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  // ---------- DisplayPatientTitle ----------

  @Test
  fun title_isDisplayedWithGivenText() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle(title = "My Stories") } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals("My Stories")
  }

  @Test
  fun title_isFoundByText() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle(title = "Pictures") } }

    composeTestRule.onNodeWithText("Pictures").assertIsDisplayed()
  }

  @Test
  fun title_fillsParentWidth() {
    composeTestRule.setContent {
      AmberAppTheme { Box(Modifier.width(300.dp)) { DisplayPatientTitle(title = "Width") } }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertWidthIsEqualTo(300.dp)
  }

  @Test
  fun title_updatesWhenTextChanges() {
    val title = mutableStateOf("First")

    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle(title = title.value) } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("First")

    title.value = "Second"
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("Second")
  }

  @Test
  fun title_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTitle(title = "Custom", testTag = "custom_title") }
    }

    composeTestRule.onNodeWithTag("custom_title").assertIsDisplayed().assertTextEquals("Custom")
  }

  @Test
  fun title_withAllDefaults_isDisplayed() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle() } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun title_appliesCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTitle(title = "Width", modifier = Modifier.width(100.dp)) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertWidthIsEqualTo(100.dp)
  }

  @Test
  fun title_usesCustomStyle() {
    val results = mutableListOf<TextLayoutResult>()
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTitle(title = "Style", titleStyle = TextStyle(fontSize = 40.sp))
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).performSemanticsAction(
        SemanticsActions.GetTextLayoutResult
    ) {
      it(results)
    }

    assertEquals(40.sp, results.first().layoutInput.style.fontSize)
  }

  // ---------- DisplayPatientButton (with and without icon) ----------

  @Test
  fun buttons_areDisplayedWithGivenText_andUseTheirDefaultTestTag() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go")
          DisplayPatientButton(text = "Add", icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON).assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertCountEquals(1)
  }

  @Test
  fun buttons_useCustomTestTags_andAreClickedIndependently() {
    var plainClicks = 0
    var iconClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", testTag = "plain", onClick = { plainClicks++ })
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              testTag = "with_icon",
              iconTestTag = "custom_icon",
              onClick = { iconClicks++ },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("plain").assertIsDisplayed()
    composeTestRule.onNodeWithTag("with_icon").assertIsDisplayed()
    composeTestRule.onNodeWithTag("custom_icon", useUnmergedTree = true).assertIsDisplayed()
    // The custom tags replace the default ones.
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertCountEquals(0)

    composeTestRule.onNodeWithTag("with_icon").performClick()
    assertEquals(0, plainClicks)
    assertEquals(1, iconClicks)

    composeTestRule.onNodeWithTag("plain").performClick()
    assertEquals(1, plainClicks)
    assertEquals(1, iconClicks)
  }

  @Test
  fun button_showsIconWithDescription_onlyWhenAnIconIsGiven() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", iconTestTag = "plain_icon")
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              description = "add icon",
          )
        }
      }
    }

    composeTestRule.onAllNodesWithTag("plain_icon", useUnmergedTree = true).assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("add icon")
  }

  @Test
  fun button_iconWithoutDescription_isDecorative() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientButton(text = "Add", icon = Icons.Default.Add) }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
        .assertIsDisplayed()
        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }

  @Test
  fun buttons_withAllDefaults_clickDoesNothing() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton()
          DisplayPatientButton(icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick().assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .performClick()
        .assertIsDisplayed()
  }

  // The custom modifier defines the space available to the button:
  // the button then takes PATIENT_BUTTON_WIDTH (0.7f) of it, i.e. 140.dp out of 200.dp.
  @Test
  fun buttons_applyCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", testTag = "plain", modifier = Modifier.width(200.dp))
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              testTag = "with_icon",
              modifier = Modifier.width(200.dp),
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("plain").assertWidthIsEqualTo(140.dp)
    composeTestRule.onNodeWithTag("with_icon").assertWidthIsEqualTo(140.dp)
  }

  @Test
  fun buttons_updateWhenTextChanges() {
    val text = mutableStateOf("First")
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = text.value)
          DisplayPatientButton(text = text.value, icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("First")
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertTextEquals("First")

    text.value = "Second"
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("Second")
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertTextEquals("Second")
  }

  // ---------- Button width and centering (fillWidth = true) ----------

  @Test
  fun buttons_takeGivenFractionOfParentWidth_andAreCentered() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(text = "Go", buttonWidth = 0.5f, testTag = "half")
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Add",
                icon = Icons.Default.Add,
                buttonWidth = 0.5f,
                testTag = "half_icon",
            )
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(text = "Go", buttonWidth = 1f, testTag = "full")
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Add",
                icon = Icons.Default.Add,
                buttonWidth = 1f,
                testTag = "full_icon",
            )
          }
        }
      }
    }

    composeTestRule
        .onNodeWithTag("half")
        .assertWidthIsEqualTo(150.dp)
        .assertLeftPositionInRootIsEqualTo(75.dp)
    composeTestRule
        .onNodeWithTag("half_icon")
        .assertWidthIsEqualTo(150.dp)
        .assertLeftPositionInRootIsEqualTo(75.dp)
    composeTestRule
        .onNodeWithTag("full")
        .assertWidthIsEqualTo(300.dp)
        .assertLeftPositionInRootIsEqualTo(0.dp)
    composeTestRule
        .onNodeWithTag("full_icon")
        .assertWidthIsEqualTo(300.dp)
        .assertLeftPositionInRootIsEqualTo(0.dp)
  }

  // ---------- Button wrapping its content (fillWidth = false) ----------

  @Test
  fun buttons_withoutFillWidth_wrapTheirContent_ignoreButtonWidth_andRespectExplicitWidth() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Go",
                buttonWidth = 0.3f,
                fillWidth = false,
                testTag = "plain",
            )
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Add",
                icon = Icons.Default.Add,
                description = "add icon",
                fillWidth = false,
                testTag = "with_icon",
            )
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Go",
                buttonWidth = 1f,
                fillWidth = false,
                testTag = "ignored_width",
            )
          }
          DisplayPatientButton(
              text = "Go",
              fillWidth = false,
              modifier = Modifier.width(200.dp),
              testTag = "explicit",
              onClick = { clickCount++ },
          )
        }
      }
    }

    val plain = composeTestRule.onNodeWithTag("plain")
    assertTrue(plain.getUnclippedBoundsInRoot().width < 300.dp)
    plain.assertLeftPositionInRootIsEqualTo(0.dp)

    val withIcon = composeTestRule.onNodeWithTag("with_icon")
    assertTrue(withIcon.getUnclippedBoundsInRoot().width < 300.dp)
    withIcon.assertLeftPositionInRootIsEqualTo(0.dp).assertTextEquals("Add")
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
        .assertContentDescriptionEquals("add icon")

    // buttonWidth (0.3f vs 1f) must have no effect when fillWidth = false.
    val plainWidth = plain.getUnclippedBoundsInRoot().width
    val ignoredWidth =
        composeTestRule.onNodeWithTag("ignored_width").getUnclippedBoundsInRoot().width
    assertEquals(plainWidth, ignoredWidth)

    composeTestRule.onNodeWithTag("explicit").assertWidthIsEqualTo(200.dp).performClick()
    assertEquals(1, clickCount)
  }

  // ---------- Custom height ----------

  @Test
  fun buttons_useCustomHeight() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", buttonHeight = 80.dp)
          DisplayPatientButton(text = "Add", icon = Icons.Default.Add, buttonHeight = 80.dp)
        }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
        .assertHeightIsEqualTo(80.dp)
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
        .assertHeightIsEqualTo(80.dp)
  }

  // ---------- Enabled ----------

  @Test
  fun buttons_areEnabledByDefault() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go")
          DisplayPatientButton(text = "Add", icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertIsEnabled()
  }

  @Test
  fun buttons_enabledStateFollowsParameter() {
    val enabled = mutableStateOf(false)
    var plainClicks = 0
    var iconClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(
              text = "Go",
              enabled = enabled.value,
              onClick = { plainClicks++ },
          )
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              enabled = enabled.value,
              onClick = { iconClicks++ },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsNotEnabled().performClick()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsNotEnabled()
        .performClick()
    assertEquals(0, plainClicks)
    assertEquals(0, iconClicks)

    enabled.value = true

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsEnabled().performClick()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsEnabled()
        .performClick()
    assertEquals(1, plainClicks)
    assertEquals(1, iconClicks)
  }

  // ---------- Outlined button ----------

  private fun assertOutlinedButtonWorks(dark: Boolean) {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme(darkTheme = dark) {
        DisplayPatientButton(text = "Go", outlined = true, onClick = { clicks++ })
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
        .performClick()
    assertEquals(1, clicks)
  }

  @Test fun outlinedButton_light_isDisplayedAndClickable() = assertOutlinedButtonWorks(dark = false)

  @Test fun outlinedButton_dark_isDisplayedAndClickable() = assertOutlinedButtonWorks(dark = true)

  // ---------- DisplayPatientCircularIconButton ----------

  @Test
  fun iconButton_defaults_areEnabledSquareAndHaveNoSpinner() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientCircularIconButton() } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertWidthIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
        .assertHeightIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsEnabled()
  }

  @Test
  fun iconButton_exposesTheDescriptionOnTheButton() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(description = "Play") }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON)
        .assertContentDescriptionEquals("Play")
  }

  @Test
  fun iconButton_click_invokesCallbackOnce() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()

    assertEquals(1, clickCount)
  }

  @Test
  fun iconButton_disabled_isNotEnabledAndIgnoresClicks() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientCircularIconButton(enabled = false, onClick = { clickCount++ })
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()

    assertEquals(0, clickCount)
  }

  @Test
  fun iconButton_withSpinner_showsTheTaggedSpinner() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientCircularIconButton(showSpinner = true, spinnerTestTag = "spinner")
      }
    }

    composeTestRule.onNodeWithTag("spinner", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun iconButton_withSpinnerAndNoSpinnerTag_stillShowsTheButton() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(showSpinner = true) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsDisplayed()
  }

  @Test
  fun iconButton_withoutSpinner_hasNoSpinner() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(spinnerTestTag = "spinner") }
    }

    composeTestRule.onAllNodesWithTag("spinner", useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun iconButton_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(testTag = "custom_icon_button") }
    }

    composeTestRule.onNodeWithTag("custom_icon_button").assertIsDisplayed()
  }

  // ---------- DisplayPatientTextField ----------

  @Test
  fun textField_displaysControlledValueAndPassesEditsToCaller() {
    val value = mutableStateOf("")
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTextField(
            value = value.value,
            onValueChange = { value.value = it },
            placeholder = context.getString(R.string.patientMapSearchHint),
        )
      }
    }

    val field = composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD)
    field.assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchHint))
        .assertIsDisplayed()

    field.performTextInput("Ada")
    assertEquals("Ada", value.value)
    field.assertTextEquals("Ada")

    value.value = "Grace"
    field.assertTextEquals("Grace")
  }

  @Test
  fun textField_displaysOptionalLabelAndCustomTag() {
    val label = context.getString(R.string.patientMapYourPosition)
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTextField(value = "Ada", label = label, testTag = "labelled_text_field")
      }
    }

    composeTestRule.onNodeWithTag("labelled_text_field").assertIsDisplayed()
    composeTestRule.onNodeWithText(label).assertIsDisplayed()
  }

  @Test
  fun textField_emptyErrorAppearsAfterFocusAndClearsWhenFilled() {
    val value = mutableStateOf("")
    val emptyMessage = context.getString(R.string.patientMapPermissionNeeded)
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTextField(
            value = value.value,
            onValueChange = { value.value = it },
            emptyErrorMessage = emptyMessage,
        )
      }
    }

    val error = composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD_ERROR)
    error.assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).performClick()
    error.assertIsDisplayed().assertTextEquals(emptyMessage)

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).performTextInput("Ada")
    error.assertDoesNotExist()

    value.value = ""
    error.assertIsDisplayed().assertTextEquals(emptyMessage)
  }

  @Test
  fun textField_explicitErrorTakesPriorityWithoutFocus() {
    val emptyMessage = context.getString(R.string.patientMapPermissionNeeded)
    val explicitMessage = context.getString(R.string.patientMapLocationOff)
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTextField(
            emptyErrorMessage = emptyMessage,
            error = explicitMessage,
        )
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD_ERROR)
        .assertIsDisplayed()
        .assertTextEquals(explicitMessage)
    composeTestRule.onNodeWithText(emptyMessage).assertDoesNotExist()
  }

  @Test
  fun textField_explicitErrorUpdatesAndClearsWithCallerState() {
    val firstMessage = context.getString(R.string.patientMapPermissionNeeded)
    val secondMessage = context.getString(R.string.patientMapLocationOff)
    val errorMessage = mutableStateOf<String?>(firstMessage)
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTextField(error = errorMessage.value) }
    }

    val error = composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD_ERROR)
    error.assertIsDisplayed().assertTextEquals(firstMessage)

    errorMessage.value = secondMessage
    error.assertIsDisplayed().assertTextEquals(secondMessage)

    errorMessage.value = null
    error.assertDoesNotExist()
  }

  @Test
  fun textField_characterGuidanceAppearsAtThresholdAfterFocus() {
    val value = mutableStateOf("Ada")
    val guidance = context.resources.getQuantityString(R.plurals.textFieldMaxCharReached, 3, 3)
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTextField(value = value.value, maxChar = 3) }
    }

    composeTestRule.onNodeWithText(guidance).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).performClick()
    composeTestRule.onNodeWithText(guidance).assertIsDisplayed()

    value.value = "Ad"
    composeTestRule.onNodeWithText(guidance).assertDoesNotExist()
  }

  @Test
  fun textField_lineGuidanceIsOnlyShownInMultilineMode() {
    val singleLine = mutableStateOf(false)
    val guidance = context.resources.getQuantityString(R.plurals.textFieldMaxLinesReached, 2, 2)
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTextField(
            value = "first\nsecond",
            maxLines = 2,
            singleLine = singleLine.value,
        )
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).performClick()
    composeTestRule.onNodeWithText(guidance).assertIsDisplayed()

    singleLine.value = true
    composeTestRule.onNodeWithText(guidance).assertDoesNotExist()
  }

  @Test
  fun textField_forwardsImeAction() {
    var searches = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTextField(
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { searches++ }),
        )
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).performImeAction()
    assertEquals(1, searches)
  }

  @Test
  fun textField_rejectsNonPositiveCharacterLimit() {
    assertThrows(IllegalArgumentException::class.java) {
      composeTestRule.setContent { AmberAppTheme { DisplayPatientTextField(maxChar = 0) } }
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun textField_rejectsNonPositiveMinimumLines() {
    assertThrows(IllegalArgumentException::class.java) {
      composeTestRule.setContent { AmberAppTheme { DisplayPatientTextField(minLines = 0) } }
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun textField_rejectsMinimumLinesAboveMaximumForMultilineInput() {
    assertThrows(IllegalArgumentException::class.java) {
      composeTestRule.setContent {
        AmberAppTheme { DisplayPatientTextField(minLines = 3, maxLines = 2) }
      }
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun textField_singleLineAllowsMinimumLinesAboveMultilineMaximum() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTextField(singleLine = true, minLines = 3, maxLines = 2) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).assertIsDisplayed()
  }

  // ---------- Previews ----------

  @Test
  fun previews_showTheTextFieldHintAndPatientButtons() {
    composeTestRule.setContent {
      Column {
        DisplayPatientTextFieldPreview()
        DisplayPatientButtonsPreview()
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TEXT_FIELD).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchHint))
        .assertIsDisplayed()

    // The labeled button takes a width fraction inside a weighted row, so it is composed but has
    // no size in this unbounded test. Existence still runs the preview.
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertExists()
  }

  @Test
  fun darkPreview_showsThePatientButtons() {
    composeTestRule.setContent { DisplayPatientButtonsDarkPreview() }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertExists()
  }

  // ---------- DisplayPatientDialog ----------

  @Test
  fun dialog_showsItsTexts_andEachButtonCallsItsOwnCallback_andLeavesOutAnEmptyMessage() {
    var confirms = 0
    var dismisses = 0
    var message by mutableStateOf("Really")
    var detail by mutableStateOf("Details")
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientDialog(
            title = "Stop?",
            message = message,
            detail = detail,
            confirmText = "Stop",
            dismissText = "Keep going",
            onConfirm = { confirms++ },
            onDismiss = { dismisses++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Stop?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Really").assertIsDisplayed()
    composeTestRule.onNodeWithText("Details").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.DIALOG_CONFIRM)
        .assertTextEquals("Stop")
        .performClick()
    assertEquals(1 to 0, confirms to dismisses)
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.DIALOG_DISMISS)
        .assertTextEquals("Keep going")
        .performClick()
    assertEquals(1 to 1, confirms to dismisses)

    // Like "Stop the guidance?": only the title and the buttons
    message = ""
    detail = ""
    composeTestRule.onNodeWithText("Really").assertDoesNotExist()
    composeTestRule.onNodeWithText("Details").assertDoesNotExist()
    // No empty line left in their place
    composeTestRule.onAllNodes(hasText("")).assertCountEquals(0)
    composeTestRule.onNodeWithText("Stop?").assertIsDisplayed()
  }
}
