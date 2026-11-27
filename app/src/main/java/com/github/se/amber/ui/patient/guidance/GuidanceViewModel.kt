// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.guidance

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.appointment.AppointmentRepositoryProvider
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.circle.SavedPlacesRepository
import com.github.se.amber.data.circle.SavedPlacesRepositoryProvider
import com.github.se.amber.data.directions.RouteError
import com.github.se.amber.data.directions.RouteRepository
import com.github.se.amber.data.directions.RouteRepositoryProvider
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.LocationUpdatesRepository
import com.github.se.amber.data.location.LocationUpdatesRepositoryProvider
import com.github.se.amber.data.location.MediaLocationRepository
import com.github.se.amber.data.location.MediaLocationRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.occurrencesIn
import com.github.se.amber.model.calendar.CalendarStatus
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.statusAt
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.progressAt
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.isSamePlace
import com.github.se.amber.model.location.pointOrNull
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How many positions in a row must be off the route before a new route is asked for. One alone may
 * be the GPS jumping.
 */
internal const val OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE = 3

/**
 * How many new routes one guidance can ask for. Each counts toward the daily route limit, so a
 * patient walking the wrong way for long doesn't use them all.
 */
internal const val MAX_NEW_ROUTES = 3

/**
 * The worst accuracy, in meters, at which a position still moves the guidance on. A worse one is
 * too far off to tell which street the patient is in.
 */
internal const val GUIDANCE_MAX_ACCURACY_METERS = 50.0

/** How many days, from today, the next appointment is looked for in. Today only. */
internal const val GUIDANCE_APPOINTMENT_DAYS = 1

/** Where a destination comes from, so the screen can show it with its own icon. */
enum class GuidanceDestinationKind {
  APPOINTMENT,
  SAVED_PLACE,
  MEMORY,
  SEARCH,
}

/**
 * A place the patient can be guided to.
 *
 * @param id the id of what it comes from (appointment, saved place or pin), or its index in the
 *   search results.
 * @param name what the patient sees: the appointment title or the place name, as the user typed it.
 * @param point where to go.
 * @param kind where it comes from.
 */
data class GuidanceDestination(
    val id: String,
    val name: String,
    val point: RoutePoint,
    val kind: GuidanceDestinationKind,
)

/**
 * Why the patient can't be guided, with the message the screen shows.
 *
 * @property messageRes the message, as a string resource.
 */
enum class GuidanceProblem(@param:StringRes val messageRes: Int) {
  NO_PERMISSION(R.string.guidanceProblemNoPermission),
  LOCATION_OFF(R.string.guidanceProblemLocationOff),
  NO_POSITION(R.string.guidanceProblemNoPosition),
  OFFLINE(R.string.guidanceProblemOffline),
  NO_ROUTE(R.string.guidanceProblemNoRoute),
  DAILY_LIMIT(R.string.guidanceProblemDailyLimit),
  UNKNOWN(R.string.guidanceProblemUnknown),
}

/** Where the patient is in the guidance, from choosing where to go to arriving. */
sealed interface GuidancePhase {

  /** The patient picks where to go. */
  data object Choosing : GuidancePhase

  /** Finding the patient's position, then the route to [destination]. */
  data class LoadingRoute(val destination: GuidanceDestination) : GuidancePhase

  /** The route is found: the screen asks the patient to start, with its walk time. */
  data class Confirming(val destination: GuidanceDestination, val route: Route) : GuidancePhase

  /**
   * The patient walks [route] to [destination].
   *
   * @param stepIndex the step they are walking, an index of [Route.steps].
   * @param nextManeuver what to do at the end of that step; null when the destination comes next.
   * @param metersToNextManeuver how far the end of that step is.
   * @param isOffRoute whether they are away from the route line.
   * @param isLocationOff whether the location was turned off; guidance goes on once it is back on.
   * @param isFindingNewRoute whether a new route is being asked for, after they went off the route.
   */
  data class Guiding(
      val destination: GuidanceDestination,
      val route: Route,
      val stepIndex: Int = 0,
      val nextManeuver: Maneuver? = route.steps.getOrNull(1)?.maneuver,
      val metersToNextManeuver: Int =
          route.steps.firstOrNull()?.distanceMeters ?: route.distanceMeters,
      val isOffRoute: Boolean = false,
      val isLocationOff: Boolean = false,
      val isFindingNewRoute: Boolean = false,
  ) : GuidancePhase

  /** The patient is at [destination]. */
  data class Arrived(val destination: GuidanceDestination) : GuidancePhase

  /** The patient can't be guided to [destination]; null if none was chosen. */
  data class Problem(
      val problem: GuidanceProblem,
      val destination: GuidanceDestination? = null,
  ) : GuidancePhase
}

