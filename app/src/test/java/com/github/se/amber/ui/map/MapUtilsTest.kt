// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
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

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()
    assertEquals(1, recenters)
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

    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapSearchHint))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapPermissionNeeded))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.patientMapRecenter))
        .assertIsDisplayed()
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
