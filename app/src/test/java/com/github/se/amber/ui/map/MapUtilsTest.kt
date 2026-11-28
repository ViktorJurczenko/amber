// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.map

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.ui.theme.AmberAppTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            query = query.value,
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
    composeTestRule.setContent { AmberAppTheme { MapSearchField(query = query.value) } }

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
  fun positionCard_displaysEachState_andGoSomewhereAndRecenterPassTheirClicks() {
    val location = mutableStateOf<MediaMapLocationState>(MediaMapLocationState.Searching)
    var recenters = 0
    var goSomewheres = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          MapRecenterButton(onRecenter = { recenters++ })
          MapPositionCard(locationState = location.value, onGoSomewhere = { goSomewheres++ })
        }
      }
    }

    composeTestRule.onNodeWithTag(MapTestTags.POSITION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapOverview))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapYourPosition))
        .assertIsDisplayed()
    // Recenter is no longer a text button in the card, but an icon button named for screen readers
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapRecenter))
        .assertDoesNotExist()
    val recenterButton = composeTestRule.onNodeWithTag(MapTestTags.RECENTER)
    recenterButton.assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.patientMapRecenter))
        .assertIsDisplayed()

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
    statesAndMessages.forEach { (state, message) ->
      location.value = state
      composeTestRule.onNodeWithText(context.getString(message)).assertIsDisplayed()
      previousMessage?.let { oldMessage ->
        composeTestRule.onNodeWithText(context.getString(oldMessage)).assertDoesNotExist()
      }
      previousMessage = message
    }

    recenterButton.performClick()
    assertEquals(1, recenters)
    composeTestRule
        .onNodeWithTag(MapTestTags.GO_SOMEWHERE)
        .assertTextEquals(context.getString(R.string.guidanceGoSomewhere))
        .performClick()
    assertEquals(1 to 1, recenters to goSomewheres)
  }

  @Test
  @Config(qualifiers = "fr")
  fun mapHelpers_displayFrenchLabels() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          MapSearchField()
          MapPositionCard(locationState = MediaMapLocationState.NoPermission)
          MapRecenterButton()
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
      // The recenter button shows its name to screen readers only
      val node =
          if (id == R.string.patientMapRecenter) {
            composeTestRule.onNodeWithContentDescription(frenchText)
          } else {
            composeTestRule.onNodeWithText(frenchText)
          }
      node.assertIsDisplayed()
      assertNotEquals(englishContext.getString(id), frenchText)
    }
  }

  @Test
  fun previews_renderDefaultSearchFieldAndSearchingPositionCard() {
    composeTestRule.setContent {
      Column {
        MapSearchFieldPreview()
        MapPositionCardPreview()
        MapRecenterButtonPreview()
      }
    }

    composeTestRule.onNodeWithTag(MapTestTags.RECENTER).assertIsDisplayed()

    composeTestRule.onNodeWithTag(MapTestTags.SEARCH).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapTestTags.POSITION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearching))
        .assertIsDisplayed()
  }
}
