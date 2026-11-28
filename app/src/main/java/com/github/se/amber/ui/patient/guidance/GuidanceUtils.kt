// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.guidance

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.RoundaboutRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straight
import androidx.compose.material.icons.outlined.TurnLeft
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.TurnSharpLeft
import androidx.compose.material.icons.outlined.TurnSharpRight
import androidx.compose.material.icons.outlined.TurnSlightLeft
import androidx.compose.material.icons.outlined.TurnSlightRight
import androidx.compose.material.icons.outlined.UTurnLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTextField
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_CHAR_PLACE_SEARCH
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.SURFACE_BIG
import com.github.se.amber.ui.theme.SURFACE_MEDIUM
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/** The default destination and route of the helpers, empty: the screen always passes its own. */
private val emptyDestination =
    GuidanceDestination("", "", RoutePoint(0.0, 0.0), GuidanceDestinationKind.SAVED_PLACE)
private val emptyRoute =
    Route(
        emptyDestination.point,
        distanceMeters = 0,
        durationSeconds = 0,
        points = emptyList(),
        steps = emptyList(),
    )

/** Zoom of the route map: a few streets around the patient, so the turns can be seen. */
private const val GUIDANCE_MAP_ZOOM = 17f

/** Width of the route line on the map, in pixels. */
private const val GUIDANCE_ROUTE_WIDTH = 16f

/** Test tags of the guidance screen. */
object GuidanceTestTags {
  const val SCREEN = "guidance_screen"
  const val NO_DESTINATIONS = "guidance_no_destinations"
  const val SEARCH_FIELD = "guidance_search_field"
  const val SEARCHING = "guidance_searching"
  const val SEARCH_ERROR = "guidance_search_error"
  const val SEARCH_RETRY = "guidance_search_retry"
  const val NO_SEARCH_RESULTS = "guidance_no_search_results"
  const val LOADING = "guidance_loading"
  const val STEP = "guidance_step"
  const val STEP_STATUS = "guidance_step_status"
  const val STOP = "guidance_stop"
  const val CONFIRM_DIALOG = "guidance_confirm_dialog"
  const val STOP_DIALOG = "guidance_stop_dialog"
  const val ARRIVED = "guidance_arrived"
  const val DONE = "guidance_done"
  const val PROBLEM = "guidance_problem"
  const val PROBLEM_ACTION = "guidance_problem_action"
  const val CHOOSE_ANOTHER = "guidance_choose_another"

  fun destination(destination: GuidanceDestination) =
      "guidance_destination_${destination.kind.name.lowercase()}_${destination.id}"
}

/**
 * Lets the patient choose where to go: one big button per place of theirs, the usual way, then an
 * address search, whose places show as they type.
 *
 * @param state the guidance state, for the places, the search and its results.
 * @param onQueryChange called when the patient types in the search field.
 * @param onSearch called to search the typed address now: the keyboard's search key, or Try again.
 * @param onChoose called with the place the patient chose.
 */
@Composable
internal fun GuidanceChoosing(
    state: GuidanceUIState = GuidanceUIState(),
    onQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onChoose: (GuidanceDestination) -> Unit = {},
) {
  Column(verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL)) {
    Text(
        text = stringResource(R.string.guidanceChooseHeader),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.guidanceYourPlaces),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (state.destinations.isEmpty()) {
      GuidanceMessage(
          stringResource(R.string.guidanceNoDestinations),
          GuidanceTestTags.NO_DESTINATIONS,
      )
    }
    state.destinations.forEach { GuidanceDestinationButton(it, onChoose) }
    DisplayPatientTextField(
        value = state.searchQuery,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.guidanceSearchHint),
        leadingIcon = Icons.Outlined.Search,
        maxChar = MAX_CHAR_PLACE_SEARCH,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.padding(top = PATIENT_PADDING_SMALL),
        testTag = GuidanceTestTags.SEARCH_FIELD,
    )
    when {
      state.isSearching ->
          GuidanceMessage(stringResource(R.string.guidanceSearching), GuidanceTestTags.SEARCHING)
      state.searchErrorRes != null -> {
        GuidanceMessage(stringResource(state.searchErrorRes), GuidanceTestTags.SEARCH_ERROR, true)
        DisplayPatientButton(
            buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
            outlined = true,
            text = stringResource(R.string.guidanceTryAgain),
            textStyle = MaterialTheme.typography.titleLarge,
            onClick = onSearch,
            testTag = GuidanceTestTags.SEARCH_RETRY,
        )
      }
      state.noSearchResults ->
          GuidanceMessage(
              stringResource(R.string.guidanceNoSearchResults),
              GuidanceTestTags.NO_SEARCH_RESULTS,
          )
      state.searchResults.isNotEmpty() -> GuidanceSearchResults(state.searchResults, onChoose)
    }
  }
}

