// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

/** Stable tag used by UI tests to locate the Google Maps view. */
object MapScreenTestTags {
  const val GOOGLE_MAP = "map_google_map"
}

private const val INITIAL_LATITUDE = 46.5197
private const val INITIAL_LONGITUDE = 6.6323
private const val INITIAL_ZOOM = 14f

/**
 * Patient map centered on the first available device position.
 *
 * This composable handles Android's permission launcher and Google Maps camera.
 *
 * @param modifier modifier applied to the screen
 * @param onTabSelected Called when the user selects a patient navigation tab.
 * @param onGoSomewhere Called when the user wants to be guided somewhere.
 * @param cameraPositionState Camera state shared with the map; callers can observe camera moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    onTabSelected: (Tab) -> Unit = {},
    onGoSomewhere: () -> Unit = {},
    viewModel: MediaMapViewModel = viewModel(),
    // Lausanne is the fallback while GPS is searching or location access is unavailable.
    cameraPositionState: CameraPositionState = rememberCameraPositionState {
      position =
          CameraPosition.fromLatLngZoom(
              LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE),
              INITIAL_ZOOM,
          )
    },
) {
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val locationState by viewModel.locationState.collectAsStateWithLifecycle()
  val userPosition = (locationState as? MediaMapLocationState.Located)?.position
  var centeredOnFreshPosition by rememberSaveable { mutableStateOf(false) }
  val cameraScope = rememberCoroutineScope()

  // Create an Android permission launcher (this does not show the dialog yet)
  // RequestMultiplePermission() lets the screen ask for both precise and approximate user location
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // After the permission dialog closes, ViewModel checks what the user granted and
        // updates locationState
        viewModel.refreshPermission()
      }

  // Check permissions when this screen enters composition, not on every recomposition.
  LaunchedEffect(Unit) {
    viewModel.refreshPermission()
    if (viewModel.locationState.value == MediaMapLocationState.NoPermission) {
      // Launch the permission dialog
      permissionLauncher.launch(
          arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION, // Precise location
              Manifest.permission.ACCESS_COARSE_LOCATION, // Approximate location
          )
      )
    }
  }

  // When a fresh GPS position arrives, map centers it again to correct it
  // A last-known fix can center the map immediately; a fresh fix replaces it once. After that,
  // automatic camera moves stop so location updates do not override the user's map gestures
  LaunchedEffect(userPosition) { // Runs when the reported position changes
    // If there is a position and the user has not centered on a fresh one yet
    val step = autoCenterStep(userPosition, centeredOnFreshPosition)
    step.moveTo?.let { position ->
      cameraPositionState.position =
          CameraPosition.fromLatLngZoom(
              LatLng(position.lat, position.lng),
              INITIAL_ZOOM,
          )
    }
    // Marks the job done only when the position is fresh
    centeredOnFreshPosition = step.centeredOnFresh
  }

  Scaffold(
      modifier = modifier,
      topBar = {
        TopAppBar(
            title = { DisplayPatientTitle(title = stringResource(R.string.screenTitlePatientMap)) }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
            selectedTab = PatientTab.Map,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
        )
      },
  ) { paddingValues ->
    Box(Modifier.fillMaxSize().padding(paddingValues)) {
      // Extract card height to adapt the position card size
      val density = LocalDensity.current
      var cardHeight by remember { mutableStateOf<Dp>(0.dp) }

      GoogleMap(
          modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.GOOGLE_MAP),
          cameraPositionState = cameraPositionState,
          properties = MapProperties(isMyLocationEnabled = userPosition != null),
          // Google's own location button is off: MapRecenterButton replaces it, and can also ask
          // for the permission
          uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
          // Reserve space for the position card and the recenter button, so Google's zoom
          // controls sit above them
          contentPadding =
              PaddingValues(
                  end = PATIENT_PADDING_MINI,
                  bottom = cardHeight + PATIENT_BUTTON_HEIGHT_SMALL + PATIENT_PADDING_SMALL,
              ),
      )
      MapSearchField(
          modifier = Modifier.align(Alignment.TopCenter).padding(PATIENT_PADDING_SMALL),
          query = searchQuery,
          onQueryChange = viewModel::updateSearchQuery,
      )
      MapPositionCard(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  // Measured height includes the card's outer padding
                  .onSizeChanged { cardHeight = with(density) { it.height.toDp() } }
                  .padding(PATIENT_PADDING_SMALL),
          locationState = locationState,
          onGoSomewhere = onGoSomewhere,
      )
      MapRecenterButton(
          // On the right side, just above the position card
          modifier =
              Modifier.align(Alignment.BottomEnd)
                  .padding(end = PATIENT_PADDING_SMALL, bottom = cardHeight),
          onRecenter = {
            // The ViewModel chooses an action; the UI performs the permission or camera API call
            when (val action = viewModel.recenter()) {
              // Ask for permission
              MapRecenterAction.RequestPermission ->
                  permissionLauncher.launch(
                      arrayOf(
                          Manifest.permission.ACCESS_FINE_LOCATION,
                          Manifest.permission.ACCESS_COARSE_LOCATION,
                      )
                  )
              is MapRecenterAction.MoveTo ->
                  cameraScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLng(
                            LatLng(action.position.lat, action.position.lng)
                        )
                    )
                  }
              MapRecenterAction.PositionUnavailable -> Unit
            }
          },
      )
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun MapScreenPreview() {
  AmberAppTheme { MapScreen() }
}
