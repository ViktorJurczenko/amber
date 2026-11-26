// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTextField
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.MAP_MIN_PLACE_QUERY_LENGTH
import com.github.se.amber.ui.theme.MAX_CHAR_PLACE_SEARCH
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.WEIGHT_ONE

/**
 * Decision produced for one location update.
 *
 * @property moveTo Position to center the camera on, or null when it should stay where it is.
 * @property centeredOnFresh Whether the first fresh position has already centered the camera.
 */
internal data class AutoCenterStep(val moveTo: DevicePosition?, val centeredOnFresh: Boolean)

/**
 * Decides whether a location update should automatically move the map camera.
 *
 * Last-known positions may center the camera while GPS searches. The first fresh position centers
 * it once more and sets [AutoCenterStep.centeredOnFresh]. Later updates leave the camera alone so
 * map gestures are not overridden. A null [position] never requests a camera move.
 *
 * @param position Latest device position, if available.
 * @param centeredOnFresh Whether a fresh position has already centered the camera.
 * @return The requested camera move and the flag to retain for the next update.
 */
internal fun autoCenterStep(position: DevicePosition?, centeredOnFresh: Boolean): AutoCenterStep =
    if (position == null || centeredOnFresh) AutoCenterStep(null, centeredOnFresh)
    else AutoCenterStep(position, centeredOnFresh = !position.isLastKnown)

/** Stable identifiers for the search controls and position card in UI tests. */
object MapTestTags {
  const val SEARCH = "map_search"
  const val SEARCH_POPUP = "map_search_popup"
  const val SUGGESTION = "map_search_suggestion"
  const val POSITION_CARD = "map_position_card"
}

/**
 * Search field and floating suggestions placed over the map.
 *
 * The parent owns [state] and handles every event. The popup appears for a nonblank query until a
 * place is selected; it shows query guidance, loading, failure, empty, or suggestion content.
 *
 * @param modifier Positions the search field and popup over the map.
 * @param state Current query, suggestions, selection, and search status supplied by the ViewModel.
 * @param onQueryChange Called whenever the user edits the search text.
 * @param onSuggestionClick Called with the place the user taps in the popup.
 * @param onRetrySearch Called when the user retries a failed place search.
 * @param onSearch Called when the user presses Search on the keyboard.
 */
@NonRestartableComposable
@Composable
internal fun MapSearchField(
    modifier: Modifier = Modifier,
    state: MapPlaceSearchState = MapPlaceSearchState(),
    onQueryChange: (String) -> Unit = {},
    onSuggestionClick: (GeoLocation) -> Unit = {},
    onRetrySearch: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
  Column(modifier = modifier.fillMaxWidth()) {
    DisplayPatientTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier =
            Modifier.background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(ROUND_CORNER),
            ),
        placeholder = stringResource(R.string.patientMapSearchHint),
        leadingIcon = Icons.Outlined.Search,
        maxChar = MAX_CHAR_PLACE_SEARCH,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        testTag = MapTestTags.SEARCH,
    )
    if (state.query.isNotBlank() && state.selectedPlace == null) {
      Surface(
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = PATIENT_PADDING_MINI)
                  .testTag(MapTestTags.SEARCH_POPUP),
          shape = RoundedCornerShape(ROUND_CORNER),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = PATIENT_PADDING_MINI,
      ) {
        Column(
            modifier = Modifier.heightIn(max = TEXT_FIELD_BIG).verticalScroll(rememberScrollState())
        ) {
          when {
            state.query.trim().length < MAP_MIN_PLACE_QUERY_LENGTH ->
                MapSearchHint(
                    pluralStringResource(
                        R.plurals.locationQueryTooShort,
                        MAP_MIN_PLACE_QUERY_LENGTH,
                        MAP_MIN_PLACE_QUERY_LENGTH,
                    )
                )
            state.isSearching -> MapSearchHint(stringResource(R.string.locationSearching))
            state.searchFailed -> {
              MapSearchHint(stringResource(R.string.patientMapSearchFailed))
              TextButton(onClick = onRetrySearch) {
                Text(stringResource(R.string.locationTryAgain))
              }
            }
            state.hasSearched && state.suggestions.isEmpty() ->
                MapSearchHint(stringResource(R.string.patientMapNoPlaceFound))
            else ->
                state.suggestions.forEachIndexed { index, place ->
                  if (index > 0) HorizontalDivider(thickness = DIVIDER_THICKNESS)
                  MapPlaceSuggestion(place = place, onClick = { onSuggestionClick(place) })
                }
          }
        }
      }
    }
  }
}

