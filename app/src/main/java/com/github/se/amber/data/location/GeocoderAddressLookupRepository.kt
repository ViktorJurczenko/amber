// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [AddressLookupRepository] backed by Android's [Geocoder]. It needs no API key, but it needs a
 * connection and a device with a geocoding service (any phone with Google Play services).
 *
 * @param findAddresses looks up at most `maxResults` addresses at a point; replaced in tests.
 */
class GeocoderAddressLookupRepository(
    private val findAddresses: suspend (lat: Double, lng: Double, maxResults: Int) -> List<Address>,
) : AddressLookupRepository {

  /** @param ioDispatcher where the blocking lookup of older Android runs; tests pass their own. */
  constructor(
      context: Context,
      ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ) : this(geocoderLookup(Geocoder(context), ioDispatcher))

  override suspend fun addressOf(lat: Double, lng: Double): Result<String?> =
      try {
        Result.success(findAddresses(lat, lng, MAX_RESULTS).firstOrNull()?.toShortAddress())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // the geocoder also throws IllegalArgumentException for coordinates out of range
        Result.failure(e)
      }

  companion object {
    /** Only the closest address is shown, so one is enough. */
    const val MAX_RESULTS = 1

    /**
     * Looks addresses up with [geocoder]. Both ways below need a connection.
     *
     * On Android 13 (API 33) and later, it uses the non-blocking call with a listener.
     *
     * Older Android doesn't have that call. There, it uses the blocking call, which Android marks
     * as deprecated. The call still works. It is only deprecated because it waits for the network
     * on the calling thread, so it runs on [ioDispatcher] to keep the screen responsive.
     *
     * We keep this older call because the app's minSdk is 28 (Android 9). Without it, phones on
     * Android 9 to 12 would never show an address. It can be removed if the minSdk is raised to 33.
     */
    internal fun geocoderLookup(
        geocoder: Geocoder,
        ioDispatcher: CoroutineDispatcher,
    ): suspend (Double, Double, Int) -> List<Address> = { lat, lng, maxResults ->
      if (!Geocoder.isPresent()) throw IOException("No address lookup on this device")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
          geocoder.getFromLocation(
              lat,
              lng,
              maxResults,
              object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) =
                    continuation.resume(addresses)

                override fun onError(errorMessage: String?) =
                    continuation.resumeWithException(IOException(errorMessage))
              },
          )
        }
      } else {
        // Android 9 to 12 have only this blocking call (see above)
        withContext(ioDispatcher) {
          @Suppress("DEPRECATION") geocoder.getFromLocation(lat, lng, maxResults).orEmpty()
        }
      }
    }
  }
}

/**
 * This address in a few words, short enough for a card: the street and number (e.g. "Rue de Bourg
 * 12"), or else the place's name, or else the town. Null if it has none of them.
 *
 * The full first address line isn't used: with the postcode and country it is too long to read at a
 * glance.
 */
internal fun Address.toShortAddress(): String? {
  val street = thoroughfare?.takeIf { it.isNotBlank() }
  if (street != null) {
    val number = subThoroughfare?.takeIf { it.isNotBlank() }
    return listOfNotNull(street, number).joinToString(" ")
  }
  return featureName?.takeIf { it.isNotBlank() } ?: locality?.takeIf { it.isNotBlank() }
}
