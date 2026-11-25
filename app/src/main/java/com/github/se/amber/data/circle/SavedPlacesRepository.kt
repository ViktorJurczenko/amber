// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.pointOrNull
import kotlinx.coroutines.flow.Flow

/**
 * Keeps the saved places of a care circle (see [CareCircle.places]). Failures are
 * [CareCircleError]s, as for the rest of the circle.
 */
interface SavedPlacesRepository {

  /**
   * Watches the saved places of the circle [circleId] (see [CareCircle.places]), in no particular
   * order. Emits them at once, then again on every change, until the collector stops. A place saved
   * on this phone shows up at once, even offline.
   *
   * Offline, the places come from what this phone has already loaded. Only the places are read, so
   * another field of the circle can't stop the list, and a place this version of the app can't read
   * is left out. A change elsewhere in the circle, such as a nickname, emits nothing.
   *
   * The flow ends with a [CareCircleError]: [CareCircleError.InvalidCircleId] right away for an
   * invalid id, and [CareCircleError.PermissionDenied] when the user can't read the circle, for
   * example after leaving it.
   */
  fun observePlaces(circleId: String): Flow<List<Place>>

  /**
   * Saves a new place in the circle [circleId] and returns it as saved: with a new id, and its name
   * trimmed. The id the caller gives is ignored.
   *
   * Unlike [CareCircleRepository.addPerson], it doesn't wait for the server: it returns once the
   * place is saved on this phone, which sends it when it is online. A caregiver saving the place
   * they stand in may have no signal there. Only caregivers may write places; the security rules
   * refuse anyone else. The phone then removes the place again, and the refusal is reported in
   * [writeFailures].
   *
   * Fails right away, without writing anything, with [CareCircleError.InvalidCircleId], or with
   * [CareCircleError.InvalidPlace] when the name is blank or the location has no point on the map.
   * Other failures are [CareCircleError]s too. Cancellation is rethrown.
   */
  suspend fun addPlace(circleId: String, place: Place): Result<Place>

  /**
   * Saves the changes to the existing place [place], for example a new name, and returns it as
   * saved, its name trimmed. Saved like [addPlace], with the same failures, and
   * [CareCircleError.InvalidPlaceId] when [Place.id] is not a valid id.
   */
  suspend fun updatePlace(circleId: String, place: Place): Result<Place>

  /**
   * Removes the place [placeId] from the circle [circleId]. Appointments keep their own copy of its
   * location, so they don't change. Saved like [addPlace]. Fails right away with
   * [CareCircleError.InvalidCircleId] or [CareCircleError.InvalidPlaceId]. Cancellation is
   * rethrown.
   */
  suspend fun deletePlace(circleId: String, placeId: String): Result<Unit>

  /**
   * The writes the server refused after [addPlace], [updatePlace] or [deletePlace] had returned
   * success, for example because the rules denied them. Each refusal is emitted once, to the
   * collectors there are at that moment.
   *
   * Only the writes made through this instance are reported, so the app must write and collect
   * through the same one, [SavedPlacesRepositoryProvider.default].
   *
   * Limit: a refusal is reported only while the app runs. A write made offline is still sent after
   * a restart. If the server refuses it then, nothing is reported.
   */
  val writeFailures: Flow<PlaceWriteFailure>
}

/** The kinds of writes [SavedPlacesRepository] makes. */
enum class PlaceWrite {
  /** Made by [SavedPlacesRepository.addPlace]. */
  ADD,

  /** Made by [SavedPlacesRepository.updatePlace]. */
  UPDATE,

  /** Made by [SavedPlacesRepository.deletePlace]. */
  DELETE,
}

/**
 * A write the server refused after the call had returned. Reported in
 * [SavedPlacesRepository.writeFailures].
 *
 * @property placeId the id of the place the write was for.
 * @property write the kind of write that was refused.
 * @property error why the server refused it, usually [CareCircleError.PermissionDenied].
 */
data class PlaceWriteFailure(
    val placeId: String,
    val write: PlaceWrite,
    val error: CareCircleError,
)

/**
 * Whether [SavedPlacesRepository] saves this place: it has a name to show and a real point the
 * patient can be walked to (see [pointOrNull]). The security rules check the same.
 */
fun Place.isValidToSave(): Boolean = name.isNotBlank() && location.pointOrNull() != null
