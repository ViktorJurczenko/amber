// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.directions

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressTest {

  // Distances computed on a flat map and on the sphere differ by far less than this
  private val toleranceMeters = 1.0

  @Test
  fun onTheFirstLeg_theFirstStepShowsWithTheDistanceToTheCorner() {
    val progress = L_ROUTE.progressAt(ON_FIRST_LEG, currentStep = 0)

    assertEquals(0, progress.stepIndex)
    assertEquals(LEG_METERS / 2, progress.metersToStepEnd.toDouble(), toleranceMeters)
    assertFalse(progress.hasArrived)
    assertFalse(progress.isOffRoute)
  }

  @Test
  fun atTheCorner_theTurnStillShows_andOnceTurnedTheNextStepDoes() {
    assertEquals(0, L_ROUTE.progressAt(CORNER, currentStep = 0).stepIndex)
    assertEquals(1, L_ROUTE.progressAt(ON_SECOND_LEG, currentStep = 0).stepIndex)
  }

  @Test
  fun aStepIsNeverGoneBack() {
    // Back on the first leg, after the turn: still the second step
    assertEquals(1, L_ROUTE.progressAt(ON_FIRST_LEG, currentStep = 1).stepIndex)
  }

  @Test
  fun aStepFurtherThanTheLookaheadIsNotJumpedTo() {
    // The steps within the lookahead are far away, and the one after comes back through START
    val far = START.moved(north = LEG_METERS)
    val further = far.moved(east = LEG_METERS)
    val steps =
        (0..STEP_LOOKAHEAD).map { RouteStep(Maneuver.STRAIGHT, LEG_METERS.toInt(), far, further) } +
            RouteStep(Maneuver.U_TURN, LEG_METERS.toInt(), further, START)
    val route = L_ROUTE.copy(steps = steps)

    assertTrue(route.progressAt(START, currentStep = 0).stepIndex <= STEP_LOOKAHEAD)
  }

  @Test
  fun nearTheEnd_theRouteIsArrived_andNotBefore() {
    assertTrue(L_ROUTE.progressAt(AT_END, currentStep = 1).hasArrived)
    assertFalse(L_ROUTE.progressAt(ON_SECOND_LEG, currentStep = 1).hasArrived)
  }

  @Test
  fun besideTheRoute_isOffRoute() {
    assertTrue(L_ROUTE.progressAt(OFF_FIRST_LEG, currentStep = 0).isOffRoute)
  }

  @Test
  fun withoutSteps_theDistanceIsToTheDestination() {
    val progress = L_ROUTE.copy(steps = emptyList()).progressAt(ON_SECOND_LEG, currentStep = 0)

    assertEquals(0, progress.stepIndex)
    assertEquals(LEG_METERS / 2, progress.metersToStepEnd.toDouble(), toleranceMeters)
  }

  @Test
  fun distanceToPath_isToTheNearestSegment_orItsEnd() {
    assertEquals(0.0, distanceToPathMeters(ON_FIRST_LEG, L_ROUTE.points), toleranceMeters)
    // Past the end of the line: the distance is to its end point
    val pastEnd = END.moved(east = LEG_METERS)
    assertEquals(LEG_METERS, distanceToPathMeters(pastEnd, L_ROUTE.points), toleranceMeters)
    assertEquals(LEG_METERS, distanceToPathMeters(START, listOf(CORNER)), toleranceMeters)
    assertEquals(Double.POSITIVE_INFINITY, distanceToPathMeters(START, emptyList()), 0.0)
    // A segment of no length is its point
    assertEquals(LEG_METERS, distanceToSegmentMeters(START, CORNER, CORNER), toleranceMeters)
  }
}
