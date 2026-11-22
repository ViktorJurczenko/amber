// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified with assistance from GitHub Copilot.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.AmberTopBar
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM

/** No-op [ProfileRepository] used by Compose previews (and preview-driven tests). */
private object PreviewPatientHomeProfileRepository : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
      Result.success(Unit)

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> =
      Result.success(Unit)

  override suspend fun getCurrentProfile(): Result<UserProfile?> = Result.success(null)

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(null)
}

object PatientHomeTestTags {
  const val GREETINGS = "patient_home_greetings"
  const val TITLE = "patient_home_title"
}

/**
 * Displays the patient home screen.
 *
 * Sign-out is available from the parameters screen.
 *
 * @param homeViewModel ViewModel providing the patient profile displayed on the screen.
 * @param onTabSelected Callback invoked when the user selects a bottom navigation tab.
 */
@Composable
fun PatientHomeScreen(
    homeViewModel: PatientHomeViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

  // Show error message
  LaunchedEffect(uiState.errorMsgRes) {
    uiState.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      homeViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        AmberTopBar { DisplayPatientTitle(title = stringResource(R.string.patientHomeTitle)) }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.Home,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          val firstName = uiState.patientProfile.person.firstName
          // The greeting is the first thing the patient reads: one large, calm, tinted card.
          Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.extraLarge,
              color = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Text(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(PATIENT_PADDING_BIG)
                        .testTag(PatientHomeTestTags.GREETINGS),
                text = stringResource(R.string.patientHomeGreeting, firstName),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
            )
          }
        }
      },
  )
}

private val previewPatientHomeViewModel = PatientHomeViewModel(PreviewPatientHomeProfileRepository)

/** Preview of [PatientHomeScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun PatientHomeScreenPreview() {
  AmberAppTheme(dynamicColor = false) {
    PatientHomeScreen(homeViewModel = previewPatientHomeViewModel)
  }
}
