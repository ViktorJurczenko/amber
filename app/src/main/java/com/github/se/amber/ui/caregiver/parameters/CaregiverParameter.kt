// Written by Yuhan YANG, with assistance from Cursor.
// Modify by Lou-Anne Maier, with assistance from Claude Code.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.parameters

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconText
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.AmberTopBar
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL

/** Test tags of [CaregiverParameterScreen]. */
object CaregiverParametersTestTags {
  const val TITLE = "caregiver_parameter_title"
  const val CREATE_CARE_CIRCLE_BUTTON = "caregiver_parameter_create_care_circle_button"
  const val SWITCH_CARE_CIRCLE_BUTTON = "caregiver_parameter_switch_care_circle_button"
  const val CREATE_INVITATION_BUTTON = "caregiver_parameter_create_invitation_button"
  const val ENTER_INVITATION_BUTTON = "caregiver_parameter_enter_invitation_button"
  const val SIGN_OUT_BUTTON = "caregiver_parameter_sign_out_button"
  const val DELETE_ACCOUNT_BUTTON = "caregiver_parameter_delete_account_button"
  const val INVITATION_HINT = "caregiver_parameter_invitation_hint"
}

/**
 * Displays the caregiver parameters screen.
 *
 * Invitation actions stay on this screen. Sign-out is confirmed on a shared screen reached through
 * [onSignOutClick].
 *
 * @param viewModel ViewModel providing the UI state of the screen.
 * @param onTabSelected Callback invoked when the user selects a bottom navigation tab.
 * @param onCreateCareCircleClick Callback invoked when the user creates a new care circle.
 * @param onSwitchCareCircleClick Callback invoked when the user opens care circle selection.
 * @param onCreateInvitationClick Callback invoked when the user creates an invitation.
 * @param onEnterInvitationClick Callback invoked when the user enters an invitation.
 * @param onSignOutClick Callback invoked when the user opens the sign-out confirmation screen.
 * @param onDeleteAccountClick Callback invoked when the user opens account deletion.
 */
@Composable
fun CaregiverParameterScreen(
    viewModel: CaregiverParameterViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
    onCreateCareCircleClick: () -> Unit = {},
    onSwitchCareCircleClick: () -> Unit = {},
    onCreateInvitationClick: () -> Unit = {},
    onEnterInvitationClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current
  val createCareCircleLabel = stringResource(R.string.caregiverParameterCreateCareCircle)
  val switchCareCircleLabel = stringResource(R.string.caregiverParameterSwitchCareCircle)
  val createInvitationLabel = stringResource(R.string.caregiverParameterCreateInvitation)
  val enterInvitationLabel = stringResource(R.string.caregiverParameterEnterInvitation)
  val signOutLabel = stringResource(R.string.caregiverParameterSignOut)
  val deleteAccountLabel = stringResource(R.string.caregiverParameterDeleteAccount)

  LaunchedEffect(uiState.errorMsgRes) {
    uiState.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        AmberTopBar {
          DisplayCaregiverTitle(
              modifier = Modifier.testTag(CaregiverParametersTestTags.TITLE),
              title = stringResource(R.string.caregiverParameterTitle),
          )
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Setting,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(CAREGIVER_PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
        ) {
          // Each row names its action next to its icon, so the icon needs no description.
          // The care circle and its invitations
          ParameterRow(
              testTag = CaregiverParametersTestTags.CREATE_CARE_CIRCLE_BUTTON,
              icon = Icons.Outlined.Add,
              text = createCareCircleLabel,
              onClick = onCreateCareCircleClick,
          )
          ParameterRow(
              testTag = CaregiverParametersTestTags.CREATE_INVITATION_BUTTON,
              icon = Icons.Outlined.PersonAdd,
              text = createInvitationLabel,
              onClick = onCreateInvitationClick,
          )
          ParameterRow(
              testTag = CaregiverParametersTestTags.ENTER_INVITATION_BUTTON,
              icon = Icons.Outlined.Mail,
              text = enterInvitationLabel,
              onClick = onEnterInvitationClick,
          )
          ParameterRow(
              testTag = CaregiverParametersTestTags.SWITCH_CARE_CIRCLE_BUTTON,
              icon = Icons.Outlined.SwapHoriz,
              text = switchCareCircleLabel,
              onClick = onSwitchCareCircleClick,
          )

          // The account, set apart from the care circle
          ParameterRow(
              modifier = Modifier.padding(top = CAREGIVER_PADDING_MEDIUM),
              testTag = CaregiverParametersTestTags.SIGN_OUT_BUTTON,
              icon = Icons.AutoMirrored.Outlined.Logout,
              text = signOutLabel,
              onClick = onSignOutClick,
          )
          ParameterRow(
              testTag = CaregiverParametersTestTags.DELETE_ACCOUNT_BUTTON,
              icon = Icons.Outlined.Delete,
              text = deleteAccountLabel,
              isDestructive = true,
              onClick = onDeleteAccountClick,
          )

          // Tells a caregiver how to add someone else: it names the Create Invitation button on
          // this screen and says to send the code it creates.
          Text(
              text =
                  stringResource(R.string.caregiverParameterInvitationHint, createInvitationLabel),
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(vertical = CAREGIVER_PADDING_MEDIUM)
                      .testTag(CaregiverParametersTestTags.INVITATION_HINT),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
      },
  )
}

/**
 * One action of the parameters screen: a [DisplayBoxWithIconText] row carrying [testTag].
 *
 * @param modifier modifier applied to the row, before its test tag.
 * @param testTag test tag of the row.
 * @param icon icon of the action.
 * @param text name of the action.
 * @param isDestructive whether the action cannot be undone. Its icon is then in the error colors.
 * @param onClick called when the row is pressed.
 */
@Composable
private fun ParameterRow(
    modifier: Modifier = Modifier,
    testTag: String = "",
    icon: ImageVector = Icons.Outlined.Add,
    text: String = "",
    isDestructive: Boolean = false,
    onClick: () -> Unit = {},
) {
  val colorScheme = MaterialTheme.colorScheme
  DisplayBoxWithIconText(
      modifier = modifier.testTag(testTag),
      icon = icon,
      text = text,
      style = MaterialTheme.typography.titleMedium,
      iconContainerColor =
          if (isDestructive) colorScheme.errorContainer else colorScheme.secondaryContainer,
      iconColor =
          if (isDestructive) colorScheme.onErrorContainer else colorScheme.onSecondaryContainer,
      onClick = onClick,
  )
}

/** Preview of a regular and a destructive [ParameterRow]. */
@Preview(showBackground = true)
@Composable
private fun ParameterRowPreview() {
  AmberAppTheme(dynamicColor = false) {
    Column(
        modifier = Modifier.padding(CAREGIVER_PADDING_MEDIUM),
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
    ) {
      ParameterRow(text = "Create a care circle")
      ParameterRow(icon = Icons.Outlined.Delete, text = "Delete account", isDestructive = true)
    }
  }
}

/** Preview of [CaregiverParameterScreen], including the create-care-circle button. */
@Preview(name = "Caregiver parameters", showBackground = true, showSystemUi = true)
@Composable
internal fun CaregiverParameterScreenPreview() {
  val viewModel = remember { CaregiverParameterViewModel() }
  AmberAppTheme(dynamicColor = false) { CaregiverParameterScreen(viewModel = viewModel) }
}
