// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.patient.parameters

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.AmberTopBar
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM

/** Test tags of [PatientParameterScreen]. */
object PatientParameterTestTags {
  const val TITLE = "patient_parameter_title"
  const val SIGN_OUT_BUTTON = "patient_parameter_sign_out_button"
  const val DELETE_BUTTON = "patient_parameter_delete_button"
}

/**
 * Patient parameters screen.
 *
 * For now it only exposes navigation to the shared sign-out confirmation screen.
 *
 * @param viewModel ViewModel providing the UI state of the screen.
 * @param onTabSelected Callback invoked when the user selects a bottom navigation tab.
 * @param onSignOutClick Callback invoked when the user chooses to open the sign-out screen.
 * @param onDeleteAccountClick Callback invoked when the user chooses to open the delete screen.
 */
@Composable
fun PatientParameterScreen(
    viewModel: PatientParameterViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

  LaunchedEffect(uiState.errorMsgRes) {
    uiState.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        AmberTopBar {
          DisplayPatientTitle(
              title = stringResource(R.string.patientParameterTitle),
              testTag = PatientParameterTestTags.TITLE,
          )
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.Setting,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(PATIENT_PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MEDIUM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Neither action is the reason to come to this screen, and both leave the app: they are
          // outlined, so that none of them invites a press. Each icon is next to its label.
          DisplayPatientButton(
              buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
              outlined = true,
              text = stringResource(R.string.patientParameterSignOut),
              icon = Icons.AutoMirrored.Outlined.Logout,
              onClick = onSignOutClick,
              testTag = PatientParameterTestTags.SIGN_OUT_BUTTON,
          )
          DisplayPatientButton(
              buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
              outlined = true,
              text = stringResource(R.string.patientParameterDeleteAccount),
              icon = Icons.Outlined.Delete,
              onClick = onDeleteAccountClick,
              testTag = PatientParameterTestTags.DELETE_BUTTON,
          )
        }
      },
  )
}

/** Preview of [PatientParameterScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun PatientParameterScreenPreview() {
  AmberAppTheme(dynamicColor = false) { PatientParameterScreen() }
}
