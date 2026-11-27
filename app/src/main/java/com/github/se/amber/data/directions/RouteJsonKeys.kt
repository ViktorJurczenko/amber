// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

/**
 * The keys of a route as JSON, shared by the two readers: [FirebaseRouteRepository] reads the
 * getWalkingRoute function's answer, and [RouteFile] reads and writes the route kept on the phone.
 * Both use the same format, so one set of keys keeps them from drifting apart.
 */
internal object RouteJsonKeys {
  const val DESTINATION = "destination"
  const val DISTANCE = "distanceMeters"
  const val DURATION = "durationSeconds"
  const val POINTS = "points"
  const val STEPS = "steps"
  const val MANEUVER = "maneuver"
  const val START = "start"
  const val END = "end"
  const val LAT = "lat"
  const val LNG = "lng"
}
