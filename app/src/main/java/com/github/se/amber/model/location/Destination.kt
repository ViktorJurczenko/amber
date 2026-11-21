// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.calendar.endAt

/** Where a destination comes from, so the screen can show a fitting icon. */
enum class DestinationKind {
  APPOINTMENT,
  SAVED_PLACE,
  MEMORY,
  SEARCH,
}

/**
 * A place the patient can be walked to, as "Go somewhere" lists it. Built on the phone, never
 * stored.
 *
 * @param id the id of what it comes from: the appointment, the saved place or the memories' pin.
 *   Unique for its [kind] only; a search result has an empty id.
 * @param name what the button says: the appointment's title, the saved place's name, or the place
 *   name of the memories or the search result.
 * @param lat latitude of the point the patient is walked to.
 * @param lng longitude of that point.
 * @param startAt when the appointment starts, in epoch milliseconds, so the button can show the
 *   time. Null for every other kind.
 */
data class Destination(
    val id: String,
    val kind: DestinationKind,
    val name: String,
    val lat: Double,
    val lng: Double,
    val startAt: Long? = null,
)

/**
 * The most places from memories in the list: the patient finds many choices hard, and the saved
 * places come first anyway.
 */
const val MAX_MEMORY_DESTINATIONS = 5

/**
 * The destinations to offer, in this order: the [nextAppointment], then the saved [places] by name,
 * then up to [MAX_MEMORY_DESTINATIONS] places from [memories], newest first.
 *
 * Left out: whatever has no real point (see [GeoLocation.pointOrNull]), an appointment with a blank
 * title, memories without a place name, and whatever is within [SAME_PLACE_RADIUS_METERS] of an
 * earlier destination, so the same pharmacy isn't offered twice. An appointment at a saved place is
 * offered once, as the appointment, since the patient is going there for it.
 *
 * @param memories the pins of the memories map, newest first, as MediaLocationRepository gives
 *   them.
 * @param nextAppointment the appointment to offer first, as [nextAppointmentWithPlace] picks it.
 */
fun destinations(
    places: List<Place>,
    memories: List<MediaMarker>,
    nextAppointment: Appointment? = null,
): List<Destination> {
  val appointment = listOfNotNull(nextAppointment?.toDestination())
  val saved =
      places
          .sortedBy { it.name.lowercase() }
          .mapNotNull { it.location.toDestination(it.id, DestinationKind.SAVED_PLACE, it.name) }
          .filter { place -> appointment.none { it.isNear(place) } }
  val fromMemories =
      memories
          .mapNotNull { pin ->
            pin.placeName
                ?.takeIf { it.isNotBlank() }
                ?.let { Destination(pin.id, DestinationKind.MEMORY, it, pin.lat, pin.lng) }
          }
          .filter { memory -> (appointment + saved).none { it.isNear(memory) } }
          .take(MAX_MEMORY_DESTINATIONS)
  return appointment + saved + fromMemories
}

/**
 * The appointment the patient may go to next: the one that starts first among those that haven't
 * ended at [nowMillis] and have a real point. One that has started counts, since the patient may be
 * late. Null if there is none.
 *
 * @param nowMillis the current time, in epoch milliseconds. It is a parameter so that tests can
 *   choose it.
 */
fun List<Appointment>.nextAppointmentWithPlace(nowMillis: Long): Appointment? {
  val notOverWithPlace = filter { it.endAt() > nowMillis && it.location?.pointOrNull() != null }
  return notOverWithPlace.minByOrNull { it.startAt }
}

/** A place found by the address search, or null if it has no real point or no name to show. */
fun GeoLocation.toSearchDestination(): Destination? =
    placeName?.takeIf { it.isNotBlank() }?.let { toDestination("", DestinationKind.SEARCH, it) }

private fun Appointment.toDestination(): Destination? =
    title
        .takeIf { it.isNotBlank() }
        ?.let { location?.toDestination(id, DestinationKind.APPOINTMENT, it) }
        ?.copy(startAt = startAt)

private fun GeoLocation.toDestination(
    id: String,
    kind: DestinationKind,
    name: String,
): Destination? = pointOrNull()?.let { (lat, lng) -> Destination(id, kind, name, lat, lng) }

/** Whether [other] is within [SAME_PLACE_RADIUS_METERS], so counts as the same place. */
private fun Destination.isNear(other: Destination): Boolean =
    distanceMeters(lat, lng, other.lat, other.lng) <= SAME_PLACE_RADIUS_METERS
