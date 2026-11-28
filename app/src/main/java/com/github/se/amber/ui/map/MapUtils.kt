// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientCircularIconButton
import com.github.se.amber.ui.patient.DisplayPatientTextField
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.MAX_CHAR_PLACE_SEARCH
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
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
  const val POSITION_CARD = "map_position_card"
  const val RECENTER = "map_recenter"
  const val GO_SOMEWHERE = "map_go_somewhere"
}

/** Search field placed over the map. */
@Composable
internal fun MapSearchField(
    modifier: Modifier = Modifier,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
) {
  DisplayPatientTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier =
          modifier.background(
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
}

/**
 * Recenter button on the side of the map, like the one of Google Maps: a square with the patient
 * button color. The parent handles [onRecenter], including permission requests and camera movement.
 *
 * @param modifier Positions the button on the map.
 * @param onRecenter Called when the user taps the button.
 */
@Composable
internal fun MapRecenterButton(modifier: Modifier = Modifier, onRecenter: () -> Unit = {}) {
  DisplayPatientCircularIconButton(
      modifier = modifier,
      icon = Icons.Outlined.MyLocation,
      description = stringResource(R.string.patientMapRecenter),
      shape = RoundedCornerShape(ROUND_CORNER),
      onClick = onRecenter,
      testTag = MapTestTags.RECENTER,
  )
}

/**
 * Position card that reflects the live GPS state, with the button to be guided somewhere.
 * [locationState] selects the translated status text.
 *
 * @param modifier Positions the card at the bottom of the map.
 * @param locationState Current permission, GPS, or position state supplied by the ViewModel.
 * @param onGoSomewhere Called when the user taps the Go somewhere button.
 */
@Composable
internal fun MapPositionCard(
    modifier: Modifier = Modifier,
    locationState: MediaMapLocationState = MediaMapLocationState.Searching,
    onGoSomewhere: () -> Unit = {},
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
          onClick = onGoSomewhere,
          text = stringResource(R.string.guidanceGoSomewhere),
          textStyle = MaterialTheme.typography.labelLarge,
          buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
          fillWidth = false,
          icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
          testTag = MapTestTags.GO_SOMEWHERE,
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

/** Preview of the recenter button. */
@Preview(showBackground = true)
@Composable
internal fun MapRecenterButtonPreview() {
  AmberAppTheme { MapRecenterButton() }
}
