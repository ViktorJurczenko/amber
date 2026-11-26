// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * The file of [DataStoreReminderCheckStore]. The check and sign-out both use it, and DataStore
 * needs a single instance per file, so it is declared once, here.
 *
 * A corrupt file is replaced with an empty one. Otherwise every read would fail, and no reminder
 * would ever come. The check then starts from the current time, as after a new install.
 */
internal val Context.reminderCheckDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "appointment_reminders",
        corruptionHandler = reminderCheckCorruptionHandler(),
    )

/** Replaces a corrupt file of [reminderCheckDataStore] with an empty one. */
internal fun reminderCheckCorruptionHandler() = ReplaceFileCorruptionHandler { emptyPreferences() }

/**
 * Remembers when this phone last checked the reminders of each user, so the next check shows only
 * what came due since then. It also remembers the reminders shown since then, so none is shown
 * twice while the check reads from the cache.
 *
 * When nothing is stored for a user, the check uses the current time. So a new install or a new
 * sign-in doesn't show old reminders. Each user has their own time, so two accounts used on the
 * same phone never share it.
 */
interface ReminderCheckStore {

  /**
   * @return the time of the last check for [uid], in epoch milliseconds, or null if none is stored.
   * @throws IOException if the time can't be read. The check then tries again later, since starting
   *   from the current time would skip the reminders due meanwhile.
   */
  suspend fun lastCheckedAt(uid: String): Long?

  /**
   * @return the keys of the reminders shown to [uid] since their last check, empty if none.
   * @throws IOException if they can't be read.
   */
  suspend fun shownSinceLastCheck(uid: String): Set<String>

  /** Adds [keys] to the reminders shown to [uid] since their last check. */
  suspend fun addShown(uid: String, keys: Set<String>)

  /**
   * Stores [millis] as the time of the last check for [uid], and forgets the reminders shown before
   * it, in one write.
   */
  suspend fun setLastCheckedAt(uid: String, millis: Long)

  /** Forgets the last check, and the reminders shown, of every user. Sign-out calls it. */
  suspend fun clear()
}

/** Stores the time of the last check in Preferences DataStore, like the active circle. */
class DataStoreReminderCheckStore(private val dataStore: DataStore<Preferences>) :
    ReminderCheckStore {

  override suspend fun lastCheckedAt(uid: String): Long? = dataStore.data.first()[key(uid)]

  override suspend fun shownSinceLastCheck(uid: String): Set<String> =
      dataStore.data.first()[shownKey(uid)].orEmpty()

  override suspend fun addShown(uid: String, keys: Set<String>) {
    if (keys.isEmpty()) return
    dataStore.edit { it[shownKey(uid)] = it[shownKey(uid)].orEmpty() + keys }
  }

  override suspend fun setLastCheckedAt(uid: String, millis: Long) {
    dataStore.edit {
      it[key(uid)] = millis
      it.remove(shownKey(uid))
    }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  /** The key of [uid]'s time, like the active circle's key. */
  private fun key(uid: String) = longPreferencesKey("last_checked_at_$uid")

  /** The key of the reminders shown to [uid] since their last check. */
  private fun shownKey(uid: String) = stringSetPreferencesKey("shown_since_check_$uid")
}
