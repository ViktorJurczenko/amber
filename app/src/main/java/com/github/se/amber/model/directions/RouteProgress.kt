// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.directions

import com.github.se.amber.model.location.EARTH_RADIUS_METERS
import com.github.se.amber.model.location.distanceMeters
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * How close to the destination, or to the end of the route, the patient must be to have arrived, in
 * meters: about the width of a street, within a phone's GPS error.
 */
const val ARRIVED_RADIUS_METERS = 20.0

/**
 * How far from the route line the patient can be before they count as off the route, in meters. A
 * phone's position jitters by 10 to 30 m in a street, so less would count them off while on it.
 */
const val OFF_ROUTE_METERS = 40.0

/**
 * How many steps ahead of the current one the patient can be found on. A route can pass near itself
 * again later on, and this keeps the patient from jumping to that later part.
 */
const val STEP_LOOKAHEAD = 2

/**
 * Where the patient is along a route.
 *
 * @param stepIndex the step they are walking, an index of [Route.steps]; 0 for a route without
 *   steps.
 * @param metersToStepEnd how far the end of that step is, where the next maneuver happens; for a
 *   route without steps, how far the destination is.
 * @param hasArrived whether they are at the destination.
 * @param isOffRoute whether they are further than [OFF_ROUTE_METERS] from the route line.
 */
data class RouteProgress(
    val stepIndex: Int,
    val metersToStepEnd: Int,
    val hasArrived: Boolean,
    val isOffRoute: Boolean,
)

/**
 * Where [position] is along this route, when the patient was last on step [currentStep].
 *
 * The step is the one whose line, from its start to its end, is nearest to [position], among
 * [currentStep] and the [STEP_LOOKAHEAD] after it: the patient never goes back a step. Between
 * steps as near, as at a corner, the earlier one is kept, so "turn left" shows until they have
 * turned.
 */
fun Route.progressAt(position: RoutePoint, currentStep: Int): RouteProgress {
  val end = points.lastOrNull() ?: destination
  val hasArrived =
      minOf(position.metersTo(destination), position.metersTo(end)) <= ARRIVED_RADIUS_METERS
  val isOffRoute = distanceToPathMeters(position, points) > OFF_ROUTE_METERS
  if (steps.isEmpty()) {
    return RouteProgress(0, position.metersTo(destination).roundToInt(), hasArrived, isOffRoute)
  }
  val first = currentStep.coerceIn(0, steps.lastIndex)
  val last = (first + STEP_LOOKAHEAD).coerceAtMost(steps.lastIndex)
  val stepIndex =
      (first..last).minBy { distanceToSegmentMeters(position, steps[it].start, steps[it].end) }
  val metersToStepEnd = position.metersTo(steps[stepIndex].end).roundToInt()
  return RouteProgress(stepIndex, metersToStepEnd, hasArrived, isOffRoute)
}

/**
 * The distance in meters from [position] to the nearest point of the line through [points]. With a
 * single point, the distance to it; with none, infinity.
 */
fun distanceToPathMeters(position: RoutePoint, points: List<RoutePoint>): Double =
    when (points.size) {
      0 -> Double.POSITIVE_INFINITY
      1 -> position.metersTo(points.single())
      else -> points.zipWithNext().minOf { (a, b) -> distanceToSegmentMeters(position, a, b) }
    }

/**
 * The distance in meters from [position] to the nearest point of the segment from [a] to [b].
 *
 * The points are laid flat around [position], which is off by far less than a phone's GPS error
 * over the length of a street.
 */
fun distanceToSegmentMeters(position: RoutePoint, a: RoutePoint, b: RoutePoint): Double {
  val (ax, ay) = a.metersFrom(position)
  val (bx, by) = b.metersFrom(position)
  val dx = bx - ax
  val dy = by - ay
  val lengthSquared = dx * dx + dy * dy
  // The share of the segment, from a, where the point nearest to position is
  val t =
      if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
  return hypot(ax + t * dx, ay + t * dy)
}

/** Distance in meters from this point to [other], along the Earth's surface. */
private fun RoutePoint.metersTo(other: RoutePoint): Double =
    distanceMeters(lat, lng, other.lat, other.lng)

/** This point in meters east and north of [origin], on a flat map around it. */
private fun RoutePoint.metersFrom(origin: RoutePoint): Pair<Double, Double> {
  val east =
      Math.toRadians(lng - origin.lng) * cos(Math.toRadians(origin.lat)) * EARTH_RADIUS_METERS
  val north = Math.toRadians(lat - origin.lat) * EARTH_RADIUS_METERS
  return east to north
}
