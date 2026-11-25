// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint

/** Finds walking routes for guidance, and keeps the last one so it still works offline. */
fun interface RouteRepository {

  /**
   * The walking route from [from] to [to].
   *
   * Online, it comes from the server and is kept on the phone, replacing the one kept before. When
   * the server can't be reached, or the user asked for too many routes today, the route kept on the
   * phone is given instead if it goes to [to] (see isSamePlace). It may start elsewhere: guidance
   * leads the patient to it. Any other failure never gives the kept route.
   *
   * The returned [Result] fails with a [RouteError]: [RouteError.NoRoute] when there is no walking
   * route, [RouteError.Network] or [RouteError.DailyLimit] when no route to [to] is kept,
   * [RouteError.NotAuthenticated], [RouteError.InvalidAnswer], or [RouteError.Unknown].
   * Cancellation is rethrown.
   */
  suspend fun walkingRoute(from: RoutePoint, to: RoutePoint): Result<Route>
}

/** Typed failures returned by [RouteRepository]. */
sealed class RouteError(message: String) : Exception(message) {
  data object NoRoute : RouteError("There is no walking route to this place")

  data object Network : RouteError("The route service can't be reached")

  data object NotAuthenticated : RouteError("No user is signed in")

  /** The user asked for the most routes the server allows in a day. It starts again tomorrow. */
  data object DailyLimit : RouteError("Too many routes asked for today")

  /** The server answered, but not with a route this version of the app can read. */
  data object InvalidAnswer : RouteError("The route service gave an answer the app can't read")

  data class Unknown(val source: Throwable) : RouteError("The route could not be found")
}