/**
 * The places found for the typed address, in a card under the search field, as in the media upload:
 * one row per place, to tap to choose it.
 */
@Composable
private fun GuidanceSearchResults(
    results: List<GuidanceDestination> = emptyList(),
    onChoose: (GuidanceDestination) -> Unit = {},
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = PATIENT_PADDING_MINI),
  ) {
    results.forEachIndexed { index, result ->
      if (index > 0) HorizontalDivider(thickness = DIVIDER_THICKNESS)
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .clickable { onChoose(result) }
                  .padding(PATIENT_PADDING_SMALL)
                  .testTag(GuidanceTestTags.destination(result)),
          horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null) // the name says it all
        // The addresses tell the results apart: they all carry what the patient typed
        Text(text = result.address ?: result.name, style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}

/** A big button with the place's name and an icon saying where it comes from. */
@Composable
private fun GuidanceDestinationButton(
    destination: GuidanceDestination = emptyDestination,
    onChoose: (GuidanceDestination) -> Unit = {},
) {
  val (icon, kindRes) = destination.kind.iconAndLabel()
  DisplayPatientButton(
      buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
      text = destination.name,
      // Smaller than the default: a found address can be long, and must fit the button
      textStyle = MaterialTheme.typography.titleLarge,
      icon = icon,
      description = stringResource(kindRes),
      onClick = { onChoose(destination) },
      testTag = GuidanceTestTags.destination(destination),
  )
}

/** "Finding the way to …", while the position and then the route are found. */
@Composable
internal fun GuidanceLoading(destinationName: String = "") {
  Column(
      modifier = Modifier.fillMaxWidth().testTag(GuidanceTestTags.LOADING),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
  ) {
    CircularProgressIndicator()
    GuidanceMessage(stringResource(R.string.guidanceLoading, destinationName))
  }
}

/**
 * What to do next, in very large text with its arrow, and why the guidance may be paused.
 *
 * @param modifier where the card goes, over the map.
 * @param guiding the guidance going on.
 */
@Composable
internal fun GuidanceStepCard(
    modifier: Modifier = Modifier,
    guiding: GuidancePhase.Guiding = GuidancePhase.Guiding(emptyDestination, emptyRoute),
) {
  Card(
      modifier = modifier.fillMaxWidth().testTag(GuidanceTestTags.STEP),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = PATIENT_PADDING_MINI),
  ) {
    Column(
        modifier = Modifier.padding(PATIENT_PADDING_SMALL),
        verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MINI),
    ) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        GuidanceIconBox(guiding.nextManeuver.icon())
        Text(
            text = guiding.stepText(),
            modifier = Modifier.weight(WEIGHT_ONE),
            style = MaterialTheme.typography.headlineLarge,
        )
      }
      val status =
          when {
            guiding.isLocationOff -> R.string.guidanceLocationOff
            guiding.isFindingNewRoute -> R.string.guidanceFindingNewRoute
            guiding.isOffRoute -> R.string.guidanceOffRoute
            else -> null
          }
      status?.let { GuidanceMessage(stringResource(it), GuidanceTestTags.STEP_STATUS, true) }
    }
  }
}

/**
 * The route as a line on the map, with the destination's pin and the patient's dot. The camera
 * starts at the route's start and follows [position] as the patient walks.
 *
 * @param contentPadding the space the step card and the Stop button cover, kept clear of the
 *   patient's dot and Google's zoom buttons.
 */
