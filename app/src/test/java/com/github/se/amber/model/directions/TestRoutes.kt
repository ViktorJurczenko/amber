// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.directions

import com.github.se.amber.model.location.TestPlaces
import com.github.se.amber.model.location.TestPlaces.METERS_PER_DEGREE_OF_LATITUDE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import kotlin.math.cos

/**
 * A route shared by the guidance tests, and positions along it, in meters: walk [LEG_METERS] north,
 * turn right at [CORNER], then walk [LEG_METERS] east to [END].
 */
object TestRoutes {

  /**
   * How far distances measured on a flat map can be from the same ones on the sphere, in meters,
   * over the length of this route.
   */
  const val FLAT_MAP_TOLERANCE_METERS = 1.0

  /**
   * A step small enough to keep a position where it is, used to make positions that differ, since a
   * StateFlow drops one equal to the last.
   */
  const val JITTER_METERS = 1.0

  /** The length of each of the two legs, well above the guidance's own distances. */
  const val LEG_METERS = OFF_ROUTE_METERS * 5

  val START = RoutePoint(TestPlaces.LAUSANNE.latitude, TestPlaces.LAUSANNE.longitude)
  val CORNER = START.moved(north = LEG_METERS)
  val END = CORNER.moved(east = LEG_METERS)

  val L_ROUTE =
      Route(
          destination = END,
          distanceMeters = (2 * LEG_METERS).toInt(),
          durationSeconds = 300,
          points = listOf(START, CORNER, END),
          steps =
              listOf(
                  RouteStep(Maneuver.DEPART, LEG_METERS.toInt(), START, CORNER),
                  RouteStep(Maneuver.RIGHT, LEG_METERS.toInt(), CORNER, END),
              ),
      )

  /** Halfway along the first leg, so [LEG_METERS] / 2 from the corner. */
  val ON_FIRST_LEG = START.moved(north = LEG_METERS / 2)

  /** Halfway along the second leg. */
  val ON_SECOND_LEG = CORNER.moved(east = LEG_METERS / 2)

  /** Just inside the arrival radius of the end. */
  val AT_END = END.moved(north = ARRIVED_RADIUS_METERS / 2)

  /** Beside the first leg, twice as far as counts as off the route. */
  val OFF_FIRST_LEG = ON_FIRST_LEG.moved(east = -2 * OFF_ROUTE_METERS)

  /** This point moved [north] and [east] meters; negative to go south or west. */
  fun RoutePoint.moved(north: Double = 0.0, east: Double = 0.0): RoutePoint =
      RoutePoint(
          lat = lat + north / METERS_PER_DEGREE_OF_LATITUDE,
          lng = lng + east / (METERS_PER_DEGREE_OF_LATITUDE * cos(Math.toRadians(lat))),
      )
}
