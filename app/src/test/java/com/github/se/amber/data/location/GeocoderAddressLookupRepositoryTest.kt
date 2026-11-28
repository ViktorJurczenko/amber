// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowGeocoder
import org.robolectric.util.ReflectionHelpers

/**
 * Checks [GeocoderAddressLookupRepository] with a fake lookup in place of the real [Geocoder], so
 * no network is used. Robolectric gives it Android's [Address], and a [Geocoder] that answers what
 * the test sets.
 */
@RunWith(RobolectricTestRunner::class)
class GeocoderAddressLookupRepositoryTest {

  private val sdkInt = Build.VERSION.SDK_INT

  @After
  fun restoreDevice() {
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdkInt)
    ShadowGeocoder.setIsPresent(true)
  }

  /** Runs [block] as on Android 12, which only has the blocking lookup. */
  private fun onAndroid12(block: suspend TestScope.() -> Unit) {
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S)
    runTest { block() }
  }

  private fun address(
      street: String? = "Rue de Bourg",
      number: String? = "12",
      place: String? = null,
      town: String? = "Lausanne",
  ) =
      Address(Locale.ENGLISH).apply {
        latitude = LAT
        longitude = LNG
        thoroughfare = street
        subThoroughfare = number
        featureName = place
        locality = town
        setAddressLine(0, "Rue de Bourg 12, 1003 Lausanne, Switzerland")
      }

  /** A repository whose lookup answers [addresses]. */
  private fun answering(vararg addresses: Address) = GeocoderAddressLookupRepository { _, _, _ ->
    addresses.toList()
  }

  // ---------- found ----------

  @Test
  fun aStreetAndNumberAreTheShortAddress() = runTest {
    val name = answering(address()).addressOf(LAT, LNG).getOrThrow()

    assertEquals("Rue de Bourg 12", name)
  }

  @Test
  fun aStreetWithoutNumberIsJustTheStreet() = runTest {
    val name = answering(address(number = " ")).addressOf(LAT, LNG).getOrThrow()

    assertEquals("Rue de Bourg", name)
    assertEquals("Rue de Bourg", address(number = null).toShortAddress())
  }

  @Test
  fun withoutStreetThePlaceIsNamedThenTheTown() {
    assertEquals("Ouchy", address(street = null, place = "Ouchy").toShortAddress())
    assertEquals("Lausanne", address(street = "", place = "").toShortAddress())
    assertEquals("Lausanne", address(street = null, place = null).toShortAddress())
  }

  @Test
  fun blankPlaceAndTownAreNoName() {
    assertNull(address(street = null, place = " ", town = " ").toShortAddress())
  }

  @Test
  fun onlyTheClosestAddressIsAskedForAtTheGivenPoint() = runTest {
    val asked = mutableListOf<Triple<Double, Double, Int>>()
    val repository = GeocoderAddressLookupRepository { lat, lng, max ->
      asked += Triple(lat, lng, max)
      listOf(address(street = "Avenue de Rhodanie"), address())
    }

    assertEquals("Avenue de Rhodanie 12", repository.addressOf(LAT, LNG).getOrThrow())
    assertEquals(listOf(Triple(LAT, LNG, 1)), asked)
  }

  // ---------- nothing ----------

  @Test
  fun noAddressAtThePointIsANullName() = runTest {
    assertNull(answering().addressOf(LAT, LNG).getOrThrow())
  }

  @Test
  fun anAddressWithNoShortNameIsANullName() = runTest {
    val nameless = address(street = null, number = null, place = null, town = null)

    assertNull(answering(nameless).addressOf(LAT, LNG).getOrThrow())
  }

  // ---------- failure ----------

  @Test
  fun aFailedLookupIsAFailedResult() = runTest {
    val repository = GeocoderAddressLookupRepository { _, _, _ -> throw IOException("offline") }

    assertTrue(repository.addressOf(LAT, LNG).exceptionOrNull() is IOException)
  }

  @Test
  fun aCancelledLookupStaysCancelled() = runTest {
    val repository = GeocoderAddressLookupRepository { _, _, _ ->
      throw CancellationException("left")
    }

    val error = runCatching { repository.addressOf(LAT, LNG) }.exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  // ---------- Android's Geocoder, answered by Robolectric, not the network ----------

  private val geocoder = Geocoder(ApplicationProvider.getApplicationContext())

  @Test
  fun theGeocoderAnswersTheNonBlockingLookup() = runTest {
    shadowOf(geocoder).setFromLocation(listOf(address()))
    val repository =
        GeocoderAddressLookupRepository(
            GeocoderAddressLookupRepository.geocoderLookup(geocoder, StandardTestDispatcher())
        )

    assertEquals("Rue de Bourg 12", repository.addressOf(LAT, LNG).getOrThrow())
  }

  @Test
  fun aGeocoderErrorIsAFailedResult() = runTest {
    shadowOf(geocoder).setErrorMessage("offline")
    val repository =
        GeocoderAddressLookupRepository(
            GeocoderAddressLookupRepository.geocoderLookup(geocoder, StandardTestDispatcher())
        )

    val error = repository.addressOf(LAT, LNG).exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals("offline", error?.message)
  }

  @Test
  fun aDeviceWithoutAddressLookupFails() = runTest {
    ShadowGeocoder.setIsPresent(false)
    val repository =
        GeocoderAddressLookupRepository(ApplicationProvider.getApplicationContext<Context>())

    assertTrue(repository.addressOf(LAT, LNG).exceptionOrNull() is IOException)
  }

  // the blocking lookup is deprecated, but it is the only one Android 9 to 12 have
  @Suppress("DEPRECATION")
  @Test
  fun olderAndroidUsesTheBlockingLookupOnTheGivenDispatcher() = onAndroid12 {
    val blocking = mockk<Geocoder>()
    every { blocking.getFromLocation(LAT, LNG, 1) } returns mutableListOf(address())
    val lookup =
        GeocoderAddressLookupRepository.geocoderLookup(
            blocking,
            StandardTestDispatcher(testScheduler),
        )

    assertEquals(
        "Rue de Bourg 12",
        GeocoderAddressLookupRepository(lookup).addressOf(LAT, LNG).getOrThrow(),
    )
  }

  @Suppress("DEPRECATION")
  @Test
  fun olderAndroidFindingNothingIsANullName() = onAndroid12 {
    val blocking = mockk<Geocoder>()
    every { blocking.getFromLocation(any(), any(), any()) } returns null
    val lookup =
        GeocoderAddressLookupRepository.geocoderLookup(
            blocking,
            StandardTestDispatcher(testScheduler),
        )

    assertNull(GeocoderAddressLookupRepository(lookup).addressOf(LAT, LNG).getOrThrow())
  }

  private companion object {
    const val LAT = 46.5205
    const val LNG = 6.6336
  }
}