@Composable
internal fun GuidanceRouteMap(
    modifier: Modifier = Modifier,
    route: Route? = null,
    position: RoutePoint? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
  val start = route?.points?.firstOrNull() ?: route?.destination
  val cameraPositionState = rememberCameraPositionState {
    start?.let { this.position = CameraPosition.fromLatLngZoom(it.toLatLng(), GUIDANCE_MAP_ZOOM) }
  }
  // Set, not animated: CameraUpdateFactory only works once the map has loaded, and the first
  // position can come before. The zoom the patient chose is kept
  LaunchedEffect(position) {
    position?.let {
      cameraPositionState.position =
          CameraPosition.fromLatLngZoom(it.toLatLng(), cameraPositionState.position.zoom)
    }
  }
  GoogleMap(
      modifier = modifier,
      cameraPositionState = cameraPositionState,
      // Guiding means the location is allowed: without it, guidance stops with a problem
      properties = MapProperties(isMyLocationEnabled = route != null),
      uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
      contentPadding = contentPadding,
  ) {
    route?.let {
      Polyline(
          points = it.points.map(RoutePoint::toLatLng),
          color = MaterialTheme.colorScheme.primary,
          width = GUIDANCE_ROUTE_WIDTH,
      )
      Marker(state = rememberUpdatedMarkerState(it.destination.toLatLng()))
    }
  }
}

/** "You're there!" with the place's name, and a button to finish. */
@Composable
internal fun GuidanceArrived(destinationName: String = "", onDone: () -> Unit = {}) {
  Column(
      modifier = Modifier.fillMaxWidth().testTag(GuidanceTestTags.ARRIVED),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
  ) {
    GuidanceIconBox(Icons.Outlined.Flag)
    Text(
        text = stringResource(R.string.guidanceArrivedTitle),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    GuidanceMessage(destinationName)
    DisplayPatientButton(
        text = stringResource(R.string.guidanceDone),
        onClick = onDone,
        testTag = GuidanceTestTags.DONE,
    )
  }
}

/**
 * Why the patient can't be guided, with one button to fix it and one to choose another place.
 *
 * @param problem what went wrong.
 * @param onAction called by the main button: ask for the location, or try again.
 * @param onChooseAnother called by the outlined button.
 */
@Composable
internal fun GuidanceProblemContent(
    problem: GuidanceProblem = GuidanceProblem.UNKNOWN,
    onAction: () -> Unit = {},
    onChooseAnother: () -> Unit = {},
) {
  Column(
      modifier = Modifier.fillMaxWidth().testTag(GuidanceTestTags.PROBLEM),
      verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
  ) {
    GuidanceMessage(stringResource(problem.messageRes))
    val actionRes =
        if (problem == GuidanceProblem.NO_PERMISSION) R.string.guidanceAllowLocation
        else R.string.guidanceTryAgain
    DisplayPatientButton(
        text = stringResource(actionRes),
        onClick = onAction,
        testTag = GuidanceTestTags.PROBLEM_ACTION,
    )
    DisplayPatientButton(
        outlined = true,
        text = stringResource(R.string.guidanceChooseAnother),
        onClick = onChooseAnother,
        testTag = GuidanceTestTags.CHOOSE_ANOTHER,
    )
  }
}

/** A centered message in large text; in the error color when [isWarning]. */
@Composable
private fun GuidanceMessage(text: String = "", testTag: String = "", isWarning: Boolean = false) {
  Text(
      text = text,
      modifier = Modifier.fillMaxWidth().testTag(testTag),
      style = MaterialTheme.typography.bodyLarge,
      color =
          if (isWarning) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
  )
}

/**
 * An icon in a yellow box, as on the map's recenter button. Decorative: the text next to it says
 * the same.
 */
@Composable
private fun GuidanceIconBox(icon: ImageVector = Icons.Outlined.Flag) {
  Box(
      modifier =
          Modifier.size(SURFACE_BIG)
              .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(ROUND_CORNER)),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(SURFACE_MEDIUM),
        tint = MaterialTheme.colorScheme.onTertiary,
    )
  }
}

/** What to do next, with the distance: "Turn left in 50 m", or "Pharmacy is in 20 m". */
@Composable
private fun GuidancePhase.Guiding.stepText(): String {
  val meters = metersToNextManeuver
  return when (nextManeuver) {
    null -> stringResource(R.string.guidanceStepDestination, destination.name, meters)
    Maneuver.DEPART,
    Maneuver.STRAIGHT -> stringResource(R.string.guidanceStepStraight, meters)
    Maneuver.SLIGHT_LEFT -> stringResource(R.string.guidanceStepSlightLeft, meters)
    Maneuver.LEFT -> stringResource(R.string.guidanceStepLeft, meters)
    Maneuver.SHARP_LEFT -> stringResource(R.string.guidanceStepSharpLeft, meters)
    Maneuver.SLIGHT_RIGHT -> stringResource(R.string.guidanceStepSlightRight, meters)
    Maneuver.RIGHT -> stringResource(R.string.guidanceStepRight, meters)
    Maneuver.SHARP_RIGHT -> stringResource(R.string.guidanceStepSharpRight, meters)
    Maneuver.U_TURN -> stringResource(R.string.guidanceStepUTurn, meters)
    Maneuver.ROUNDABOUT -> stringResource(R.string.guidanceStepRoundabout, meters)
    Maneuver.UNKNOWN -> stringResource(R.string.guidanceStepKeepGoing, meters)
  }
}

