// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.guidance

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientScreen
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientDialog
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Guides the patient step by step to a place that matters to them.
 *
 * Shows what [viewModel]'s [GuidancePhase] is: the places to choose from, the route being found, a
 * dialog to confirm before leaving, the next step with the route on a map, arrival, or what went
 * wrong. While guiding, Stop, the top back arrow and the system back all ask first, so a tap by
 * mistake doesn't end the guidance; stopping goes back to the places.
 *
 * @param viewModel the guidance, which follows the patient's position.
 * @param selectedTab the bottom tab to highlight: the one of the screen that opened guidance.
 * @param onBack called after the patient leaves the screen, guidance stopped.
 * @param onTabSelected called when the patient selects a bottom navigation tab.
 * @param routeMap draws the route while guiding, following the patient, keeping the padding clear
 *   of what covers it; a map by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidanceScreen(
    viewModel: GuidanceViewModel = viewModel(),
    selectedTab: Tab = PatientTab.Map,
    onBack: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
    routeMap: @Composable (Modifier, GuidancePhase.Guiding, PaddingValues) -> Unit =
        { modifier, guiding, padding ->
          GuidanceRouteMap(modifier, guiding.route, guiding.position, padding)
        },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val phase = uiState.phase
  var isStopDialogOpen by rememberSaveable { mutableStateOf(false) }
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Tries again whatever was answered: without the permission, the problem shows again
        viewModel.retry()
      }
  val leave = {
    viewModel.stop()
    onBack()
  }
  val askOrLeave = { if (phase is GuidancePhase.Guiding) isStopDialogOpen = true else leave() }
  BackHandler(enabled = phase is GuidancePhase.Guiding) { isStopDialogOpen = true }

  Scaffold(
      modifier = Modifier.testTag(GuidanceTestTags.SCREEN),
      topBar = {
        TopAppBar(
            title = {
              Column {
                TopNavigationMenu(
                    modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
                    action = TopNavigationAction.CANCEL,
                    onBackClick = askOrLeave,
                )
                // While guiding, the map gets the space: the step card says where they're going
                if (phase !is GuidancePhase.Guiding) {
                  DisplayPatientTitle(title = stringResource(R.string.guidanceGoSomewhere))
                }
              }
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
  ) { paddingValues ->
    // Phases other than guiding: centered when they fit, scrolled when they don't
    val centered: @Composable (@Composable ColumnScope.() -> Unit) -> Unit = { content ->
      Box(
          Modifier.fillMaxSize().padding(paddingValues).padding(PATIENT_PADDING_SMALL),
          contentAlignment = Alignment.Center,
      ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
            content = content,
        )
      }
    }
    when (phase) {
      is GuidancePhase.Guiding ->
          // The map fills the screen, the step and Stop float over it, as on the map screen
          Box(Modifier.fillMaxSize().padding(paddingValues)) {
            val density = LocalDensity.current
            var stepHeight by remember { mutableStateOf(0.dp) }
            var stopHeight by remember { mutableStateOf(0.dp) }
            routeMap(
                Modifier.fillMaxSize(),
                phase,
                PaddingValues(top = stepHeight, bottom = stopHeight),
            )
            GuidanceStepCard(
                // Measured height includes the card's outer padding
                Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { stepHeight = with(density) { it.height.toDp() } }
                    .padding(PATIENT_PADDING_SMALL),
                phase,
            )
            StopButton(
                Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { stopHeight = with(density) { it.height.toDp() } }
                    .padding(PATIENT_PADDING_SMALL),
                onClick = { isStopDialogOpen = true },
            )
          }
      // The confirm dialog shows over the places, which stay where they were
      GuidancePhase.Choosing,
      is GuidancePhase.Confirming ->
          centered {
            GuidanceChoosing(
                state = uiState,
                onQueryChange = viewModel::setSearchQuery,
                onSearch = viewModel::search,
                onChoose = viewModel::chooseDestination,
            )
          }
      is GuidancePhase.LoadingRoute ->
          centered {
            GuidanceLoading(phase.destination.name)
            StopButton(onClick = viewModel::stop)
          }
      is GuidancePhase.Arrived ->
          centered { GuidanceArrived(phase.destination.name, onDone = leave) }
      is GuidancePhase.Problem ->
          centered {
            GuidanceProblemContent(
                problem = phase.problem,
                onAction = {
                  if (phase.problem == GuidanceProblem.NO_PERMISSION) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                  } else {
                    viewModel.retry()
                  }
                },
                onChooseAnother = viewModel::stop,
            )
          }
    }
  }

  if (phase is GuidancePhase.Confirming) {
    // The walk time in whole minutes, rounded up
    val walkMinutes = ceil(phase.route.durationSeconds.seconds / 1.minutes).toInt().coerceAtLeast(1)
    DisplayPatientDialog(
        title = stringResource(R.string.guidanceConfirmTitle, phase.destination.name),
        message = pluralStringResource(R.plurals.guidanceWalkMinutes, walkMinutes, walkMinutes),
        // The address of a searched place, to check it's the right one
        detail = phase.destination.address.orEmpty(),
        confirmText = stringResource(R.string.guidanceGo),
        dismissText = stringResource(R.string.guidanceCancel),
        onConfirm = viewModel::startGuidance,
        onDismiss = viewModel::stop,
        testTag = GuidanceTestTags.CONFIRM_DIALOG,
    )
  }
  // Closes by itself if the guidance ended meanwhile, for example on arrival
  if (isStopDialogOpen && phase is GuidancePhase.Guiding) {
    DisplayPatientDialog(
        title = stringResource(R.string.guidanceStopTitle),
        confirmText = stringResource(R.string.guidanceStop),
        dismissText = stringResource(R.string.guidanceKeepGoing),
        onConfirm = {
          isStopDialogOpen = false
          viewModel.stop()
        },
        onDismiss = { isStopDialogOpen = false },
        testTag = GuidanceTestTags.STOP_DIALOG,
    )
  }
}

/**
 * The bottom tab to highlight on the guidance screen: Home when guidance was opened from Home, the
 * map otherwise, since the map is the only other way in.
 *
 * @param previousRoute the route of the screen that opened guidance.
 */
fun guidanceTabFor(previousRoute: String = ""): PatientTab =
    if (previousRoute == PatientScreen.Home.route) PatientTab.Home else PatientTab.Map

/** The big Stop button, outlined so it isn't mistaken for the main action. */
@Composable
private fun StopButton(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
  DisplayPatientButton(
      modifier = modifier,
      buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
      outlined = true,
      text = stringResource(R.string.guidanceStop),
      onClick = onClick,
      testTag = GuidanceTestTags.STOP,
  )
}

/** Preview of [GuidanceScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun GuidanceScreenPreview() {
  AmberAppTheme(dynamicColor = false) { GuidanceScreen() }
}
