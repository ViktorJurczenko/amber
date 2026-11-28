// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.guidance

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.TestRoutes.END
import com.github.se.amber.model.directions.TestRoutes.LEG_METERS
import com.github.se.amber.model.directions.TestRoutes.L_ROUTE
import com.github.se.amber.model.directions.TestRoutes.OFF_FIRST_LEG
import com.github.se.amber.model.directions.TestRoutes.START
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The parts of the guidance screen, each shown alone with a given state. */
@RunWith(RobolectricTestRunner::class)
class GuidanceUtilsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val pharmacy =
      GuidanceDestination("pharmacy", "Pharmacy", END, GuidanceDestinationKind.SAVED_PLACE)
  private val found =
      GuidanceDestination(
          "0",
          "Bourg",
          START,
          GuidanceDestinationKind.SEARCH,
          address = "Rue de Bourg, Lausanne",
      )
  private val meters = LEG_METERS.toInt()

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  @Test
  fun choosing_listsThePlaces_andEachSearchState_withItsOwnCallback() {
    val chosen = mutableListOf<GuidanceDestination>()
    var searches = 0
    var state by mutableStateOf(GuidanceUIState())
    composeTestRule.setContent {
      AmberAppTheme {
        GuidanceChoosing(state = state, onSearch = { searches++ }, onChoose = { chosen += it })
      }
    }
    composeTestRule.onNodeWithTag(GuidanceTestTags.NO_DESTINATIONS).assertIsDisplayed()

    state = state.copy(destinations = listOf(pharmacy), isSearching = true)
    composeTestRule.onNodeWithTag(GuidanceTestTags.SEARCHING).assertIsDisplayed()
    // Each place says what kind it is, for screen readers
    composeTestRule
        .onNodeWithContentDescription(string(R.string.guidanceKindSavedPlace))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(pharmacy.name).performClick()

    state = state.copy(isSearching = false, searchErrorRes = R.string.guidanceSearchError)
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.SEARCH_ERROR)
        .assertTextEquals(string(R.string.guidanceSearchError))
    composeTestRule.onNodeWithTag(GuidanceTestTags.SEARCH_RETRY).performClick()
    assertEquals(1, searches)

    state = state.copy(searchErrorRes = null, noSearchResults = true)
    composeTestRule.onNodeWithTag(GuidanceTestTags.NO_SEARCH_RESULTS).assertIsDisplayed()

    // A result shows its address, or its name without one
    val unnamed = found.copy(id = "1", address = null)
    state = state.copy(noSearchResults = false, searchResults = listOf(found, unnamed))
    composeTestRule.onNodeWithText(found.name).assertIsDisplayed()
    composeTestRule.onNodeWithText(checkNotNull(found.address)).performClick()
    assertEquals(listOf(pharmacy, found), chosen)
  }

  @Test
  fun eachNextManeuver_showsItsOwnWords_andTheDestinationComesLast() {
    val words =
        mapOf(
            Maneuver.DEPART to R.string.guidanceStepStraight,
            Maneuver.STRAIGHT to R.string.guidanceStepStraight,
            Maneuver.SLIGHT_LEFT to R.string.guidanceStepSlightLeft,
            Maneuver.LEFT to R.string.guidanceStepLeft,
            Maneuver.SHARP_LEFT to R.string.guidanceStepSharpLeft,
            Maneuver.SLIGHT_RIGHT to R.string.guidanceStepSlightRight,
            Maneuver.RIGHT to R.string.guidanceStepRight,
            Maneuver.SHARP_RIGHT to R.string.guidanceStepSharpRight,
            Maneuver.U_TURN to R.string.guidanceStepUTurn,
            Maneuver.ROUNDABOUT to R.string.guidanceStepRoundabout,
            Maneuver.UNKNOWN to R.string.guidanceStepKeepGoing,
        )
    assertEquals("every maneuver is listed", Maneuver.entries.toSet(), words.keys)
    var guiding by
        mutableStateOf(GuidancePhase.Guiding(pharmacy, L_ROUTE, metersToNextManeuver = meters))
    composeTestRule.setContent { AmberAppTheme { GuidanceStepCard(guiding = guiding) } }

    words.forEach { (maneuver, wordsRes) ->
      guiding = guiding.copy(nextManeuver = maneuver)
      composeTestRule.onNodeWithText(string(wordsRes, meters)).assertIsDisplayed()
    }
    guiding = guiding.copy(nextManeuver = null)
    composeTestRule
        .onNodeWithText(string(R.string.guidanceStepDestination, pharmacy.name, meters))
        .assertIsDisplayed()
  }

  @Test
  fun theStepCard_saysWhyTheGuidanceMayBePaused_locationOffFirst() {
    var guiding by mutableStateOf(GuidancePhase.Guiding(pharmacy, L_ROUTE))
    composeTestRule.setContent { AmberAppTheme { GuidanceStepCard(guiding = guiding) } }
    composeTestRule.onNodeWithTag(GuidanceTestTags.STEP_STATUS).assertDoesNotExist()

    val statuses =
        listOf(
            guiding.copy(isOffRoute = true) to R.string.guidanceOffRoute,
            guiding.copy(isOffRoute = true, isFindingNewRoute = true) to
                R.string.guidanceFindingNewRoute,
            guiding.copy(isOffRoute = true, isFindingNewRoute = true, isLocationOff = true) to
                R.string.guidanceLocationOff,
        )
    statuses.forEach { (paused, statusRes) ->
      guiding = paused
      composeTestRule
          .onNodeWithTag(GuidanceTestTags.STEP_STATUS)
          .assertTextEquals(string(statusRes))
    }
  }

  @Test
  fun theRouteMap_drawsTheRoute_andFollowsThePatient() {
    var position by mutableStateOf(START)
    composeTestRule.setContent {
      AmberAppTheme {
        GuidanceRouteMap(Modifier.testTag(ROUTE_MAP), route = L_ROUTE, position = position)
      }
    }
    composeTestRule.onNodeWithTag(ROUTE_MAP).assertIsDisplayed()

    position = OFF_FIRST_LEG
    composeTestRule.onNodeWithTag(ROUTE_MAP).assertIsDisplayed()
  }

  @Test
  fun loadingArrivalAndProblems_sayWhatIsGoingOn_andEachButtonCallsItsOwnCallback() {
    var phase by mutableStateOf<GuidancePhase>(GuidancePhase.LoadingRoute(pharmacy))
    var dones = 0
    var actions = 0
    var others = 0
    composeTestRule.setContent {
      AmberAppTheme {
        when (val shown = phase) {
          is GuidancePhase.LoadingRoute -> GuidanceLoading(shown.destination.name)
          is GuidancePhase.Arrived -> GuidanceArrived(shown.destination.name) { dones++ }
          is GuidancePhase.Problem ->
              GuidanceProblemContent(shown.problem, { actions++ }, { others++ })
          else -> Unit
        }
      }
    }
    composeTestRule
        .onNodeWithText(string(R.string.guidanceLoading, pharmacy.name))
        .assertIsDisplayed()

    phase = GuidancePhase.Arrived(pharmacy)
    composeTestRule.onNodeWithText(string(R.string.guidanceArrivedTitle)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GuidanceTestTags.DONE).performClick()
    assertEquals(1, dones)

    // Without the permission the button asks for it; otherwise it tries again
    phase = GuidancePhase.Problem(GuidanceProblem.NO_PERMISSION, pharmacy)
    composeTestRule
        .onNodeWithText(string(GuidanceProblem.NO_PERMISSION.messageRes))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.PROBLEM_ACTION)
        .assertTextEquals(string(R.string.guidanceAllowLocation))
    phase = GuidancePhase.Problem(GuidanceProblem.OFFLINE, pharmacy)
    composeTestRule
        .onNodeWithTag(GuidanceTestTags.PROBLEM_ACTION)
        .assertTextEquals(string(R.string.guidanceTryAgain))
        .performClick()
    composeTestRule.onNodeWithTag(GuidanceTestTags.CHOOSE_ANOTHER).performClick()
    assertEquals(1 to 1, actions to others)
  }

  private companion object {
    const val ROUTE_MAP = "test_route_map"
  }
}
