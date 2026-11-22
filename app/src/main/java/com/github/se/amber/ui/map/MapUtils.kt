// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTextField
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.MAX_CHAR_PLACE_SEARCH
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER

object MapTestTags {
  const val SEARCH = "patient_map_search"
  const val POSITION_CARD = "patient_map_position_card"
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
      modifier = modifier,
      placeholder = stringResource(R.string.patientMapSearchHint),
      leadingIcon = Icons.Outlined.Search,
      maxChar = MAX_CHAR_PLACE_SEARCH,
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { onSearch() }),
      testTag = MapTestTags.SEARCH,
  )
}

/** Position card that reflects the live GPS state. */
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
            if (locationState.position.isLastKnown) R.string.patientMapLastKnown
            else R.string.patientMapLocationFound
      }
  Card(
      modifier = modifier.fillMaxWidth().testTag(MapTestTags.POSITION_CARD),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = PATIENT_PADDING_MINI),
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PATIENT_PADDING_SMALL),
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
      DisplayPatientButton(
          onClick = onRecenter,
          text = stringResource(R.string.patientMapRecenter),
          textStyle = MaterialTheme.typography.labelLarge,
          buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
          buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun MapSearchFieldPreview() {
  AmberAppTheme { MapSearchField() }
}

@Preview(showBackground = true)
@Composable
private fun MapPositionCardPreview() {
  AmberAppTheme { MapPositionCard() }
}
