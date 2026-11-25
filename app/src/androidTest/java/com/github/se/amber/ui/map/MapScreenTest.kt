// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.connectivity.ConnectivityMonitor
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.LocationUpdatesRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import io.mockk.mockk
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Screen checks use local repositories; the map tiles themselves are outside these assertions. */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  private fun cameraIsNear(camera: CameraPositionState, position: DevicePosition): Boolean =
      abs(camera.position.target.latitude - position.lat) < 0.0001 &&
          abs(camera.position.target.longitude - position.lng) < 0.0001

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule
  val locationPermissionRule =
      GrantPermissionRule.grant(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
      )

  /** Supplies controlled location and permission states without starting device services. */
  private fun createViewModel(
      updates: Flow<LocationUpdate> = MutableStateFlow(LocationUpdate.Searching),
      permissionStatus: () -> Boolean = { true },
  ): MediaMapViewModel {
    val profiles =
        object : ProfileRepository {
          override suspend fun createProfile(person: Person, role: Role) = Result.success(Unit)

          override suspend fun updateOnboardingProfile(person: Person, role: Role) =
              Result.success(Unit)

          override suspend fun getCurrentProfile(): Result<UserProfile?> = Result.success(null)

          override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(null)
        }
    val connectivity =
        object : ConnectivityMonitor {
          override val isOnline = MutableStateFlow(true)
          override val backOnline = emptyFlow<Unit>()
        }
    val location =
        object : LocationUpdatesRepository {
          override fun hasPermission() = permissionStatus()

          override fun isLocationEnabled() = true

          override fun observeLocation() = updates
        }
    return MediaMapViewModel(
        mediaLocationRepository = { flowOf(emptyList()) },
        profileRepository = profiles,
        activeCircleRepository = mockk<ActiveCircleRepository>(),
        connectivityMonitor = connectivity,
        locationUpdatesRepository = location,
    )
  }

  @Test
  fun mapDisplaysControlsAndRoutesSearchAndTabActions() {
    val viewModel = createViewModel()
    var chosenTab: Tab? = null
    composeTestRule.setContent {
      AmberAppTheme { MapScreen(viewModel = viewModel, onTabSelected = { chosenTab = it }) }
    }

    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.screenTitlePatientMap))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapTestTags.POSITION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Map))
        .assertIsSelected()

    composeTestRule.onNodeWithTag(MapTestTags.SEARCH).performTextInput("Lausanne")
    assertEquals("Lausanne", viewModel.searchQuery.value)
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()
    assertEquals(PatientTab.Home, chosenTab)
  }

  @Test
  fun mapPreviewDisplaysMapAndPatientNavigation() {
    composeTestRule.setContent { MapScreenPreview() }

    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Map))
        .assertIsSelected()
  }

  @Test
  fun locationChangesKeepRecenterAvailable() {
    val updates = MutableStateFlow<LocationUpdate>(LocationUpdate.Searching)
    val viewModel = createViewModel(updates)
    val cameraPositionState =
        CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f))
    composeTestRule.setContent {
      AmberAppTheme { MapScreen(viewModel = viewModel, cameraPositionState = cameraPositionState) }
    }
    val recenter =
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.patientMapRecenter)
        )

    recenter.performClick()
    assertEquals(MediaMapLocationState.Searching, viewModel.locationState.value)

    updates.value = LocationUpdate.LocationOff
    composeTestRule.waitUntil(5_000) {
      viewModel.locationState.value == MediaMapLocationState.LocationOff
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLocationOff))
        .assertIsDisplayed()
    recenter.performClick()

    val lastKnown = DevicePosition(46.52, 6.63, isLastKnown = true)
    updates.value = LocationUpdate.Position(lastKnown)
    composeTestRule.waitUntil(5_000) {
      (viewModel.locationState.value as? MediaMapLocationState.Located)?.position == lastKnown
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLastKnown))
        .assertIsDisplayed()
    composeTestRule.waitUntil(5_000) { cameraIsNear(cameraPositionState, lastKnown) }

    val fresh = DevicePosition(46.53, 6.64)
    updates.value = LocationUpdate.Position(fresh)
    composeTestRule.waitUntil(5_000) {
      (viewModel.locationState.value as? MediaMapLocationState.Located)?.position == fresh
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLocationFound))
        .assertIsDisplayed()
    composeTestRule.waitUntil(5_000) { cameraIsNear(cameraPositionState, fresh) }
    updates.value = LocationUpdate.Position(DevicePosition(46.54, 6.65))
    composeTestRule.waitUntil(5_000) {
      (viewModel.locationState.value as? MediaMapLocationState.Located)?.position?.lat == 46.54
    }
    composeTestRule.waitForIdle()
    assertEquals(true, cameraIsNear(cameraPositionState, fresh))
    recenter.performClick()
  }

  @Test
  fun permissionLossAndGpsFailureKeepTheMapVisible() {
    var permissionReported = false
    val updates =
        flow<LocationUpdate> {
          emit(LocationUpdate.Searching)
          throw IllegalStateException()
        }
    val viewModel = createViewModel(updates, permissionStatus = { permissionReported })
    composeTestRule.setContent { AmberAppTheme { MapScreen(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapPermissionNeeded))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapRecenter))
        .performClick()
    assertEquals(MediaMapLocationState.NoPermission, viewModel.locationState.value)

    permissionReported = true
    viewModel.refreshPermission()
    composeTestRule.waitUntil(5_000) {
      viewModel.locationState.value == MediaMapLocationState.Unavailable
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLocationUnavailable))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapRecenter))
        .performClick()
    assertEquals(MediaMapLocationState.Unavailable, viewModel.locationState.value)
  }
}
