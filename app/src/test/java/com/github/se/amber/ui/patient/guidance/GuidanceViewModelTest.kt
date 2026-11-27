// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.guidance

import com.github.se.amber.R
import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.SavedPlacesRepository
import com.github.se.amber.data.directions.RouteError
import com.github.se.amber.data.directions.RouteRepository
import com.github.se.amber.data.location.FakeLocationUpdatesRepository
import com.github.se.amber.data.location.FakeMediaLocationRepository
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.TestRoutes.AT_END
import com.github.se.amber.model.directions.TestRoutes.CORNER
import com.github.se.amber.model.directions.TestRoutes.END
import com.github.se.amber.model.directions.TestRoutes.LEG_METERS
import com.github.se.amber.model.directions.TestRoutes.L_ROUTE
import com.github.se.amber.model.directions.TestRoutes.OFF_FIRST_LEG
import com.github.se.amber.model.directions.TestRoutes.ON_FIRST_LEG
import com.github.se.amber.model.directions.TestRoutes.ON_SECOND_LEG
import com.github.se.amber.model.directions.TestRoutes.START
import com.github.se.amber.model.directions.TestRoutes.moved
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.SAME_PLACE_RADIUS_METERS
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceViewModelTest {

  /**
   * A [RouteRepository] that records what it is asked, and answers with [answer]: [L_ROUTE] unless
   * the test changes it, for example to wait on a [CompletableDeferred].
   */
  private class FakeRouteRepository : RouteRepository {
    val requests = mutableListOf<Pair<RoutePoint, RoutePoint>>()
    var answer: suspend () -> Result<Route> = { Result.success(L_ROUTE) }

    override suspend fun walkingRoute(from: RoutePoint, to: RoutePoint): Result<Route> {
      requests += from to to
      return answer()
    }
  }

  private val circle = CareCircle(id = "circle1", patientId = "patient")
  private val patient =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )
  private val today = LocalDate.of(2026, 11, 27)
  private val now = today.atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant().toEpochMilli()

  private val routes = FakeRouteRepository()
  private val location = FakeLocationUpdatesRepository(permission = true)
  private val appointments = FakeAppointmentRepository(ZoneOffset.UTC)
  private val pins = FakeMediaLocationRepository()
  private val savedPlaces = mockk<SavedPlacesRepository>()
  private val profiles = mockk<ProfileRepository>()
  private val activeCircle = mockk<ActiveCircleRepository>()
  private var search = PlaceSearchRepository { Result.success(emptyList()) }

  private val endPlace =
      GuidanceDestination("end", "Pharmacy", END, GuidanceDestinationKind.SAVED_PLACE)
  // Precise enough to move the guidance on
  private val accuracy = (GUIDANCE_MAX_ACCURACY_METERS / 2).toFloat()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profiles.getCurrentProfile() } returns Result.success(patient)
    every { activeCircle.activeCircleId(any(), any()) } returns flowOf("circle1")
    every { savedPlaces.observePlaces(any()) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      GuidanceViewModel(
          routeRepository = routes,
          locationUpdatesRepository = location,
          placeSearchRepository = { search.search(it) },
          savedPlacesRepository = savedPlaces,
          mediaLocationRepository = pins,
          appointmentRepository = appointments,
          profileRepository = profiles,
          activeCircleRepository = activeCircle,
          nowMillis = { now },
          zone = ZoneOffset.UTC,
      )

  private fun moveTo(
      point: RoutePoint,
      isLastKnown: Boolean = false,
      accuracy: Float = this.accuracy,
  ) {
    location.updates.value =
        LocationUpdate.Position(DevicePosition(point.lat, point.lng, accuracy, isLastKnown))
  }

  private val GuidanceViewModel.phase
    get() = uiState.value.phase

  private val GuidanceViewModel.guiding
    get() = phase as GuidancePhase.Guiding

  /** A view model guiding to [endPlace] on [L_ROUTE], from [START]. */
  private fun guidingViewModel(): GuidanceViewModel {
    moveTo(START)
    return createViewModel().apply {
      chooseDestination(endPlace)
      startGuidance()
    }
  }

  private fun at(point: RoutePoint, name: String = "") = GeoLocation(point.lat, point.lng, name)

  @Test
  fun destinations_areTodaysNextAppointment_thenSavedPlaces_thenOtherNamedMemoryPlaces() {
    runBlocking {
      val hour = 1.hours.inWholeMilliseconds
      appointments.addAppointment(
          circle,
          Appointment(
              id = "passed",
              title = "Passed",
              startAt = now - 2 * hour,
              location = at(START),
          ),
      )
      appointments.addAppointment(
          circle,
          Appointment(id = "noPlace", title = "No place", startAt = now + hour),
      )
      appointments.addAppointment(
          circle,
          Appointment(
              id = "next",
              title = "Doctor",
              startAt = now + 2 * hour,
              location = at(CORNER),
          ),
      )
    }
    every { savedPlaces.observePlaces("circle1") } returns
        flowOf(
            listOf(
                Place("b", "Bakery", at(START)),
                Place("a", "Aunt", at(END)),
                Place("x", "No point", GeoLocation()),
            )
        )
    val nearAunt = END.moved(north = SAME_PLACE_RADIUS_METERS / 2)
    pins.markers.value =
        mapOf(
            "circle1" to
                listOf(
                    MediaMarker("m1", ON_FIRST_LEG.lat, ON_FIRST_LEG.lng, "Lake", emptyList()),
                    MediaMarker("m2", nearAunt.lat, nearAunt.lng, "Aunt's garden", emptyList()),
                    MediaMarker("m3", CORNER.lat, CORNER.lng, null, emptyList()),
                )
        )

    val destinations = createViewModel().uiState.value.destinations

    assertEquals(listOf("Doctor", "Aunt", "Bakery", "Lake"), destinations.map { it.name })
    assertEquals(
        listOf(
            GuidanceDestinationKind.APPOINTMENT,
            GuidanceDestinationKind.SAVED_PLACE,
            GuidanceDestinationKind.SAVED_PLACE,
            GuidanceDestinationKind.MEMORY,
        ),
        destinations.map { it.kind },
    )
    assertEquals(CORNER, destinations.first().point)
  }

  @Test
  fun aSourceThatFails_isLeftOut_andTheOthersStillShow() {
    every { savedPlaces.observePlaces("circle1") } returns
        flow { throw IllegalStateException("denied") }
    pins.markers.value =
        mapOf("circle1" to listOf(MediaMarker("m1", END.lat, END.lng, "Lake", emptyList())))

    assertEquals(listOf("Lake"), createViewModel().uiState.value.destinations.map { it.name })
  }

  @Test
  fun withoutPermissionOrLocation_aProblemShows_withoutTheGps_andRetryTriesAgain() {
    location.permission = false
    val viewModel = createViewModel()

    viewModel.chooseDestination(endPlace)
    assertEquals(GuidancePhase.Problem(GuidanceProblem.NO_PERMISSION, endPlace), viewModel.phase)

    location.permission = true
    location.updates.value = LocationUpdate.LocationOff
    viewModel.retry()
    assertEquals(GuidancePhase.Problem(GuidanceProblem.LOCATION_OFF, endPlace), viewModel.phase)
    assertTrue(routes.requests.isEmpty())

    moveTo(START)
    viewModel.retry()
    assertEquals(GuidancePhase.Confirming(endPlace, L_ROUTE), viewModel.phase)
    assertFalse(location.isRunning)
  }

  @Test
  fun choosing_waitsForThePosition_thenTheRoute_thenAsksToConfirm() {
    val route = CompletableDeferred<Result<Route>>()
    routes.answer = { route.await() }
    val viewModel = createViewModel()

    viewModel.chooseDestination(endPlace)
    assertEquals(GuidancePhase.LoadingRoute(endPlace), viewModel.phase)
    assertTrue(routes.requests.isEmpty())

    moveTo(START)
    assertEquals(listOf(START to END), routes.requests)
    assertFalse(location.isRunning)
    assertEquals(GuidancePhase.LoadingRoute(endPlace), viewModel.phase)

    route.complete(Result.success(L_ROUTE))
    assertEquals(GuidancePhase.Confirming(endPlace, L_ROUTE), viewModel.phase)
  }

  @Test
  fun eachRouteOrGpsFailure_showsItsProblem() {
    val routeErrors =
        mapOf(
            RouteError.Network to GuidanceProblem.OFFLINE,
            RouteError.NoRoute to GuidanceProblem.NO_ROUTE,
            RouteError.DailyLimit to GuidanceProblem.DAILY_LIMIT,
            RouteError.NotAuthenticated to GuidanceProblem.UNKNOWN,
        )
    moveTo(START)
    val viewModel = createViewModel()
    routeErrors.forEach { (error, problem) ->
      routes.answer = { Result.failure(error) }
      viewModel.chooseDestination(endPlace)
      assertEquals(GuidancePhase.Problem(problem, endPlace), viewModel.phase)
    }

    val gpsErrors =
        mapOf(
            SecurityException("permission taken back") to GuidanceProblem.NO_PERMISSION,
            IllegalStateException("no Play services") to GuidanceProblem.NO_POSITION,
        )
    gpsErrors.forEach { (error, problem) ->
      location.failure.value = error
      viewModel.chooseDestination(endPlace)
      assertEquals(GuidancePhase.Problem(problem, endPlace), viewModel.phase)
      location.failure.value = null
    }
  }

  @Test
  fun stoppingWhileTheRouteLoads_goesBackToChoosing_andTheLateRouteIsIgnored() {
    val route = CompletableDeferred<Result<Route>>()
    routes.answer = { route.await() }
    moveTo(START)
    val viewModel = createViewModel()
    viewModel.chooseDestination(endPlace)

    viewModel.stop()
    route.complete(Result.success(L_ROUTE))

    assertEquals(GuidancePhase.Choosing, viewModel.phase)
  }

  @Test
  fun guiding_followsTheSteps_thenArrives_andStopsTheGps() {
    val viewModel = guidingViewModel()
    assertTrue(location.isRunning)
    assertEquals(Maneuver.RIGHT, viewModel.guiding.nextManeuver)
    assertEquals(LEG_METERS.toInt(), viewModel.guiding.metersToNextManeuver)

    moveTo(ON_FIRST_LEG)
    assertEquals(0, viewModel.guiding.stepIndex)
    assertEquals(Maneuver.RIGHT, viewModel.guiding.nextManeuver)
    assertEquals(LEG_METERS / 2, viewModel.guiding.metersToNextManeuver.toDouble(), 1.0)

    moveTo(ON_SECOND_LEG)
    assertEquals(1, viewModel.guiding.stepIndex)
    assertNull("the destination comes next", viewModel.guiding.nextManeuver)

    moveTo(AT_END)
    assertEquals(GuidancePhase.Arrived(endPlace), viewModel.phase)
    assertFalse(location.isRunning)
  }

  @Test
  fun lastKnownAndImprecisePositions_doNotMoveTheGuidanceOn() {
    val viewModel = guidingViewModel()

    moveTo(AT_END, isLastKnown = true)
    moveTo(ON_SECOND_LEG, accuracy = (GUIDANCE_MAX_ACCURACY_METERS * 2).toFloat())

    assertEquals(0, viewModel.guiding.stepIndex)
  }

  @Test
  fun walkingOffTheRoute_asksForANewRoute_onlyAfterSeveralPositionsInARow() {
    val viewModel = guidingViewModel()
    val newRoute = L_ROUTE.copy(durationSeconds = L_ROUTE.durationSeconds * 2)
    routes.answer = { Result.success(newRoute) }
    routes.requests.clear()
    // Different points each time, as a StateFlow drops a repeated one
    val offPoints =
        (1..OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE).map { OFF_FIRST_LEG.moved(north = it.toDouble()) }

    offPoints.dropLast(1).forEach { moveTo(it) }
    assertTrue(viewModel.guiding.isOffRoute)
    assertTrue(routes.requests.isEmpty())

    // Back on the route starts the count again
    moveTo(ON_FIRST_LEG)
    offPoints.dropLast(1).forEach { moveTo(it.moved(east = -1.0)) }
    assertTrue(routes.requests.isEmpty())

    moveTo(offPoints.last())
    assertEquals(listOf(offPoints.last() to END), routes.requests)
    assertEquals(newRoute, viewModel.guiding.route)
    assertFalse(viewModel.guiding.isFindingNewRoute)
    assertFalse(viewModel.guiding.isOffRoute)
  }

  @Test
  fun newRoutes_stopAtTheLimit() {
    val viewModel = guidingViewModel()
    routes.requests.clear()

    repeat((MAX_NEW_ROUTES + 1) * OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE) {
      moveTo(OFF_FIRST_LEG.moved(north = it.toDouble()))
    }

    assertEquals(MAX_NEW_ROUTES, routes.requests.size)
    assertTrue(viewModel.guiding.isOffRoute)
  }

  @Test
  fun aNewRouteThatFails_keepsTheOldOne_andStoppingOrArrivingDuringOneEndsIt() {
    val viewModel = guidingViewModel()
    routes.answer = { Result.failure(RouteError.Network) }
    repeat(OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE) {
      moveTo(OFF_FIRST_LEG.moved(north = it.toDouble()))
    }
    assertEquals(L_ROUTE, viewModel.guiding.route)
    assertFalse(viewModel.guiding.isFindingNewRoute)

    // A new route still loading when the patient arrives, then when they stop
    val pending = CompletableDeferred<Result<Route>>()
    routes.answer = { pending.await() }
    repeat(OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE) {
      moveTo(OFF_FIRST_LEG.moved(east = -it.toDouble()))
    }
    assertTrue(viewModel.guiding.isFindingNewRoute)
    moveTo(AT_END)
    pending.complete(Result.success(L_ROUTE))
    assertEquals(GuidancePhase.Arrived(endPlace), viewModel.phase)

    val stopped = guidingViewModel()
    routes.answer = { CompletableDeferred<Result<Route>>().await() }
    repeat(OFF_ROUTE_POSITIONS_BEFORE_NEW_ROUTE) {
      moveTo(OFF_FIRST_LEG.moved(north = -it.toDouble()))
    }
    assertTrue(stopped.guiding.isFindingNewRoute)
    stopped.stop()
    assertEquals(GuidancePhase.Choosing, stopped.phase)
    assertFalse(location.isRunning)
  }

  @Test
  fun locationTurnedOffWhileGuiding_isShown_andGuidanceGoesOnOnceBack() {
    val viewModel = guidingViewModel()

    location.updates.value = LocationUpdate.LocationOff
    assertTrue(viewModel.guiding.isLocationOff)

    moveTo(ON_SECOND_LEG)
    assertFalse(viewModel.guiding.isLocationOff)
    assertEquals(1, viewModel.guiding.stepIndex)
  }

  @Test
  fun search_keepsPlacesWithAPoint_showsFailures_andABlankQueryClears() {
    search = PlaceSearchRepository {
      Result.success(
          listOf(at(END, "Lausanne, Switzerland"), GeoLocation(placeName = "Nowhere"), at(START))
      )
    }
    val viewModel = createViewModel()

    viewModel.setSearchQuery("Lausanne")
    viewModel.search()
    assertEquals(
        listOf("Lausanne, Switzerland", "Lausanne"),
        viewModel.uiState.value.searchResults.map { it.name },
    )
    assertFalse(viewModel.uiState.value.isSearching)

    search = PlaceSearchRepository { Result.failure(IllegalStateException("offline")) }
    viewModel.search()
    assertEquals(R.string.guidanceSearchError, viewModel.uiState.value.searchErrorRes)
    assertFalse(viewModel.uiState.value.isSearching)

    viewModel.setSearchQuery(" ")
    assertNull(viewModel.uiState.value.searchErrorRes)
    viewModel.search()
    assertTrue(viewModel.uiState.value.searchResults.isEmpty())
  }

  @Test
  fun anOlderSearchFinishingLate_doesNotReplaceTheNewerOne() {
    val older = CompletableDeferred<Result<List<GeoLocation>>>()
    search = PlaceSearchRepository { older.await() }
    val viewModel = createViewModel()
    viewModel.setSearchQuery("Lau")
    viewModel.search()
    assertTrue(viewModel.uiState.value.isSearching)

    search = PlaceSearchRepository { Result.success(listOf(at(END, "Lausanne"))) }
    viewModel.setSearchQuery("Lausanne")
    viewModel.search()
    older.complete(Result.success(listOf(at(START, "Laupen"))))

    assertEquals(listOf("Lausanne"), viewModel.uiState.value.searchResults.map { it.name })
    assertFalse(viewModel.uiState.value.isSearching)
  }
}