/**
 * One tappable place returned by the search repository.
 *
 * [onClick] leaves selection and camera movement to the parent screen.
 *
 * @param place Suggestion whose name is shown in the row.
 * @param onClick Called when the user taps this suggestion.
 */
@NonRestartableComposable
@Composable
internal fun MapPlaceSuggestion(place: GeoLocation = GeoLocation(), onClick: () -> Unit = {}) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(PATIENT_PADDING_SMALL)
              .testTag(MapTestTags.SUGGESTION),
      horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MINI),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Place, contentDescription = null)
    Text(place.placeName.orEmpty(), style = MaterialTheme.typography.bodyMedium)
  }
}

/**
 * A translated search status inside the suggestion popup.
 *
 * @param text Localized hint, loading message, failure, or empty result message.
 */
@NonRestartableComposable
@Composable
private fun MapSearchHint(text: String = "") {
  Text(
      text,
      modifier = Modifier.padding(PATIENT_PADDING_SMALL),
      style = MaterialTheme.typography.bodyMedium,
  )
}

/**
 * Position card that reflects the live GPS state.
 *
 * [locationState] selects the translated status text. The parent handles [onRecenter], including
 * permission requests and camera movement, so this component only renders state and user input.
 *
 * @param modifier Positions the card at the bottom of the map.
 * @param locationState Current permission, GPS, or position state supplied by the ViewModel.
 * @param onRecenter Called when the user taps the recenter button.
 */
@Composable
internal fun MapPositionCard(
    modifier: Modifier = Modifier,
    locationState: MediaMapLocationState = MediaMapLocationState.Searching,
    onRecenter: () -> Unit = {},
) {
  val detail =
      when (locationState) {
        MediaMapLocationState.NoPermission -> R.string.patientMapPermissionNeeded
        MediaMapLocationState.LocationOff -> R.string.patientMapLocationOff
        MediaMapLocationState.Searching -> R.string.patientMapSearching
        MediaMapLocationState.Unavailable -> R.string.patientMapLocationUnavailable
        is MediaMapLocationState.Located ->
            // TODO the user location will be displayed later instead of a hard coded string
            if (locationState.position.isLastKnown) R.string.patientMapLastKnown
            else R.string.patientMapLocationFound
      }
  Card(
      modifier = modifier.fillMaxWidth().testTag(MapTestTags.POSITION_CARD),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = PATIENT_PADDING_MINI),
  ) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PATIENT_PADDING_SMALL),
        horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
          modifier = Modifier.weight(WEIGHT_ONE),
          verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MINI),
      ) {
        Text(
            text = stringResource(R.string.patientMapOverview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.patientMapYourPosition),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DisplayPatientButton(
          onClick = onRecenter,
          text = stringResource(R.string.patientMapRecenter),
          textStyle = MaterialTheme.typography.labelLarge,
          buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
          fillWidth = false,
          icon = Icons.Outlined.MyLocation,
      )
    }
  }
}

/** Preview of the search field with its default empty query. */
@Preview(showBackground = true)
@Composable
internal fun MapSearchFieldPreview() {
  AmberAppTheme { MapSearchField() }
}

/** Preview of the position card while the device is searching for a location. */
@Preview(showBackground = true)
@Composable
internal fun MapPositionCardPreview() {
  AmberAppTheme { MapPositionCard() }
}
