// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

/**
 * Names the place at a point on the map, so the patient can read where they are (e.g. "Rue de Bourg
 * 12") instead of only seeing a dot.
 */
fun interface AddressLookupRepository {

  /**
   * The address of the point at [lat], [lng], in a few words: the street and number, or else the
   * place or the town. It is null when nothing is known there.
   *
   * The returned [Result] fails if the lookup can't be done: no connection, no address lookup on
   * this device, or coordinates that aren't on Earth.
   */
  suspend fun addressOf(lat: Double, lng: Double): Result<String?>
}
