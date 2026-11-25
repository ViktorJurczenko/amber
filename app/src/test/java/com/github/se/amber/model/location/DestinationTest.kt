// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.calendar.DEFAULT_DURATION_MINUTES
import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.JUST_INSIDE_SAME_PLACE_RADIUS
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import com.github.se.amber.model.location.TestPlaces.movedNorth
import com.github.se.amber.model.media.MediaItem
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationTest {

  /** Far enough from the other test places for its own pin and its own destination. */
  private val farAway = SAME_PLACE_RADIUS_METERS * 4

  private val home = Place(id = "home", name = "home", location = LAUSANNE)
  /** Capitalized, unlike [home]: the order must ignore case. */
  private val pharmacy = Place(id = "pharmacy", name = "Pharmacy", location = GENEVA)

  /** Any time; the appointments are placed around it. */
  private val now = 10.days.inWholeMilliseconds

  /** How long an appointment without a duration lasts. */
  private val defaultDuration = DEFAULT_DURATION_MINUTES.minutes.inWholeMilliseconds

  /** A doctor near [home], so close that it counts as the same place. */
  private val clinic = LAUSANNE.movedNorth(JUST_INSIDE_SAME_PLACE_RADIUS)

  private fun appointment(
      id: String = "a1",
      startAt: Long = now,
      location: GeoLocation? = clinic,
  ) = Appointment(id = id, title = "Dr. Martin", startAt = startAt, location = location)

  /** One pin per place, newest first, each with one picture. */
  private fun pins(vararg places: GeoLocation): List<MediaMarker> =
      places
          .mapIndexed { index, place ->
            MediaItem.Picture(id = "m$index", createdAt = -index.toLong(), location = place)
          }
          .toMediaMarkers()

  @Test
  fun listsSavedPlacesByNameThenMemories() {
    val market = GENEVA.movedNorth(farAway).copy(placeName = "Market")

    val result = destinations(listOf(pharmacy, home), pins(market))

    assertEquals(
        listOf(
            Destination(
                "home",
                DestinationKind.SAVED_PLACE,
                "home",
                LAUSANNE.latitude,
                LAUSANNE.longitude,
            ),
            Destination(
                "pharmacy",
                DestinationKind.SAVED_PLACE,
                "Pharmacy",
                GENEVA.latitude,
                GENEVA.longitude,
            ),
        ),
        result.dropLast(1),
    )
    assertEquals(
        listOf("Market" to DestinationKind.MEMORY),
        result.takeLast(1).map { it.name to it.kind },
    )
  }

  @Test
  fun leavesOutWhatCannotBeWalkedToOrIsAlreadyOffered() {
    val namedOnly = GeoLocation(placeName = "Grandma's house")
    val nearHome = LAUSANNE.movedNorth(JUST_INSIDE_SAME_PLACE_RADIUS).copy(placeName = "Flon")
    val unnamed = GENEVA.movedNorth(farAway).copy(placeName = null)
    // Pins never get a blank name from media, but the list doesn't count on it
    val blankName = pins(GENEVA.movedNorth(-farAway)).map { it.copy(placeName = " ") }

    val result =
        destinations(
            places = listOf(home, home.copy(id = "old", name = "Old flat", location = namedOnly)),
            memories = pins(nearHome, unnamed) + blankName,
            // At home, so it would hide it if the check came before the blank title
            nextAppointment = appointment().copy(title = " "),
        )

    assertEquals(listOf("home"), result.map { it.name })
  }

  @Test
  fun putsTheAppointmentFirstAndOffersItsPlaceOnce() {
    // Near the clinic but not near home: memories are checked against the appointment too
    val nearClinic =
        LAUSANNE.movedNorth(JUST_INSIDE_SAME_PLACE_RADIUS * 2).copy(placeName = "Waiting room")
    val market = GENEVA.movedNorth(farAway).copy(placeName = "Market")

    val result = destinations(listOf(home, pharmacy), pins(nearClinic, market), appointment())

    assertEquals(
        Destination(
            "a1",
            DestinationKind.APPOINTMENT,
            "Dr. Martin",
            clinic.latitude,
            clinic.longitude,
            startAt = now,
        ),
        result.first(),
    )
    assertEquals(listOf("Dr. Martin", "Pharmacy", "Market"), result.map { it.name })
  }

  @Test
  fun nextAppointmentIsTheFirstNotOverWithAPlace() {
    // Ends exactly now, so it's over
    val ended = appointment(id = "ended", startAt = now - defaultDuration)
    val noPlace = appointment(id = "noPlace", location = GeoLocation(placeName = "Clinic"))
    val halfway = appointment(id = "halfway", startAt = now - defaultDuration / 2)
    val later = appointment(id = "later", startAt = now + defaultDuration)

    assertEquals(
        "halfway",
        listOf(ended, noPlace, later, halfway).nextAppointmentWithPlace(now)?.id,
    )
    assertEquals("later", listOf(ended, noPlace, later).nextAppointmentWithPlace(now)?.id)
    assertNull(listOf(ended, noPlace).nextAppointmentWithPlace(now))
    // Two that start together: the same one whatever the order of the list
    val twin = appointment(id = "a0", startAt = later.startAt)
    assertEquals("a0", listOf(later, twin).nextAppointmentWithPlace(now)?.id)
    assertEquals("a0", listOf(twin, later).nextAppointmentWithPlace(now)?.id)
  }

  @Test
  fun anEarlierAppointmentWithoutATitleDoesntHideTheNextOne() {
    val untitled = appointment(id = "untitled").copy(title = " ")
    val drMartin = appointment(startAt = now + defaultDuration)

    val next = listOf(untitled, drMartin).nextAppointmentWithPlace(now)

    assertEquals("Dr. Martin", destinations(emptyList(), emptyList(), next).first().name)
  }

  @Test
  fun keepsOnlyTheNewestMemoryPlaces() {
    val places =
        (1..MAX_MEMORY_DESTINATIONS + 1).map { step ->
          GENEVA.movedNorth(farAway * step).copy(placeName = "Place $step")
        }

    val result = destinations(emptyList(), pins(*places.toTypedArray()))

    assertEquals(places.take(MAX_MEMORY_DESTINATIONS).map { it.placeName }, result.map { it.name })
  }

  @Test
  fun searchResultNeedsANameAndAPoint() {
    assertEquals(
        Destination("", DestinationKind.SEARCH, "Geneva", GENEVA.latitude, GENEVA.longitude),
        GENEVA.toSearchDestination(),
    )
    assertNull(GENEVA.copy(placeName = " ").toSearchDestination())
    assertNull(GeoLocation(placeName = "Geneva").toSearchDestination())
  }
}
