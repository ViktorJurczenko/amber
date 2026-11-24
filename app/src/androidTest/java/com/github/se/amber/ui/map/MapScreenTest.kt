// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.connectivity.ConnectivityMonitor
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.LocationUpdatesRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Screen-level checks with local repositories; map tiles are not part of these assertions. */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val place = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne")

  /** The map gets no live profile, GPS or place service during these tests. */
  private fun createViewModel(): MediaMapViewModel {
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
          override fun hasPermission() = true

          override fun isLocationEnabled() = true

          override fun observeLocation() = flowOf(LocationUpdate.Searching)
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
  fun mapShowsPatientLayoutAndRoutesTabSelection() {
    val viewModel = createViewModel()
    var chosenTab: Tab? = null
    composeTestRule.setContent {
      AmberAppTheme {
        MapScreen(viewModel = viewModel, onTabSelected = { chosenTab = it })
      }
    }

    composeTestRule
        .onNodeWithTag(MapScreenTestTags.GOOGLE_MAP)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.screenTitlePatientMap))
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(MapTestTags.SEARCH)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(MapTestTags.POSITION_CARD)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Map))
        .assertIsSelected()

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()
    assertEquals(PatientTab.Home, chosenTab)
  }

  @Test
  fun selectingPlaceSuggestionUpdatesSearchFieldAndClosesPopup() {
    val viewModel = createViewModel()
    composeTestRule.setContent { AmberAppTheme { MapScreen(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(MapTestTags.SEARCH).performTextInput("Lausanne")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      viewModel.placeSearchState.value.suggestions.isNotEmpty()
    }
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapTestTags.SUGGESTION).performClick()

    composeTestRule.onNodeWithTag(MapTestTags.SEARCH).assertTextEquals("Lausanne")
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()
    assertEquals(place, viewModel.placeSearchState.value.selectedPlace)
  }
}
