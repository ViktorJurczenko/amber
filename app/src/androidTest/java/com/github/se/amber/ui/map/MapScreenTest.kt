// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

  @Test
  fun withoutPermissionTheCardAsksToAllowLocation_andTheMapStaysVisible() {
    showMap(createViewModel(permissionStatus = { false }), FakePermissionRegistryOwner(denied))

    composeTestRule.onNodeWithText(text(R.string.patientMapPermissionNeeded)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP).assertIsDisplayed()
  }

  @Test
  fun withoutPermissionTheRequestIsLaunchedOnceOnEntry_and_TappingRecenterWithoutPermissionAsksAgain() {
    val registry = FakePermissionRegistryOwner(denied)
    showMap(createViewModel(permissionStatus = { false }), registry)
    composeTestRule.waitForIdle()

    assertEquals(1, registry.requestCount.get())

    composeTestRule.onNodeWithText(text(R.string.patientMapRecenter)).performClick()
    assertEquals(2, registry.requestCount.get())
  }

  @Test
  fun grantingThePermissionLaterReplacesThePermissionCard() {
    val granted = AtomicBoolean(false)
    val viewModel = createViewModel(permissionStatus = { granted.get() })
    showMap(viewModel, FakePermissionRegistryOwner(denied))

    granted.set(true)
    composeTestRule.runOnIdle { viewModel.refreshPermission() }
    composeTestRule.waitUntil(TIME_OUT) {
      viewModel.locationState.value == MediaMapLocationState.Searching
    }

    composeTestRule.onNodeWithText(text(R.string.patientMapSearching)).assertIsDisplayed()
  }

  @Test
  fun gpsFailureShowsThePositionUnavailableCard() {
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

    composeTestRule.onNodeWithText(text(R.string.patientMapLocationUnavailable)).assertIsDisplayed()
  }
}
