// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import android.util.Log
import com.github.se.amber.data.directions.RouteJsonKeys.DESTINATION
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
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Where the last route is kept on the phone. */
interface RouteStore {

  /** Keeps [route], replacing the one kept before. Throws an [IOException] if it can't. */
  suspend fun save(route: Route)

  /** The route kept, or null if there is none or it can't be read. */
  suspend fun load(): Route?
}

/**
 * Keeps the last route as JSON in [file], in the app's cache: losing it only means no route
 * offline. Written to a temporary file first, then moved over [file] in one step, so an app killed
 * mid-write never leaves half a route, and two saves at once leave one whole route. A save that
 * fails leaves no temporary file behind.
 */
class RouteFile(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RouteStore {

  override suspend fun save(route: Route): Unit =
      withContext(ioDispatcher) {
        // A unique name per save: two saves at once, or a file left by a killed app, never share it
        val temporary = File.createTempFile(file.name, TEMPORARY_SUFFIX, file.parentFile)
        try {
          temporary.writeText(route.toJson().toString())
          // In one step, replacing the kept route on every platform, unlike File.renameTo
          Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
          // Nothing to delete once moved
          if (!temporary.delete() && temporary.exists()) {
            Log.w(TAG, "Couldn't delete ${temporary.name}")
          }
        }
      }

  override suspend fun load(): Route? =
      withContext(ioDispatcher) {
        if (!file.exists()) return@withContext null
        try {
          JSONObject(file.readText()).toRoute()
        } catch (_: JSONException) {
          // An older or broken file: as if none was kept
          null
        } catch (_: IOException) {
          null
        }
      }

  /** This route as the JSON kept in the file. */
  private fun Route.toJson() =
      JSONObject()
          .put(DESTINATION, destination.toJson())
          .put(DISTANCE, distanceMeters)
          .put(DURATION, durationSeconds)
          .put(POINTS, JSONArray(points.map { it.toJson() }))
          .put(STEPS, JSONArray(steps.map { it.toJson() }))

  /** This step as JSON, its maneuver by name. */
  private fun RouteStep.toJson() =
      JSONObject()
          .put(MANEUVER, maneuver.name)
          .put(DISTANCE, distanceMeters)
          .put(START, start.toJson())
          .put(END, end.toJson())

  /** This point as JSON. */
  private fun RoutePoint.toJson() = JSONObject().put(LAT, lat).put(LNG, lng)

  /** The route kept in this JSON. Throws a [JSONException] if a field is missing. */
  private fun JSONObject.toRoute() =
      Route(
          destination = getJSONObject(DESTINATION).toPoint(),
          distanceMeters = getInt(DISTANCE),
          durationSeconds = getInt(DURATION),
          points = getJSONArray(POINTS).objects().map { it.toPoint() },
          steps = getJSONArray(STEPS).objects().map { it.toStep() },
      )

  /** A step kept in this JSON. A maneuver this version doesn't know is [Maneuver.UNKNOWN]. */
  private fun JSONObject.toStep() =
      RouteStep(
          maneuver = Maneuver.entries.find { it.name == getString(MANEUVER) } ?: Maneuver.UNKNOWN,
          distanceMeters = getInt(DISTANCE),
          start = getJSONObject(START).toPoint(),
          end = getJSONObject(END).toPoint(),
      )

  /** A point kept in this JSON. */
  private fun JSONObject.toPoint() = RoutePoint(getDouble(LAT), getDouble(LNG))

  /** The objects of this array, in order. */
  private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

  private companion object {
    const val TAG = "RouteFile"
    const val TEMPORARY_SUFFIX = ".tmp"
  }
}

/**
 * The name of the file in the app's cache that keeps the last route. Sign-out and account deletion
 * delete it (see DeviceLocalPhone): it holds where the patient lives and goes.
 */
const val LAST_ROUTE_FILE_NAME = "last_route.json"
