// Written by Yuhan YANG, with assistance from Cursor
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.parameters.createIntivation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.ui.caregiver.DisplayCaregiverButtonTestTags
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationScreen
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationTestTags
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationUIState
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationViewModel
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Tests what [CreateInvitationScreen] draws and which actions a click performs.
 *
 * The ViewModel is mocked, so these tests do not load a profile or call Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class CreateInvitationScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  @Test
  fun showsPlaceholderUsageAndListHeaderWithoutACircleSelector() {
    setScreen(viewModel(CreateInvitationUIState()))

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.HINT_BOX)
        .assertTextContains(string(R.string.createInvitationPlaceholder))
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.USAGE_TEXT)
        .assertTextEquals(string(R.string.createInvitationUsage))
    composeTestRule.onNodeWithText(string(R.string.createInvitationListHeader)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(DisplayCaregiverButtonTestTags.ICON, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "+en-rUS")
  fun showsCodeInvitationAndErrorAndDisablesCreateWhileCreating() {
    setScreen(
        viewModel(
            CreateInvitationUIState(
                generatedCode = "AB12CD34",
                invitations = listOf(invitation("AB12CD34")),
                errorMsgRes = R.string.createInvitationErrorNotAuthenticated,
                isCreating = true,
            ),
        ),
    )

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.HINT_BOX).assertTextContains("AB12CD34")
    composeTestRule
        .onNodeWithText(
            "AB12CD34 | ${createdAtText(Locale.US)} | ${string(R.string.createInvitationStatusActive)}"
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorNotAuthenticated))
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).assertIsNotEnabled()
  }

  @Test
  fun createClickNotifiesTheViewModel() {
    val viewModel = viewModel(CreateInvitationUIState())
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).performClick()

    verify(exactly = 1) { viewModel.createInvitation() }
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenAndErrorAreShownInFrench() {
    setScreen(
        viewModel(CreateInvitationUIState(errorMsgRes = R.string.createInvitationErrorNetwork))
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.TITLE)
        .assertTextEquals(string(R.string.createInvitationTitle))
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.HINT_BOX)
        .assertTextContains(string(R.string.createInvitationPlaceholder))
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.USAGE_TEXT)
        .assertTextEquals(string(R.string.createInvitationUsage))
    composeTestRule.onNodeWithText(string(R.string.createInvitationListHeader)).assertExists()
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorNetwork))
  }

  @Test
  @Config(qualifiers = "fr")
  fun invitationRowShowsTheStatusAndDateInFrench() {
    setScreen(
        viewModel(
            CreateInvitationUIState(
                invitations = listOf(invitation("USED1234", InvitationStatus.USED))
            )
        ),
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.codeRow("USED1234"))
        .assertTextEquals(
            string(
                R.string.createInvitationListRow,
                "USED1234",
                createdAtText(Locale.FRENCH),
                string(R.string.createInvitationStatusUsed),
            ),
        )
  }

  @Test
  @Config(qualifiers = "+en-rUS")
  fun expiredInvitationShowsTheExpiredStatus() {
    setScreen(
        viewModel(
            CreateInvitationUIState(
                invitations =
                    listOf(
                        invitation(
                            "EXP12345",
                            InvitationStatus.EXPIRED,
                            expiresAt = CREATED_AT_MS,
                        ),
                    ),
            ),
        ),
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.codeRow("EXP12345"))
        .assertTextEquals(
            string(
                R.string.createInvitationListRow,
                "EXP12345",
                createdAtText(Locale.US),
                string(R.string.createInvitationStatusExpired),
            ),
        )
  }

  @Test
  fun copyButtonCopiesThatRowAndShowsTheMessage() {
    val state =
        MutableStateFlow(
            CreateInvitationUIState(
                invitations =
                    listOf(
                        invitation("FIRST123"),
                        invitation("SECOND12", InvitationStatus.USED),
                    ),
            ),
        )
    val viewModel =
        mockk<CreateInvitationViewModel>(relaxed = true) {
          every { uiState } returns state
          every { copyCode(any()) } answers
              {
                val code = args[0] as String
                state.value =
                    state.value.copy(
                        codeToCopy = code,
                        copiedMessageRes = R.string.createInvitationCodeCopied,
                    )
              }
          // Without a real clear, a second tap of the same code would leave the state unchanged
          // and the screen would not copy or toast again.
          every { clearCopiedCode() } answers
              {
                state.value = state.value.copy(codeToCopy = null, copiedMessageRes = null)
              }
        }
    setScreen(viewModel)
    ShadowToast.reset()
    val clipboard =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)!!
    val copySecond = composeTestRule.onNodeWithTag(CreateInvitationTestTags.copyCode("SECOND12"))

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.copyCode("FIRST123")).assertIsDisplayed()
    copySecond
        .assertContentDescriptionEquals(string(R.string.createInvitationCopyCode, "SECOND12"))
        .performClick()
    composeTestRule.waitForIdle()

    assertEquals("SECOND12", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    assertEquals(string(R.string.createInvitationCodeCopied), ShadowToast.getTextOfLatestToast())
    assertEquals(1, ShadowToast.shownToastCount())

    clipboard.setPrimaryClip(ClipData.newPlainText("other", "OTHERCODE"))
    copySecond.performClick()
    composeTestRule.waitForIdle()

    assertEquals(2, ShadowToast.shownToastCount())
    assertEquals("SECOND12", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    assertEquals(string(R.string.createInvitationCodeCopied), ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun defaultNavigationCallbacksLeaveTheScreenOpen() {
    composeTestRule.setContent {
      CreateInvitationScreen(viewModel = viewModel(CreateInvitationUIState()))
    }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home))
        .performClick()

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun cancelCallsCheckout() {
    var checkedOut = false
    setScreen(viewModel = viewModel(CreateInvitationUIState()), checkout = { checkedOut = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(checkedOut)
  }

  @Test
  fun selectingAnotherTabReportsThatTab() {
    var selected: Tab? = null
    setScreen(viewModel = viewModel(CreateInvitationUIState()), onTabSelected = { selected = it })

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home))
        .performClick()

    assertEquals(CaregiverTab.Home, selected)
  }

  private fun viewModel(state: CreateInvitationUIState): CreateInvitationViewModel =
      mockk(relaxed = true) { every { uiState } returns MutableStateFlow(state) }

  private fun invitation(
      code: String,
      status: InvitationStatus = InvitationStatus.ACTIVE,
      expiresAt: Long = CREATED_AT_MS + ONE_DAY_MS,
  ): InvitationSummary =
      InvitationSummary(
          code = code,
          createdAt = CREATED_AT_MS,
          expiresAt = expiresAt,
          status = status,
      )

  private fun createdAtText(locale: Locale): String =
      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
          .format(Date(CREATED_AT_MS))

  private fun setScreen(
      viewModel: CreateInvitationViewModel,
      onTabSelected: (Tab) -> Unit = {},
      checkout: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        CreateInvitationScreen(
            viewModel = viewModel,
            onTabSelected = onTabSelected,
            checkout = checkout,
        )
      }
    }
  }

  private companion object {
    const val CREATED_AT_MS = 1_700_000_000_000L
    const val ONE_DAY_MS = 86_400_000L
  }
}
