// Written by MeiliV, with assistance from OpenAI Codex.

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

object MapScreenTestTags {
  const val GOOGLE_MAP = "map_google_map"
}

private const val INITIAL_LATITUDE = 46.5197
private const val INITIAL_LONGITUDE = 6.6323
private const val INITIAL_ZOOM = 14f

/** Patient map centered on the first available device position; media pins come later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    onTabSelected: (Tab) -> Unit = {},
    viewModel: MediaMapViewModel = viewModel(),
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

  // Lausanne is a fallback while GPS is searching or location access is unavailable
  val cameraPositionState = rememberCameraPositionState {
    position =
        CameraPosition.fromLatLngZoom(
            LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE),
            INITIAL_ZOOM,
        )
  }

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
  LaunchedEffect(userPosition) { // Runs when the reported position changes
    // If there is a position and the user has not centered on a fresh one yet
    if (userPosition != null && !centeredOnFreshPosition) {
      cameraPositionState.position =
          CameraPosition.fromLatLngZoom(
              LatLng(userPosition.lat, userPosition.lng),
              INITIAL_ZOOM,
          )

      // Marks the job done only when the position is fresh
      if (!userPosition.isLastKnown) centeredOnFreshPosition = true
    }
  }

  Scaffold(
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
    Box(modifier.fillMaxSize().padding(paddingValues)) {
      GoogleMap(
          modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.GOOGLE_MAP),
          cameraPositionState = cameraPositionState,
          properties = MapProperties(isMyLocationEnabled = userPosition != null),
          uiSettings = MapUiSettings(zoomControlsEnabled = true),
          contentPadding =
              PaddingValues(
                  end = PATIENT_PADDING_MINI,
                  bottom = PATIENT_PADDING_ENORMOUS + PATIENT_PADDING_BIG + PATIENT_PADDING_SMALL,
              ),
      )
      MapSearchField(
          modifier = Modifier.align(Alignment.TopCenter).padding(PATIENT_PADDING_SMALL),
          query = searchQuery,
          onQueryChange = viewModel::updateSearchQuery,
      )
      MapPositionCard(
          modifier = Modifier.align(Alignment.BottomCenter).padding(PATIENT_PADDING_SMALL),
          locationState = locationState,
          onRecenter = {
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
private fun MapScreenPreview() {
  AmberAppTheme { MapScreen() }
}
