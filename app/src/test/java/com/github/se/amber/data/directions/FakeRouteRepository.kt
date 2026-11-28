// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.TestRoutes.L_ROUTE

/**
 * A [RouteRepository] that records what it is asked, and answers with [answer]: [L_ROUTE] unless
 * the test changes it, for example to wait on a `CompletableDeferred`.
 */
class FakeRouteRepository : RouteRepository {
  val requests = mutableListOf<Pair<RoutePoint, RoutePoint>>()
  var answer: suspend () -> Result<Route> = { Result.success(L_ROUTE) }

  override suspend fun walkingRoute(from: RoutePoint, to: RoutePoint): Result<Route> {
    requests += from to to
    return answer()
  }
}
