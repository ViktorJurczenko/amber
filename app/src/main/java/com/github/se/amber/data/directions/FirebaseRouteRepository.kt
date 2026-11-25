// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import android.util.Log
import com.github.se.amber.data.directions.RouteJsonKeys.DISTANCE
import com.github.se.amber.data.directions.RouteJsonKeys.DURATION
import com.github.se.amber.data.directions.RouteJsonKeys.END
import com.github.se.amber.data.directions.RouteJsonKeys.LAT
import com.github.se.amber.data.directions.RouteJsonKeys.LNG
import com.github.se.amber.data.directions.RouteJsonKeys.MANEUVER
import com.github.se.amber.data.directions.RouteJsonKeys.POINTS
import com.github.se.amber.data.directions.RouteJsonKeys.START
import com.github.se.amber.data.directions.RouteJsonKeys.STEPS
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.RouteStep
import com.github.se.amber.model.location.isSamePlace
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [RouteRepository] backed by the getWalkingRoute Cloud Function, which holds the Routes API key.
 * Only the two points are sent; the function doesn't store them.
 *
 * @param store keeps the last route, for offline.
 * @param functions the Firebase Functions instance.
 */
class FirebaseRouteRepository(
    private val store: RouteStore,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) : RouteRepository {

  override suspend fun walkingRoute(from: RoutePoint, to: RoutePoint): Result<Route> {
    val fetched = fetch(from, to)
    fetched.onSuccess { route ->
      try {
        store.save(route)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // The route still works now, just not offline later
        Log.w(TAG, "Could not keep the route on the phone", exception)
      }
    }
    if (fetched.exceptionOrNull() !in KEPT_ROUTE_ERRORS) return fetched
    val kept = store.load()?.takeIf { it.destination.isSamePlaceAs(to) }
    return kept?.let { Result.success(it) } ?: fetched
  }

  /** Asks the cloud function for the route. Never reads nor writes the route kept on the phone. */
  private suspend fun fetch(from: RoutePoint, to: RoutePoint): Result<Route> =
      try {
        val callable = functions.getHttpsCallable(FUNCTION)
        callable.setTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val data = mapOf(ORIGIN to from.toMap(), DESTINATION to to.toMap())
        val response = callable.call(data).await().data as? Map<*, *>
        response?.toRoute(to)?.let { Result.success(it) }
            ?: Result.failure(RouteError.InvalidAnswer)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFunctionsException) {
        Result.failure(exception.toRouteError())
      } catch (exception: Exception) {
        Result.failure(RouteError.Unknown(exception))
      }

  /** The route of the function's answer, or null if a field is missing or of the wrong type. */
  private fun Map<*, *>.toRoute(destination: RoutePoint): Route? {
    val points = (this[POINTS] as? List<*>)?.map { (it as? Map<*, *>)?.toPoint() ?: return null }
    val steps = (this[STEPS] as? List<*>)?.map { (it as? Map<*, *>)?.toStep() ?: return null }
    return Route(
        destination = destination,
        distanceMeters = (this[DISTANCE] as? Number)?.toInt() ?: return null,
        durationSeconds = (this[DURATION] as? Number)?.toInt() ?: return null,
        points = points?.takeIf { it.isNotEmpty() } ?: return null,
        steps = steps ?: return null,
    )
  }

  /** A step of the function's answer, or null if a field is missing or of the wrong type. */
  private fun Map<*, *>.toStep(): RouteStep? =
      RouteStep(
          maneuver = Maneuver.fromRoutesApi(this[MANEUVER] as? String),
          distanceMeters = (this[DISTANCE] as? Number)?.toInt() ?: return null,
          start = (this[START] as? Map<*, *>)?.toPoint() ?: return null,
          end = (this[END] as? Map<*, *>)?.toPoint() ?: return null,
      )

  /** A point of the function's answer, or null without two numbers. */
  private fun Map<*, *>.toPoint(): RoutePoint? {
    val lat = (this[LAT] as? Number)?.toDouble() ?: return null
    val lng = (this[LNG] as? Number)?.toDouble() ?: return null
    return RoutePoint(lat, lng)
  }

  /** This point as the function expects it. */
  private fun RoutePoint.toMap() = mapOf(LAT to lat, LNG to lng)

  /** Whether [other] counts as the same place, so a route to one serves the other. */
  private fun RoutePoint.isSamePlaceAs(other: RoutePoint) =
      isSamePlace(lat, lng, other.lat, other.lng)

  /**
   * The [RouteError] for a failed call. Only the [KEPT_ROUTE_ERRORS] let [walkingRoute] fall back
   * to the route kept on the phone.
   */
  private fun FirebaseFunctionsException.toRouteError(): RouteError =
      when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> RouteError.Network
        // The SDK reports a request that couldn't be sent as INTERNAL, with the IOException as
        // cause
        FirebaseFunctionsException.Code.INTERNAL ->
            if (cause is IOException) RouteError.Network else RouteError.Unknown(this)
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> RouteError.NotAuthenticated
        FirebaseFunctionsException.Code.NOT_FOUND ->
            if (reason() == NO_ROUTE) RouteError.NoRoute else RouteError.Unknown(this)
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
            if (reason() == DAILY_LIMIT) RouteError.DailyLimit else RouteError.Unknown(this)
        else -> RouteError.Unknown(this)
      }

  /** The reason the function gave in its error's details, such as [NO_ROUTE]. */
  private fun FirebaseFunctionsException.reason() = (details as? Map<*, *>)?.get(REASON)

  private companion object {
    const val TAG = "FirebaseRouteRepository"
    const val FUNCTION = "getWalkingRoute"
    const val ORIGIN = "origin"
    const val DESTINATION = "destination"
    const val REASON = "reason"
    const val NO_ROUTE = "NO_ROUTE"
    const val DAILY_LIMIT = "DAILY_LIMIT"

    /**
     * The failures where the kept route helps: the server can't be reached, or won't give more
     * routes today. Any other failure says something about this request, so an old route would hide
     * it.
     */
    val KEPT_ROUTE_ERRORS = setOf(RouteError.Network, RouteError.DailyLimit)
  }
}

/**
 * How long the app waits for a route. A patient is waiting at the door: past this, the kept route
 * or an error is better than waiting. The function gives up on the Routes API sooner
 * (ROUTES_TIMEOUT_MS), so it can still answer "unavailable" in time.
 */
internal const val CALL_TIMEOUT_SECONDS = 20L
