// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Tests what [AddAppointmentScreen] shows and what it sends to [AddAppointmentViewModel]. */
@RunWith(RobolectricTestRunner::class)
class AddAppointmentScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val day = LocalDate.of(2026, 11, 21)

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun screenShowsTheTitleTheFieldsTheButtonAndCancel() {
    showScreen()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.TITLE_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.NOTE_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON).assertIsDisplayed()
  }

  @Test
  fun addButtonIsEnabledOnlyWithATitleAndWhileNotAdding() {
    val state = MutableStateFlow(AddAppointmentUIState())
    val viewModel = viewModel()
    every { viewModel.uiState } returns state
    showScreen(viewModel)

    composeTestRule.onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON).assertIsNotEnabled()
    state.value = state.value.copy(title = "Dr. Martin")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON).assertIsEnabled()
    state.value = state.value.copy(isAdding = true)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun focusedFieldsShowTheirPlaceholderAndTheEmptyTitleMessage() {
    showScreen()

    field(AddAppointmentTestTags.TITLE_FIELD).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText(string(R.string.addAppointmentTitlePlaceholder))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.addAppointmentErrorTitleEmpty))
        .assertIsDisplayed()
    field(AddAppointmentTestTags.NOTE_FIELD).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText(string(R.string.addAppointmentNotePlaceholder))
        .assertIsDisplayed()
  }

  @Test
  fun typedTitleIsSentToTheViewModel() {
    val viewModel = viewModel()
    showScreen(viewModel)
    field(AddAppointmentTestTags.TITLE_FIELD).performTextInput("Dr. Martin")
    verify { viewModel.setTitle("Dr. Martin") }
  }

  @Test
  fun typedNoteIsSentToTheViewModel() {
    val viewModel = viewModel()
    showScreen(viewModel)
    field(AddAppointmentTestTags.NOTE_FIELD).performTextInput("Bring the results")
    verify { viewModel.setNote("Bring the results") }
  }

  @Test
  fun addButtonCallsViewModelWithGivenDate() {
    val viewModel = viewModel(AddAppointmentUIState(title = "Dr. Martin"))
    showScreen(viewModel)
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON).performClick()
    verify(exactly = 1) { viewModel.addAppointment(day) }
  }

  @Test
  fun cancelGoesBackWithoutAddingTheAppointment() {
    var wentBack = false
    val viewModel = viewModel(AddAppointmentUIState(title = "Dr. Martin"))
    showScreen(viewModel, goBack = { wentBack = true })
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    assertTrue(wentBack)
    verify(exactly = 0) { viewModel.addAppointment(any()) }
  }

  @Test
  fun screenIsLeftOnlyOnceTheAddedEventArrives() {
    var wentBack = false
    val events = MutableSharedFlow<AddAppointmentEvent>(extraBufferCapacity = 1)
    val viewModel = viewModel()
    every { viewModel.events } returns events
    showScreen(viewModel, goBack = { wentBack = true })

    composeTestRule.waitForIdle()
    assertFalse(wentBack)
    events.tryEmit(AddAppointmentEvent.Added)
    composeTestRule.waitForIdle()
    assertTrue(wentBack)
  }

  @Test
  fun anErrorIsShownInAToastThenCleared() {
    ShadowToast.reset()
    val viewModel =
        viewModel(AddAppointmentUIState(errorMsgRes = R.string.addAppointmentErrorTitleEmpty))
    showScreen(viewModel)
    composeTestRule.waitForIdle()
    assertEquals(
        string(R.string.addAppointmentErrorTitleEmpty),
        ShadowToast.getTextOfLatestToast(),
    )
    verify(exactly = 1) { viewModel.clearErrorMsg() }
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenTextIsInFrench() {
    showScreen()

    composeTestRule
        .onNodeWithTag(AddAppointmentTestTags.TITLE)
        .assertTextEquals(string(R.string.addAppointmentTitle))
    composeTestRule.onNodeWithText(string(R.string.addAppointmentTitleLabel)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.addAppointmentNoteLabel)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON)
        .assertTextEquals(string(R.string.addAppointmentAddButton))
  }

  @Test
  fun previewShowsTheAddAppointmentScreen() {
    composeTestRule.setContent { AddAppointmentScreenPreview(viewModel()) }
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.TITLE_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.NOTE_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddAppointmentTestTags.ADD_BUTTON).assertIsDisplayed()
  }

  private fun viewModel(
      state: AddAppointmentUIState = AddAppointmentUIState()
  ): AddAppointmentViewModel {
    val viewModel = mockk<AddAppointmentViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    every { viewModel.events } returns emptyFlow()
    return viewModel
  }

  private fun showScreen(
      viewModel: AddAppointmentViewModel = viewModel(),
      goBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AddAppointmentScreen(viewModel = viewModel, date = day, goBack = goBack)
    }
  }

  private fun field(sectionTag: String) =
      composeTestRule.onNode(
          hasTestTag(DisplayTextFieldTestTags.TEXT) and hasAnyAncestor(hasTestTag(sectionTag)),
          useUnmergedTree = true,
      )
}