/** The arrow for this maneuver; a flag when the destination comes next (null). */
private fun Maneuver?.icon(): ImageVector =
    when (this) {
      null -> Icons.Outlined.Flag
      Maneuver.SLIGHT_LEFT -> Icons.Outlined.TurnSlightLeft
      Maneuver.LEFT -> Icons.Outlined.TurnLeft
      Maneuver.SHARP_LEFT -> Icons.Outlined.TurnSharpLeft
      Maneuver.SLIGHT_RIGHT -> Icons.Outlined.TurnSlightRight
      Maneuver.RIGHT -> Icons.Outlined.TurnRight
      Maneuver.SHARP_RIGHT -> Icons.Outlined.TurnSharpRight
      Maneuver.U_TURN -> Icons.Outlined.UTurnLeft
      Maneuver.ROUNDABOUT -> Icons.Outlined.RoundaboutRight
      Maneuver.DEPART,
      Maneuver.STRAIGHT,
      Maneuver.UNKNOWN -> Icons.Outlined.Straight
    }

/** The icon and the name, for screen readers, of where a destination comes from. */
private fun GuidanceDestinationKind.iconAndLabel(): Pair<ImageVector, Int> =
    when (this) {
      GuidanceDestinationKind.APPOINTMENT ->
          Icons.Outlined.Event to R.string.guidanceKindAppointment
      GuidanceDestinationKind.SAVED_PLACE ->
          Icons.Outlined.LocationOn to R.string.guidanceKindSavedPlace
      GuidanceDestinationKind.MEMORY -> Icons.Outlined.Photo to R.string.guidanceKindMemory
      GuidanceDestinationKind.SEARCH -> Icons.Outlined.Search to R.string.guidanceKindSearch
    }

private fun RoutePoint.toLatLng() = LatLng(lat, lng)

/** Sample places for the previews; the names are what a user would type. */
private val previewDestination = emptyDestination.copy(id = "pharmacy", name = "Pharmacy")
private val previewDestinations =
    listOf(
        previewDestination.copy(
            id = "doctor",
            name = "Dr. Martin",
            kind = GuidanceDestinationKind.APPOINTMENT,
        ),
        previewDestination,
        previewDestination.copy(
            id = "lake",
            name = "The lake",
            kind = GuidanceDestinationKind.MEMORY,
        ),
    )
private val previewChoosing =
    GuidanceUIState(
        destinations = previewDestinations,
        searchQuery = "EPFL",
        searchResults =
            listOf(
                previewDestination.copy(
                    name = "EPFL",
                    kind = GuidanceDestinationKind.SEARCH,
                    address = "EPFL, Lausanne",
                )
            ),
    )
private val previewGuiding =
    GuidancePhase.Guiding(
        previewDestination,
        emptyRoute,
        nextManeuver = Maneuver.LEFT,
        isOffRoute = true,
    )

/** Preview of [GuidanceChoosing] with a few places and a search, in light and dark mode. */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GuidanceChoosingPreview() {
  AmberAppTheme { GuidanceChoosing(previewChoosing) }
}

/** Preview of [GuidanceStepCard] and the other phases, in light and dark mode. */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GuidancePhasesPreview() {
  AmberAppTheme {
    Column(verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL)) {
      GuidanceStepCard(guiding = previewGuiding)
      GuidanceLoading(previewDestination.name)
      GuidanceArrived(previewDestination.name)
      GuidanceProblemContent(GuidanceProblem.NO_PERMISSION)
    }
  }
}

/** Preview of [GuidanceRouteMap]; the map itself only shows with Google Play services. */
@Preview(showBackground = true)
@Composable
internal fun GuidanceRouteMapPreview() {
  AmberAppTheme { GuidanceRouteMap(Modifier.fillMaxSize()) }
}
