// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.guidance

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.SavedPlacesRepository
import com.github.se.amber.data.directions.FakeRouteRepository
import com.github.se.amber.data.directions.RouteError
import com.github.se.amber.data.location.FakeLocationUpdatesRepository
import com.github.se.amber.data.location.FakeMediaLocationRepository
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.TestRoutes.AT_END
import com.github.se.amber.model.directions.TestRoutes.END
import com.github.se.amber.model.directions.TestRoutes.LEG_METERS
import com.github.se.amber.model.directions.TestRoutes.L_ROUTE
import com.github.se.amber.model.directions.TestRoutes.OFF_FIRST_LEG
import com.github.se.amber.model.directions.TestRoutes.START
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.PatientScreen
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class GuidanceScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Records the permission requests, and answers each one at once. */
  private class FakePermissionRegistryOwner : ActivityResultRegistryOwner {
    var requests = 0

    override val activityResultRegistry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            requests++
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, emptyMap<String, Boolean>() as O)
          }
        }
  }

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val routes = FakeRouteRepository()
  private val location = FakeLocationUpdatesRepository(permission = true)
  private val savedPlaces = mockk<SavedPlacesRepository>()
  private val profiles = mockk<ProfileRepository>()
  private val activeCircle = mockk<ActiveCircleRepository>()
  private val pins = FakeMediaLocationRepository()
  private val permissions = FakePermissionRegistryOwner()
  private var search = PlaceSearchRepository { Result.success(emptyList()) }
  private val pharmacy = Place("pharmacy", "Pharmacy", GeoLocation(END.lat, END.lng))
  private val lake = MediaMarker("lake", START.lat, START.lng, "The lake", emptyList())
  private val foundAddress = GeoLocation(END.lat, END.lng, "Rue de Bourg")
  private val accuracy = (GUIDANCE_MAX_ACCURACY_METERS / 2).toFloat()
  private var backs = 0

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  @Before
  fun setUp() {
    val patient = UserProfile("patient", Role.PATIENT, Person(), listOf("circle1"))
    coEvery { profiles.getCurrentProfile() } returns Result.success(patient)
    every { activeCircle.activeCircleId(any(), any()) } returns flowOf("circle1")
    every { savedPlaces.observePlaces("circle1") } returns flowOf(listOf(pharmacy))
    pins.markers.value = mapOf("circle1" to listOf(lake))
    moveTo(START)
  }

  private fun moveTo(point: RoutePoint) {
    location.updates.value = LocationUpdate.Position(DevicePosition(point.lat, point.lng, accuracy))
  }

  private fun setScreen() {
    val viewModel =
        GuidanceViewModel(
            routeRepository = routes,
            locationUpdatesRepository = location,
            placeSearchRepository = { search.search(it) },
            savedPlacesRepository = savedPlaces,
            mediaLocationRepository = pins,
            appointmentRepository = FakeAppointmentRepository(ZoneOffset.UTC),
            profileRepository = profiles,
            activeCircleRepository = activeCircle,
            zone = ZoneOffset.UTC,
        )
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides permissions) {
        AmberAppTheme {
          GuidanceScreen(
              viewModel = viewModel,
              onBack = { backs++ },
              // The real map needs Google Play services
              routeMap = { modifier, _, _ -> Box(modifier.testTag(ROUTE_MAP)) },
          )
        }
      }
    }
  }

  private fun choosePharmacy() = composeTestRule.onNodeWithText(pharmacy.name).performClick()

  private fun choosePharmacyAndGo() {
    choosePharmacy()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG_CONFIRM).performClick()
  }

  @Test
  fun choosingAPlace_asksToConfirmWithTheWalkTime_cancelOrGo_thenStopAsksFirst() {
    setScreen()
    // Each place shows what kind it is, for screen readers
    composeTestRule
        .onNodeWithContentDescription(string(R.string.guidanceKindSavedPlace))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(string(R.string.guidanceKindMemory))
        .assertIsDisplayed()

    choosePharmacy()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG_DISMISS).performClick()
    composeTestRule.onNodeWithTag(GuidanceTestTags.CONFIRM_DIALOG).assertDoesNotExist()

    choosePharmacy()
    composeTestRule
        .onNodeWithText(string(R.string.guidanceConfirmTitle, pharmacy.name))
        .assertIsDisplayed()
    val minutes = L_ROUTE.durationSeconds / SECONDS_PER_MINUTE
    composeTestRule
        .onNodeWithText(
            context.resources.getQuantityString(R.plurals.guidanceWalkMinutes, minutes, minutes)
        )
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG_CONFIRM).performClick()
    composeTestRule
        .onNodeWithText(string(R.string.guidanceStepRight, LEG_METERS.toInt()))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ROUTE_MAP).assertIsDisplayed()
    // Only the cancel bar stays over the map
    composeTestRule.onNodeWithText(string(R.string.guidanceGoSomewhere)).assertDoesNotExist()

    // Keep going leaves the guidance on, Stop goes back to the places
    composeTestRule.onNodeWithTag(GuidanceTestTags.STOP).performClick()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG_DISMISS).performClick()
    composeTestRule.onNodeWithTag(GuidanceTestTags.STEP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GuidanceTestTags.STOP).performClick()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG_CONFIRM).performClick()
    composeTestRule.onNodeWithText(pharmacy.name).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.guidanceGoSomewhere)).assertIsDisplayed()
    assertEquals(0, backs)
  }

  @Test
  fun whileTheRouteLoads_itSaysSo_andStopGoesBackToThePlaces() {
    val route = CompletableDeferred<Result<Route>>()
    routes.answer = { route.await() }
    setScreen()

    choosePharmacy()
    composeTestRule
        .onNodeWithText(string(R.string.guidanceLoading, pharmacy.name))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(GuidanceTestTags.STOP).performClick()
    route.complete(Result.success(L_ROUTE))

    composeTestRule.onNodeWithText(pharmacy.name).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GuidanceTestTags.CONFIRM_DIALOG).assertDoesNotExist()
  }

  @Test
  fun offTheRoute_theStepSaysSo_backAsksFirst_andArrivalClosesTheDialog_thenDoneLeaves() {
    setScreen()
    choosePharmacyAndGo()

    moveTo(OFF_FIRST_LEG)
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.STEP_STATUS)
        .assertTextEquals(string(R.string.guidanceOffRoute))

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(GuidanceTestTags.STOP_DIALOG).assertIsDisplayed()
    assertEquals(0, backs)

    moveTo(AT_END)
    composeTestRule.onNodeWithTag(GuidanceTestTags.STOP_DIALOG).assertDoesNotExist()
    composeTestRule.onNodeWithText(string(R.string.guidanceArrivedTitle)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GuidanceTestTags.DONE).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun aProblem_showsItsMessage_tryAgainOrAllowLocationTriesAgain_orAnotherPlace() {
    routes.answer = { Result.failure(RouteError.Network) }
    setScreen()

    choosePharmacy()
    composeTestRule.onNodeWithText(string(R.string.guidanceProblemOffline)).assertIsDisplayed()
    val requestsBefore = routes.requests.size
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.PROBLEM_ACTION)
        .assertTextEquals(string(R.string.guidanceTryAgain))
        .performClick()
    assertEquals(requestsBefore + 1, routes.requests.size)

    composeTestRule.onNodeWithTag(GuidanceTestTags.CHOOSE_ANOTHER).performClick()
    composeTestRule.onNodeWithText(pharmacy.name).assertIsDisplayed()

    // Without the permission, the button asks Android for it, then tries again
    location.permission = false
    choosePharmacy()
    location.permission = true
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.PROBLEM_ACTION)
        .assertTextEquals(string(R.string.guidanceAllowLocation))
        .performClick()
    assertEquals(1, permissions.requests)
    assertEquals(requestsBefore + 2, routes.requests.size)
  }

  /** Waits for the search the patient's typing starts, once they stop typing. */
  private fun waitFor(matcher: SemanticsMatcher) = composeTestRule.waitUntil {
    composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
  }

  @Test
  fun typing_showsItIsSearching_thenTheFoundPlaces_orWhyItFailed_orThatNoneWasFound() {
    every { savedPlaces.observePlaces("circle1") } returns flowOf(emptyList())
    pins.markers.value = emptyMap()
    val found = CompletableDeferred<Result<List<GeoLocation>>>()
    search = PlaceSearchRepository { found.await() }
    setScreen()
    composeTestRule.onNodeWithTag(GuidanceTestTags.NO_DESTINATIONS).assertIsDisplayed()

    val typed = "Bourg"
    val address = checkNotNull(foundAddress.placeName)
    composeTestRule.onNodeWithTag(GuidanceTestTags.SEARCH_FIELD).performTextInput(typed)
    composeTestRule.onNodeWithTag(GuidanceTestTags.SEARCHING).assertIsDisplayed()
    found.complete(Result.success(listOf(foundAddress)))
    // The list shows the address; once chosen, the place is called what the patient typed
    waitFor(hasText(address))
    composeTestRule.onNodeWithText(address).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.guidanceConfirmTitle, typed)).assertIsDisplayed()
    composeTestRule
        .onNode(hasText(address) and hasAnyAncestor(hasTestTag(GuidanceTestTags.CONFIRM_DIALOG)))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.DIALOG_DISMISS).performClick()

    search = PlaceSearchRepository { Result.failure(IllegalStateException("offline")) }
    composeTestRule.onNodeWithTag(GuidanceTestTags.SEARCH_FIELD).performTextReplacement("Bourg-en")
    waitFor(hasTestTag(GuidanceTestTags.SEARCH_ERROR))
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.SEARCH_ERROR)
        .assertTextEquals(string(R.string.guidanceSearchError))

    search = PlaceSearchRepository { Result.success(emptyList()) }
    composeTestRule.onNodeWithTag(GuidanceTestTags.SEARCH_RETRY).performClick()
    composeTestRule.onNodeWithTag(GuidanceTestTags.NO_SEARCH_RESULTS).assertIsDisplayed()
    assertTrue(
        composeTestRule
            .onAllNodesWithTag(GuidanceTestTags.SEARCH_ERROR)
            .fetchSemanticsNodes()
            .isEmpty()
    )
  }

  @Test
  fun theTabOfTheScreenThatOpenedGuidance_isHighlighted() {
    assertEquals(PatientTab.Home, guidanceTabFor(PatientScreen.Home.route))
    assertEquals(PatientTab.Map, guidanceTabFor(PatientScreen.Map.route))
    assertEquals(PatientTab.Map, guidanceTabFor())
  }

  @Test
  @Config(qualifiers = "fr")
  fun theStep_isInFrench() {
    setScreen()
    choosePharmacyAndGo()

    val french = string(R.string.guidanceStepRight, LEG_METERS.toInt())
    val englishConfiguration =
        Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    val english =
        context
            .createConfigurationContext(englishConfiguration)
            .getString(R.string.guidanceStepRight, LEG_METERS.toInt())
    composeTestRule.onNodeWithText(french).assertIsDisplayed()
    assertNotEquals(english, french)
  }

  private companion object {
    const val ROUTE_MAP = "test_route_map"
    const val SECONDS_PER_MINUTE = 60
  }
}
