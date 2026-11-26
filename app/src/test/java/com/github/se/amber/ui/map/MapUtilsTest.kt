// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.MAP_MIN_PLACE_QUERY_LENGTH
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapUtilsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun cameraCentersOnLastKnown_ThenFreshFix_ThenStopsFollowing() {
    assertEquals(AutoCenterStep(null, false), autoCenterStep(null, false))

    val lastKnown = DevicePosition(46.52, 6.63, isLastKnown = true)
    // Sends a last-known position and checks that the camera moves there
    val lastKnownStep = autoCenterStep(lastKnown, false)
    assertEquals(AutoCenterStep(lastKnown, false), lastKnownStep)

    // Sends a fresh position and checks that the camera moves there
    val fresh = DevicePosition(46.53, 6.64)
    val freshStep = autoCenterStep(fresh, lastKnownStep.centeredOnFresh)
    assertEquals(AutoCenterStep(fresh, true), freshStep)

    // Sends another position and checks that the camera stays at the first fresh position
    // The map does not move away when you drag it to see other locations
    val later = DevicePosition(46.54, 6.65)
    assertEquals(AutoCenterStep(null, true), autoCenterStep(later, freshStep.centeredOnFresh))
  }

  @Test
  fun searchField_displaysHintAndPassesUserActionsToCallbacks() {
    val query = mutableStateOf("")
    var searches = 0
    composeTestRule.setContent {
      AmberAppTheme {
        MapSearchField(
            state = MapPlaceSearchState(query = query.value),
            onQueryChange = { query.value = it },
            onSearch = { searches++ },
        )
      }
    }

    val field = composeTestRule.onNodeWithTag(MapTestTags.SEARCH)
    field.assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchHint))
        .assertIsDisplayed()

    field.performTextInput("Lausanne")
    field.assertTextEquals("Lausanne")
    assertEquals("Lausanne", query.value)

    field.performImeAction()
    assertEquals(1, searches)
  }

  @Test
  fun searchField_reflectsQueryChangesFromItsParent() {
    val query = mutableStateOf("Lausanne")
    composeTestRule.setContent {
      AmberAppTheme { MapSearchField(state = MapPlaceSearchState(query = query.value)) }
    }

    val field = composeTestRule.onNodeWithTag(MapTestTags.SEARCH)
    field.assertTextEquals("Lausanne")

    query.value = "Geneva"
    field.assertTextEquals("Geneva")

    query.value = ""
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchHint))
        .assertIsDisplayed()
  }

  @Test
  fun searchPopup_showsSuggestionsAndSelectionClosesIt() {
    val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne")
    val geneva = GeoLocation(lat = 46.20, lng = 6.14, placeName = "Geneva")
    val state = mutableStateOf(MapPlaceSearchState(query = "Lausanne", isSearching = true))
    var selected: GeoLocation? = null
    composeTestRule.setContent {
      AmberAppTheme {
        MapSearchField(
            state = state.value,
            onSuggestionClick = { selected = it },
        )
      }
    }

    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.locationSearching))
        .assertIsDisplayed()

    state.value =
        MapPlaceSearchState(
            query = "Lausanne",
            suggestions = listOf(lausanne, geneva),
            hasSearched = true,
        )
    composeTestRule
        .onNodeWithText(context.getString(R.string.locationSearching))
        .assertDoesNotExist()
    composeTestRule.onAllNodesWithTag(MapTestTags.SUGGESTION).assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(MapTestTags.SUGGESTION)[1].performClick()
    assertEquals(geneva, selected)

    state.value = MapPlaceSearchState(query = geneva.placeName.orEmpty(), selectedPlace = geneva)
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()
  }

  @Test
  fun suggestionWithoutPlaceNameCanStillBeSelected() {
    val place = GeoLocation(lat = 46.52, lng = 6.63)
    var selected = false
    composeTestRule.setContent {
      AmberAppTheme { MapPlaceSuggestion(place = place, onClick = { selected = true }) }
    }

    composeTestRule.onNodeWithTag(MapTestTags.SUGGESTION).performClick()
    assertTrue(selected)
  }

  @Test
  fun searchPopup_showsGuidanceOnlyUntilSearchCompletes() {
    val state = mutableStateOf(MapPlaceSearchState(query = "La"))
    composeTestRule.setContent { AmberAppTheme { MapSearchField(state = state.value) } }

    val shortHint =
        context.resources.getQuantityString(
            R.plurals.locationQueryTooShort,
            MAP_MIN_PLACE_QUERY_LENGTH,
            MAP_MIN_PLACE_QUERY_LENGTH,
        )
    composeTestRule.onNodeWithText(shortHint).assertIsDisplayed()

    // Whitespace is not a search, while padded short text still shows query guidance.
    state.value = MapPlaceSearchState(query = "   ")
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()

    state.value = MapPlaceSearchState(query = " La ")
    composeTestRule.onNodeWithText(shortHint).assertIsDisplayed()

    // A long query with no completed lookup should not claim that no place exists.
    state.value = MapPlaceSearchState(query = "Geneva")
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertExists()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapNoPlaceFound))
        .assertDoesNotExist()

    state.value = MapPlaceSearchState(query = "Geneva", hasSearched = true)
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapNoPlaceFound))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(shortHint).assertDoesNotExist()

    state.value = MapPlaceSearchState()
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()
  }

  @Test
  fun searchPopup_failedSearchCanRetryAndLoadingHidesPriorFailure() {
    val state = mutableStateOf(MapPlaceSearchState())
    var retries = 0
    composeTestRule.setContent {
      AmberAppTheme { MapSearchField(state = state.value, onRetrySearch = { retries++ }) }
    }

    // A failed search takes priority over suggestions left from an earlier response.
    state.value =
        MapPlaceSearchState(
            query = "Geneva",
            suggestions = listOf(GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne")),
            searchFailed = true,
        )
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchFailed))
        .assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(MapTestTags.SUGGESTION).assertCountEquals(0)
    composeTestRule.onNodeWithText(context.getString(R.string.locationTryAgain)).performClick()
    assertEquals(1, retries)

    // During retry, the loading status hides the prior failure and retry action.
    state.value = MapPlaceSearchState(query = "Geneva", isSearching = true, searchFailed = true)
    composeTestRule
        .onNodeWithText(context.getString(R.string.locationSearching))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchFailed))
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithText(context.getString(R.string.locationTryAgain))
        .assertDoesNotExist()

    state.value = MapPlaceSearchState()
    composeTestRule.onNodeWithTag(MapTestTags.SEARCH_POPUP).assertDoesNotExist()
  }

  @Test
  fun positionCard_displaysEachStateAndPassesRecenterClick() {
    val location = mutableStateOf<MediaMapLocationState>(MediaMapLocationState.Searching)
    var recenters = 0
    composeTestRule.setContent {
      AmberAppTheme {
        MapPositionCard(locationState = location.value, onRecenter = { recenters++ })
      }
    }

    composeTestRule.onNodeWithTag(MapTestTags.POSITION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapOverview))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapYourPosition))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapRecenter))
        .assertIsDisplayed()

    val recenterButton = composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
    val positionTitle =
        composeTestRule.onNodeWithText(context.getString(R.string.patientMapYourPosition))
    val recenterIcon =
        composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
    val recenterText =
        composeTestRule.onNodeWithText(
            context.getString(R.string.patientMapRecenter),
            useUnmergedTree = true,
        )
    recenterIcon.assertIsDisplayed()
    assertTrue(
        recenterButton.getUnclippedBoundsInRoot().left >
            positionTitle.getUnclippedBoundsInRoot().right
    )
    assertTrue(
        recenterIcon.getUnclippedBoundsInRoot().left >=
            recenterText.getUnclippedBoundsInRoot().right
    )

    // Check status card while location is searching and turned off
    val statesAndMessages =
        listOf(
            MediaMapLocationState.Searching to R.string.patientMapSearching,
            MediaMapLocationState.NoPermission to R.string.patientMapPermissionNeeded,
            MediaMapLocationState.LocationOff to R.string.patientMapLocationOff,
            MediaMapLocationState.Unavailable to R.string.patientMapLocationUnavailable,
            MediaMapLocationState.Located(DevicePosition(46.52, 6.63)) to
                R.string.patientMapLocationFound,
            MediaMapLocationState.Located(DevicePosition(46.52, 6.63, isLastKnown = true)) to
                R.string.patientMapLastKnown,
        )
    var previousMessage: Int? = null
    statesAndMessages.forEachIndexed { index, (state, message) ->
      location.value = state
      composeTestRule.onNodeWithText(context.getString(message)).assertIsDisplayed()
      previousMessage?.let { oldMessage ->
        composeTestRule.onNodeWithText(context.getString(oldMessage)).assertDoesNotExist()
      }
      // The action stays available even before a fresh position is found.
      recenterButton.performClick()
      assertEquals(index + 1, recenters)
      previousMessage = message
    }
  }

  @Test
  @Config(qualifiers = "fr")
  fun mapHelpers_displayFrenchLabels() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          MapSearchField()
          MapPositionCard(locationState = MediaMapLocationState.NoPermission)
        }
      }
    }

    val englishConfiguration =
        Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    val englishContext = context.createConfigurationContext(englishConfiguration)
    val keys =
        listOf(
            R.string.patientMapSearchHint,
            R.string.patientMapPermissionNeeded,
            R.string.patientMapRecenter,
            R.string.patientMapOverview,
            R.string.patientMapYourPosition,
        )

    keys.forEach { id ->
      val frenchText = context.getString(id)
      composeTestRule.onNodeWithText(frenchText).assertIsDisplayed()
      assertNotEquals(englishContext.getString(id), frenchText)
    }
  }

  @Test
  fun previews_renderDefaultSearchFieldAndSearchingPositionCard() {
    composeTestRule.setContent {
      Column {
        MapSearchFieldPreview()
        MapPositionCardPreview()
      }
    }

    composeTestRule.onNodeWithTag(MapTestTags.SEARCH).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapTestTags.POSITION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearching))
        .assertIsDisplayed()
  }
}
