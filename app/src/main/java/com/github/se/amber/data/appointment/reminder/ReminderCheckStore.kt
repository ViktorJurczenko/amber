// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * The file of [DataStoreReminderCheckStore]. The check and sign-out both use it, and DataStore
 * needs a single instance per file, so it is declared once, here.
 */
internal val Context.reminderCheckDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "appointment_reminders")

/**
 * Remembers when this phone last checked its reminders, so the next check shows only what came due
 * since then.
 *
 * When nothing is stored, the check uses the current time. So a new install or a new sign-in
 * doesn't show old reminders.
 */
interface ReminderCheckStore {

  /** @return the time of the last check, in epoch milliseconds, or null if none is stored. */
  suspend fun lastCheckedAt(): Long?

  /** Stores [millis] as the time of the last check. */
  suspend fun setLastCheckedAt(millis: Long)

  /** Forgets the last check. Sign-out calls it. */
  suspend fun clear()
}

/**
 * Stores the time of the last check in Preferences DataStore, like the active circle.
 *
 * A read that fails with [IOException] counts as nothing stored, so the check starts from the
 * current time again.
 */
class DataStoreReminderCheckStore(private val dataStore: DataStore<Preferences>) :
    ReminderCheckStore {

  override suspend fun lastCheckedAt(): Long? =
      try {
        dataStore.data.first()[LAST_CHECKED_AT]
      } catch (e: IOException) {
        null
      }

  override suspend fun setLastCheckedAt(millis: Long) {
    dataStore.edit { it[LAST_CHECKED_AT] = millis }
  }

  override suspend fun clear() {
    dataStore.edit { it.remove(LAST_CHECKED_AT) }
  }

  private companion object {
    val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
  }
}
