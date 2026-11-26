// Written by Neha Chakraborty and Claude Code
// Modified by MeiliV, with assistance from OpenAI Codex
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.circle

import android.util.Log
import com.github.se.amber.data.isValidDocumentId
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.user.Person
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation of [CareCircleRepository] and [SavedPlacesRepository]. */
class FirebaseCareCircleRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : CareCircleRepository, SavedPlacesRepository {

  // No replay: a refusal nobody collects is dropped. The buffer keeps a slow collector from losing
  // a burst of refusals, up to FAILURE_BUFFER of them.
  private val failures =
      MutableSharedFlow<PlaceWriteFailure>(
          extraBufferCapacity = FAILURE_BUFFER,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

  override val writeFailures: Flow<PlaceWriteFailure> = failures.asSharedFlow()

  override fun newId(): String = newId(firestore.collection(CARE_CIRCLES_COLLECTION))

  override suspend fun getCircle(circleId: String): Result<CareCircle> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }

    return circleCall {
      val circles = firestore.collection(CARE_CIRCLES_COLLECTION)
      val snapshot = circles.document(circleId).get().await()
      val circle =
          snapshot.takeIf { it.exists() }?.toObject(CareCircle::class.java)
              ?: return@circleCall Result.failure(CareCircleError.NotFound)

      Result.success(circle.copy(id = snapshot.id))
    }
  }

  override suspend fun addPerson(circleId: String, person: Person): Result<Person> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }

    if (!person.hasValidName()) {
      return Result.failure(CareCircleError.InvalidPersonName)
    }

    return circleCall {
      val circles = firestore.collection(CARE_CIRCLES_COLLECTION)
      val circleDocument = circles.document(circleId)
      val personId = newId(circles)
      val savedPerson =
          person.copy(
              id = personId,
              firstName = person.firstName.trim(),
              lastName = person.lastName.trim(),
              nickname = person.nickname?.trim()?.takeIf { it.isNotEmpty() },
          )

      // Writes one entry to CareCircle.people
      circleDocument.update(FieldPath.of("people", personId), savedPerson).await()
      Result.success(savedPerson)
    }
  }

  override fun observePlaces(circleId: String): Flow<List<Place>> {
    if (!isValidDocumentId(circleId)) {
      return flow { throw CareCircleError.InvalidCircleId }
    }

    return callbackFlow {
      val registration =
          circleDocument(circleId).addSnapshotListener { snapshot, error ->
            when {
              error != null -> close(error.toCareCircleError())
              snapshot != null -> trySend(snapshot.readPlaces())
            }
          }
      awaitClose { registration.remove() }
    }
        // The listener is on the whole circle: a nickname change sends the same places again
        .distinctUntilChanged()
        .conflate()
  }

  /**
   * Reads only CareCircle.places, one place at a time, so another field of the circle or one
   * unreadable place can't stop the list. A place this version of the app can't read is logged and
   * left out. The key is the place's id, even if the stored id field differs.
   */
  private fun DocumentSnapshot.readPlaces(): List<Place> =
      (get(PLACES_FIELD) as? Map<*, *>).orEmpty().keys.filterIsInstance<String>().mapNotNull {
          placeId ->
        try {
          get(FieldPath.of(PLACES_FIELD, placeId), Place::class.java)?.copy(id = placeId)
        } catch (exception: RuntimeException) {
          Log.w(TAG, "Couldn't read place $placeId of ${reference.path}", exception)
          null
        }
      }

  override suspend fun addPlace(circleId: String, place: Place): Result<Place> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }
    return circleCall {
      writePlace(
          circleId,
          place.copy(id = newId(firestore.collection(CARE_CIRCLES_COLLECTION))),
          PlaceWrite.ADD,
      )
    }
  }

  override suspend fun updatePlace(circleId: String, place: Place): Result<Place> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }
    if (!isValidDocumentId(place.id)) {
      return Result.failure(CareCircleError.InvalidPlaceId)
    }
    return circleCall { writePlace(circleId, place, PlaceWrite.UPDATE) }
  }

  override suspend fun deletePlace(circleId: String, placeId: String): Result<Unit> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }
    if (!isValidDocumentId(placeId)) {
      return Result.failure(CareCircleError.InvalidPlaceId)
    }
    return circleCall {
      startPlaceWrite(placeId, PlaceWrite.DELETE) {
        circleDocument(circleId).update(FieldPath.of(PLACES_FIELD, placeId), FieldValue.delete())
      }
      Result.success(Unit)
    }
  }

  /** Writes one entry of CareCircle.places, without waiting for the server (see [addPlace]). */
  private fun writePlace(circleId: String, place: Place, write: PlaceWrite): Result<Place> {
    if (!place.isValidToSave()) {
      return Result.failure(CareCircleError.InvalidPlace)
    }
    val savedPlace = place.copy(name = place.name.trim())
    startPlaceWrite(savedPlace.id, write) {
      circleDocument(circleId).update(FieldPath.of(PLACES_FIELD, savedPlace.id), savedPlace)
    }
    return Result.success(savedPlace)
  }

  private fun circleDocument(circleId: String) =
      firestore.collection(CARE_CIRCLES_COLLECTION).document(circleId)

  /**
   * Starts a write without waiting for the server: the phone saves it at once and sends it once
   * online. A refusal that comes later is reported in [writeFailures]. An exception thrown while
   * starting reaches the caller, through [circleCall].
   */
  private fun startPlaceWrite(placeId: String, write: PlaceWrite, start: () -> Task<Void>) {
    start().addOnFailureListener(DIRECT_EXECUTOR) { error ->
      failures.tryEmit(PlaceWriteFailure(placeId, write, error.toCareCircleError()))
    }
  }

  // To be used any time the same error catching pattern arises
  private suspend fun <T> circleCall(
      block: suspend () -> Result<T>,
  ): Result<T> =
      try {
        block()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFirestoreException) {
        Result.failure(exception.toCareCircleError())
      } catch (exception: Exception) {
        Result.failure(CareCircleError.Unknown(exception))
      }

  private fun newId(collection: CollectionReference): String = collection.document().id

  private fun Exception.toCareCircleError(): CareCircleError =
      when (this) {
        is CareCircleError -> this
        is FirebaseFirestoreException -> toCareCircleError()
        else -> CareCircleError.Unknown(this)
      }

  private fun FirebaseFirestoreException.toCareCircleError(): CareCircleError =
      when (code) {
        FirebaseFirestoreException.Code.NOT_FOUND -> CareCircleError.NotFound
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CareCircleError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> CareCircleError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CareCircleError.Network
        else -> CareCircleError.Unknown(this)
      }

  private companion object {
    const val TAG = "FirebaseCareCircleRepository"
    const val CARE_CIRCLES_COLLECTION = "careCircles"
    const val PLACES_FIELD = "places"

    /** How many refusals wait for a slow collector before the oldest is dropped. */
    const val FAILURE_BUFFER = 16

    // Runs the failure listener on the thread that completes the task: it only emits
    val DIRECT_EXECUTOR = Executor(Runnable::run)
  }
}