/**
 * UI state of the guidance screen.
 *
 * @param phase where the patient is in the guidance.
 * @param destinations the places to choose from: today's next appointment with a place, then the
 *   saved places, then the memory places, each by name.
 * @param searchQuery the address typed in the search field.
 * @param searchResults the places found for the last search.
 * @param isSearching whether a search is running.
 * @param searchErrorRes why the last search failed, as a string resource, or null.
 */
data class GuidanceUIState(
    val phase: GuidancePhase = GuidancePhase.Choosing,
    val destinations: List<GuidanceDestination> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<GuidanceDestination> = emptyList(),
    val isSearching: Boolean = false,
    @param:StringRes val searchErrorRes: Int? = null,
)

/**
 * Guides the patient step by step to a place that matters to them.
 *
 * The patient chooses a destination, the route is found from their position, and once they start,
 * each new position moves the guidance on: the next step, a new route when they keep walking the
 * wrong way, and arrival. The GPS only runs while finding the first position and while guiding, and
 * stops on arrival, on a problem, and when the patient stops.
 *
 * @param routeRepository finds the walking routes.
 * @param locationUpdatesRepository gives the patient's position, and whether they allowed it.
 * @param placeSearchRepository finds places from a typed address.
 * @param savedPlacesRepository gives the saved places of the circle.
 * @param mediaLocationRepository gives the memory places of the circle.
 * @param appointmentRepository gives today's appointments of the circle.
 * @param profileRepository gives the signed-in patient's circles.
 * @param activeCircleRepository picks which of their circles to use.
 * @param nowMillis gives the current time, so tests can choose it.
 * @param zone the time zone "today" is counted in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceViewModel(
    private val routeRepository: RouteRepository = RouteRepositoryProvider.default.repository,
    private val locationUpdatesRepository: LocationUpdatesRepository =
        LocationUpdatesRepositoryProvider.default.repository,
    private val placeSearchRepository: PlaceSearchRepository =
        PlaceSearchRepositoryProvider.default.repository,
    private val savedPlacesRepository: SavedPlacesRepository =
        SavedPlacesRepositoryProvider.default.repository,
    private val mediaLocationRepository: MediaLocationRepository =
        MediaLocationRepositoryProvider.default.repository,
    private val appointmentRepository: AppointmentRepository =
        AppointmentRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val activeCircleRepository: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(GuidanceUIState())
  val uiState: StateFlow<GuidanceUIState> = _uiState.asStateFlow()

  private var phase: GuidancePhase
    get() = _uiState.value.phase
    set(value) = _uiState.update { it.copy(phase = value) }

  // Finds the route, then follows the position while guiding
  private var guidanceJob: Job? = null
  private var newRouteJob: Job? = null
  private var offRoutePositions = 0
  private var newRoutes = 0
  // Only the last search may clear isSearching
  private var lastSearch = 0

  init {
    loadDestinations()
  }

  /** Stores [query] as the address to search for. */
  fun setSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query, searchErrorRes = null) }
  }

  /** Searches for the typed address. A blank one clears the results. */
  fun search() {
    val query = _uiState.value.searchQuery
    val search = ++lastSearch
    if (query.isBlank()) {
      _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
      return
    }
    _uiState.update { it.copy(isSearching = true, searchErrorRes = null) }
    viewModelScope.launch {
      val result = placeSearchRepository.search(query)
      if (search != lastSearch) return@launch // a newer search replaces it
      result
          .onSuccess { places ->
            val found = places.mapIndexedNotNull { index, place ->
              place.toSearchResult(index, query)
            }
            _uiState.update { it.copy(searchResults = found, isSearching = false) }
          }
          .onFailure { e ->
            Log.e(TAG, "Error searching for a place", e)
            _uiState.update {
              it.copy(isSearching = false, searchErrorRes = R.string.guidanceSearchError)
            }
          }
    }
  }

  /** Finds the route to [destination] from the patient's position, to confirm before starting. */
  fun chooseDestination(destination: GuidanceDestination) {
    stopJobs()
    val problem =
        when {
          !locationUpdatesRepository.hasPermission() -> GuidanceProblem.NO_PERMISSION
          !locationUpdatesRepository.isLocationEnabled() -> GuidanceProblem.LOCATION_OFF
          else -> null
        }
    if (problem != null) {
      phase = GuidancePhase.Problem(problem, destination)
      return
    }
    phase = GuidancePhase.LoadingRoute(destination)
    guidanceJob = viewModelScope.launch {
      val from = firstPosition(destination) ?: return@launch
      routeRepository
          .walkingRoute(from, destination.point)
          .onSuccess { phase = GuidancePhase.Confirming(destination, it) }
          .onFailure { phase = GuidancePhase.Problem(problemFor(it), destination) }
    }
  }

  /** Starts guiding on the confirmed route. Does nothing unless a route is being confirmed. */
  fun startGuidance() {
    val confirming = phase as? GuidancePhase.Confirming ?: return
    offRoutePositions = 0
    newRoutes = 0
    phase = GuidancePhase.Guiding(confirming.destination, confirming.route)
    guidanceJob = viewModelScope.launch {
      locationUpdatesRepository
          .observeLocation()
          .catch { e ->
            Log.e(TAG, "Error following the position", e)
            val problem =
                if (e is SecurityException) GuidanceProblem.NO_PERMISSION
                else GuidanceProblem.NO_POSITION
            phase = GuidancePhase.Problem(problem, confirming.destination)
          }
          .collect(::onLocationUpdate)
    }
  }

  /** Stops whatever is going on, the GPS included, and goes back to choosing where to go. */
  fun stop() {
    stopJobs()
    phase = GuidancePhase.Choosing
  }

  /**
   * Tries the destination of the current problem again, for example once the location is turned on.
   * Without one, goes back to choosing.
   */
  fun retry() {
    val destination = (phase as? GuidancePhase.Problem)?.destination ?: return stop()
    chooseDestination(destination)
  }

  /** The patient's position, or null after showing why it can't be found. */
  private suspend fun firstPosition(destination: GuidanceDestination): RoutePoint? {
    val problem =
        try {
          when (
              val update =
                  locationUpdatesRepository.observeLocation().first {
                    it != LocationUpdate.Searching
                  }
          ) {
            is LocationUpdate.Position -> return update.position.toRoutePoint()
            else -> GuidanceProblem.LOCATION_OFF
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: SecurityException) {
          Log.e(TAG, "No permission to read the position", e)
          GuidanceProblem.NO_PERMISSION
        } catch (e: Exception) {
          Log.e(TAG, "Error reading the position", e)
          GuidanceProblem.NO_POSITION
        }
    phase = GuidancePhase.Problem(problem, destination)
    return null
  }

  /** Moves the guidance on with [update]: the next step, arrival, or a new route. */
  private fun onLocationUpdate(update: LocationUpdate) {
    val guiding = phase as? GuidancePhase.Guiding ?: return
    val position = (update as? LocationUpdate.Position)?.position
    if (position == null || !position.isUsableForGuidance()) {
      phase = guiding.copy(isLocationOff = update == LocationUpdate.LocationOff)
      return
    }
    val point = position.toRoutePoint()
    val progress = guiding.route.progressAt(point, guiding.stepIndex)
    if (progress.hasArrived) {
      stopJobs()
      phase = GuidancePhase.Arrived(guiding.destination)
      return
    }
    offRoutePositions = if (progress.isOffRoute) offRoutePositions + 1 else 0
    val moved =
        guiding.copy(
            stepIndex = progress.stepIndex,
            nextManeuver = guiding.route.steps.getOrNull(progress.stepIndex + 1)?.maneuver,
            metersToNextManeuver = progress.metersToStepEnd,
            isOffRoute = progress.isOffRoute,
            isLocationOff = false,
        )
    phase = moved
    if (
        offRoutePositions >= OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE &&
            !moved.isFindingNewRoute &&
            newRoutes < MAX_NEW_ROUTES
    ) {
      findNewRoute(point, moved)
    }
  }

  /**
   * Asks for a new route from [from], where the patient went off [guiding]'s route. If none comes,
   * they keep the old one.
   */
  private fun findNewRoute(from: RoutePoint, guiding: GuidancePhase.Guiding) {
    newRoutes++
    offRoutePositions = 0
    phase = guiding.copy(isFindingNewRoute = true)
    newRouteJob = viewModelScope.launch {
      val result = routeRepository.walkingRoute(from, guiding.destination.point)
      val current = phase as? GuidancePhase.Guiding ?: return@launch
      result.onFailure { Log.e(TAG, "Error finding a new route", it) }
      phase =
          result.fold(
              onSuccess = { GuidancePhase.Guiding(current.destination, it) },
              onFailure = { current.copy(isFindingNewRoute = false) },
          )
    }
  }

  private fun stopJobs() {
    guidanceJob?.cancel()
    newRouteJob?.cancel()
    guidanceJob = null
    newRouteJob = null
  }

  /** Follows the active circle and keeps [GuidanceUIState.destinations] up to date. */
  private fun loadDestinations() {
    viewModelScope.launch {
      val profile =
          profileRepository.getCurrentProfile().getOrElse { e ->
            Log.e(TAG, "Error fetching profile", e)
            null
          } ?: return@launch
      activeCircleRepository
          .activeCircleId(profile.uid, profile.circleIds)
          .distinctUntilChanged()
          .flatMapLatest { circleId ->
            if (circleId == null) flowOf(emptyList()) else destinationsOf(circleId)
          }
          .collect { destinations -> _uiState.update { it.copy(destinations = destinations) } }
    }
  }

  /**
   * The destinations of [circleId], in the order of [GuidanceUIState.destinations]. Each source
   * shows as soon as it loads, and one that fails is left out without stopping the others.
   */
  private fun destinationsOf(circleId: String): Flow<List<GuidanceDestination>> =
      combine(
          nextAppointment(circleId).orOnError(null, "appointments"),
          savedPlacesRepository.observePlaces(circleId).orOnError(emptyList(), "saved places"),
          mediaLocationRepository.observeMarkers(circleId).orOnError(emptyList(), "memory places"),
      ) { appointment, places, pins ->
        val saved = places.mapNotNull { it.toDestination() }.sortedBy { it.name }
        // A memory at a saved place would show the same place twice
        val memories =
            pins
                .mapNotNull { it.toDestination() }
                .filter { memory -> saved.none { it.point.isSamePlaceAs(memory.point) } }
                .sortedBy { it.name }
        listOfNotNull(appointment?.toDestination()) + saved + memories
      }

  /** Today's next appointment that isn't over and has a place, kept up to date. */
  private fun nextAppointment(circleId: String): Flow<Appointment?> {
    val today = Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()
    val window = dayWindow(today, GUIDANCE_APPOINTMENT_DAYS, zone)
    return appointmentRepository
        .observeAppointments(circleId, today, GUIDANCE_APPOINTMENT_DAYS)
        .map { appointments ->
          val now = nowMillis()
          appointments
              .flatMap { it.value.occurrencesIn(window, zone) }
              .sortedBy { it.startAt }
              .firstOrNull {
                it.statusAt(now) != CalendarStatus.PASSED && it.location?.pointOrNull() != null
              }
        }
  }

  /** Starts with [fallback], and goes on with it, logged, if the flow fails. */
  private fun <T> Flow<T>.orOnError(fallback: T, what: String): Flow<T> = onStart {
    emit(fallback)
  }
      .catch { e ->
        Log.e(TAG, "Error loading the $what", e)
        emit(fallback)
      }

  private companion object {
    const val TAG = "GuidanceViewModel"
  }
}

