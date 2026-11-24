// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState

object MapScreenTestTags {
  const val GOOGLE_MAP = "map_google_map"
}

private const val INITIAL_LATITUDE = 46.5197
private const val INITIAL_LONGITUDE = 6.6323
private const val INITIAL_ZOOM = 14f

/** Basic patient map layout; map interactions and media pins belong to a later change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(modifier: Modifier = Modifier, onTabSelected: (Tab) -> Unit = {}) {
  var searchQuery by remember { mutableStateOf("") }

  // Set initial camera position
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(
        LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE), INITIAL_ZOOM)
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
      )
      MapSearchField(
          modifier = Modifier.align(Alignment.TopCenter).padding(PATIENT_PADDING_SMALL),
          query = searchQuery,
          onQueryChange = { searchQuery = it },
      )
      MapPositionCard(
          modifier = Modifier.align(Alignment.BottomCenter).padding(PATIENT_PADDING_SMALL),
      )
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MapScreenPreview() {
  AmberAppTheme { MapScreen() }
}
