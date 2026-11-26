// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.connectivity.ConnectivityMonitor
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.LocationUpdatesRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Screen checks use local repositories; the map tiles themselves are outside these assertions. */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule
  val locationPermissionRule =
      GrantPermissionRule.grant(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
      )

  private val place = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne")

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
        placeSearchRepository = { Result.success(listOf(place)) },
    )
  }

  @Test
  fun mapDisplaysControlsAndRoutesSearchAndTabActions() {
    // Render the screen with local repositories and replaceable model/camera state
    val viewModel = createViewModel()
    val alternateViewModel = createViewModel()
    val useAlternateCamera = mutableStateOf(false)
    val firstCamera = CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f))
    val secondCamera = CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f))
    var chosenTab: Tab? = null
    composeTestRule.setContent {
      AmberAppTheme {
        MapScreen(
            modifier =
                Modifier.testTag(if (useAlternateCamera.value) "map_second" else "map_first"),
            viewModel = if (useAlternateCamera.value) alternateViewModel else viewModel,
            onTabSelected = { chosenTab = it },
            cameraPositionState = if (useAlternateCamera.value) secondCamera else firstCamera,
        )
      }
    }

    // Assert: the map, position card, title, and selected navigation tab are visible.
    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.screenTitlePatientMap))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapTestTags.POSITION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Map))
        .assertIsSelected()

    // Act and assert: selecting a search suggestion updates the query and closes the popup.
    val search = composeTestRule.onNodeWithTag(MapTestTags.SEARCH)
    search.performTextInput("Laus")
    composeTestRule.waitUntil(TIME_OUT) {
      viewModel.placeSearchState.value.suggestions.isNotEmpty()
    }
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapTestTags.SUGGESTION).performClick()
    search.assertTextEquals("Lausanne")
    assertEquals(place, viewModel.placeSearchState.value.selectedPlace)
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()

    // Act and assert: replacing the model/camera preserves the map and navigation callbacks.
    composeTestRule.runOnIdle { useAlternateCamera.value = true }
    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()
    assertEquals(PatientTab.Home, chosenTab)
  }

  @Test
  fun mapPreviewDisplaysMapAndPatientNavigation() {
    // Arrange: start with the default preview, then supply a model with an existing query.
    val suppliedModel = createViewModel().apply { updateSearchQuery("La") }
    val useSuppliedModel = mutableStateOf(false)
    composeTestRule.setContent {
      if (useSuppliedModel.value) MapScreenPreview(viewModel = suppliedModel)
      else MapScreenPreview()
    }

    // Assert: the default preview renders the map and selects the patient map tab.
    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Map))
        .assertIsSelected()
    // Act and assert: the preview reflects the supplied model's query after replacement.
    composeTestRule.runOnIdle { useSuppliedModel.value = true }
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH).assertTextEquals("La")
  }

  @Test
  fun locationFixesUpdateTheMapAndRecenterUsesTheFreshPosition() {
    // Arrange: control location emissions and camera replacement independently of device GPS.
    val updates = MutableStateFlow<LocationUpdate>(LocationUpdate.Searching)
    val viewModel = createViewModel(updates)
    val useSecondCamera = mutableStateOf(false)
    val firstCamera = CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f))
    val secondCamera = CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f))
    composeTestRule.setContent {
      AmberAppTheme {
        MapScreen(
            viewModel = viewModel,
            cameraPositionState = if (useSecondCamera.value) secondCamera else firstCamera,
        )
      }
    }

    // No fix yet: recenter leaves the screen searching; a disabled provider updates the card.
    composeTestRule.onNodeWithText(text(R.string.patientMapRecenter)).performClick()
    assertEquals(MediaMapLocationState.Searching, viewModel.locationState.value)

    updates.value = LocationUpdate.LocationOff
    composeTestRule.waitUntil(5_000) {
      viewModel.locationState.value == MediaMapLocationState.LocationOff
    }
    waitForCameraAttachment(firstCamera)

    // Last-known fix: show its status and center the first attached camera.
    val lastKnown = DevicePosition(46.52, 6.63, isLastKnown = true)
    updates.value = LocationUpdate.Position(lastKnown)
    composeTestRule.waitUntil(5_000) {
      (viewModel.locationState.value as? MediaMapLocationState.Located)?.position == lastKnown
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLastKnown))
        .assertIsDisplayed()
    waitForCameraTarget(firstCamera, lastKnown)

    // The next fresh fix should center the camera currently attached to the map.
    composeTestRule.runOnIdle { useSecondCamera.value = true }
    waitForCameraAttachment(secondCamera)

    // First fresh fix: update the status and center the replacement camera once.
    val fresh = DevicePosition(46.53, 6.64)
    updates.value = LocationUpdate.Position(fresh)
    composeTestRule.waitUntil(5_000) {
      (viewModel.locationState.value as? MediaMapLocationState.Located)?.position == fresh
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLocationFound))
        .assertIsDisplayed()
    waitForCameraTarget(secondCamera, fresh)

    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()

    // Later fix: update location state while leaving the camera at the first fresh fix.
    val later = DevicePosition(46.54, 6.65)
    updates.value = LocationUpdate.Position(later)
    composeTestRule.waitUntil(5_000) {
      (viewModel.locationState.value as? MediaMapLocationState.Located)?.position == later
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.patientMapLocationFound))
        .assertIsDisplayed()
    composeTestRule.waitForIdle()
    waitForCameraTarget(secondCamera, fresh)

    // Recenter must use the latest fix even after automatic following has stopped.
    composeTestRule.runOnIdle {
      secondCamera.position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f)
    }
    waitForCameraTarget(secondCamera, DevicePosition(0.0, 0.0))
    composeTestRule.onNodeWithText(text(R.string.patientMapRecenter)).performClick()
    waitForCameraTarget(secondCamera, later)

    // Search with no suggestions: the IME action must leave the selection empty.
    val search = composeTestRule.onNodeWithTag(MapTestTags.SEARCH)
    search.performTextInput("La")
    search.performImeAction()
    assertNull(viewModel.placeSearchState.value.selectedPlace)

    // Search with a suggestion: the IME action selects it and dismisses the popup.
    search.performTextInput("usanne")
    composeTestRule.waitUntil(5_000) { viewModel.placeSearchState.value.suggestions.isNotEmpty() }
    search.performImeAction()
    composeTestRule.waitUntil(5_000) { viewModel.placeSearchState.value.selectedPlace == place }
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()
  }

  /**
   * Maps attaches asynchronously, outside Compose's idling. Before attachment, position reads only
   * return cached requests and cannot confirm that the native map has processed a camera move.
   * Projection is available as soon as the camera is bound; this does not wait for network tiles.
   */
  private fun waitForCameraAttachment(camera: CameraPositionState) {
    composeTestRule.waitUntil(MAP_READY_TIMEOUT) {
      composeTestRule.runOnUiThread { camera.projection != null }
    }
    composeTestRule.waitForIdle()
  }

  /** Read native camera state on the UI thread and allow Maps' coordinate rounding. */
  private fun waitForCameraTarget(camera: CameraPositionState, expected: DevicePosition) {
    composeTestRule.waitUntil(TIME_OUT) {
      composeTestRule.runOnUiThread {
        val target = camera.position.target
        !camera.isMoving &&
            abs(target.latitude - expected.lat) < 0.0001 &&
            abs(target.longitude - expected.lng) < 0.0001
      }
    }
  }

  // ActivityResultRegistry that answers deterministically and counts requests
  private val denied =
      mapOf(
          Manifest.permission.ACCESS_FINE_LOCATION to false,
          Manifest.permission.ACCESS_COARSE_LOCATION to false,
      )

  /** Answers every permission request with [result] and counts the requests. */
  private class FakePermissionRegistryOwner(private val result: Map<String, Boolean>) :
      ActivityResultRegistryOwner {
    val requestCount = AtomicInteger(0)

    override val activityResultRegistry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            requestCount.incrementAndGet()
            @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, result as O)
          }
        }
  }

  private fun showMap(viewModel: MediaMapViewModel, registry: ActivityResultRegistryOwner) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
        AmberAppTheme { MapScreen(viewModel = viewModel) }
      }
    }
  }

  private fun text(id: Int) = composeTestRule.activity.getString(id)

  private val TIME_OUT = 5_000L
  private val MAP_READY_TIMEOUT = 30_000L

  @Test
  fun withoutPermissionTheCardAsksToAllowLocation_andTheMapStaysVisible() {
    // Arrange: deny location access without displaying a real system permission dialog.
    showMap(createViewModel(permissionStatus = { false }), FakePermissionRegistryOwner(denied))

    // Assert: permission guidance is visible alongside the map.
    composeTestRule.onNodeWithText(text(R.string.patientMapPermissionNeeded)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
  }

  @Test
  fun withoutPermissionTheRequestIsLaunchedOnceOnEntry_and_TappingRecenterWithoutPermissionAsksAgain() {
    // Arrange: record permission requests while consistently denying access.
    val registry = FakePermissionRegistryOwner(denied)
    showMap(createViewModel(permissionStatus = { false }), registry)
    composeTestRule.waitForIdle()

    // Assert: entering the screen requests permission exactly once.
    assertEquals(1, registry.requestCount.get())

    // Act and assert: recenter explicitly requests permission again.
    composeTestRule.onNodeWithText(text(R.string.patientMapRecenter)).performClick()
    assertEquals(2, registry.requestCount.get())
  }

  @Test
  fun grantingThePermissionLaterReplacesThePermissionCard() {
    // Arrange: start with denied permission and a provider that is still searching.
    val granted = AtomicBoolean(false)
    val viewModel = createViewModel(permissionStatus = { granted.get() })
    showMap(viewModel, FakePermissionRegistryOwner(denied))

    // Act: simulate granting permission externally, then refresh the model's permission state.
    granted.set(true)
    composeTestRule.runOnIdle { viewModel.refreshPermission() }
    composeTestRule.waitUntil(TIME_OUT) {
      viewModel.locationState.value == MediaMapLocationState.Searching
    }

    // Assert: the searching card replaces the permission guidance.
    composeTestRule.onNodeWithText(text(R.string.patientMapSearching)).assertIsDisplayed()
  }

  @Test
  fun gpsFailureShowsThePositionUnavailableCard() {
    // Arrange: fail the local location stream after its initial searching state.
    val failing =
        flow<LocationUpdate> {
          emit(LocationUpdate.Searching)
          throw IllegalStateException("no Google Play services")
        }
    val viewModel = createViewModel(failing, permissionStatus = { true })
    showMap(viewModel, FakePermissionRegistryOwner(denied))
    composeTestRule.waitUntil(TIME_OUT) {
      viewModel.locationState.value == MediaMapLocationState.Unavailable
    }

    // Assert: a provider failure keeps the map visible and shows the unavailable status.
    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
    composeTestRule.onNodeWithText(text(R.string.patientMapLocationUnavailable)).assertIsDisplayed()
    // Act and assert: recenter without a position leaves the unavailable state unchanged.
    composeTestRule.onNodeWithText(text(R.string.patientMapRecenter)).performClick()
    assertEquals(MediaMapLocationState.Unavailable, viewModel.locationState.value)
  }
}