/** The route error's message for the patient. */
private fun problemFor(error: Throwable): GuidanceProblem =
    when (error) {
      RouteError.Network -> GuidanceProblem.OFFLINE
      RouteError.NoRoute -> GuidanceProblem.NO_ROUTE
      RouteError.DailyLimit -> GuidanceProblem.DAILY_LIMIT
      else -> GuidanceProblem.UNKNOWN
    }

/** Whether this position is fresh and precise enough to move the guidance on. */
private fun DevicePosition.isUsableForGuidance(): Boolean =
    !isLastKnown && (accuracyMeters == null || accuracyMeters <= GUIDANCE_MAX_ACCURACY_METERS)

private fun DevicePosition.toRoutePoint() = RoutePoint(lat, lng)

private fun GeoLocation.toRoutePointOrNull(): RoutePoint? =
    pointOrNull()?.let { (lat, lng) -> RoutePoint(lat, lng) }

private fun RoutePoint.isSamePlaceAs(other: RoutePoint) =
    isSamePlace(lat, lng, other.lat, other.lng)

private fun Appointment.toDestination(): GuidanceDestination? =
    location?.toRoutePointOrNull()?.let {
      GuidanceDestination(id, title, it, GuidanceDestinationKind.APPOINTMENT)
    }

private fun Place.toDestination(): GuidanceDestination? =
    location.toRoutePointOrNull()?.let {
      GuidanceDestination(id, name, it, GuidanceDestinationKind.SAVED_PLACE)
    }

/** A pin without a place name is left out: the patient couldn't tell where it is. */
private fun MediaMarker.toDestination(): GuidanceDestination? =
    placeName
        ?.takeIf { it.isNotBlank() }
        ?.let { GuidanceDestination(id, it, RoutePoint(lat, lng), GuidanceDestinationKind.MEMORY) }

/** A search result, named by its place name, or by [query] without one. */
private fun GeoLocation.toSearchResult(index: Int, query: String): GuidanceDestination? =
    toRoutePointOrNull()?.let {
      GuidanceDestination(
          index.toString(),
          placeName?.takeIf { name -> name.isNotBlank() } ?: query,
          it,
          GuidanceDestinationKind.SEARCH,
      )
    }
