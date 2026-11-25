// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks that the time of the last check is kept in a real DataStore file. */
class ReminderCheckStoreTest {

  private fun TestScope.store(): ReminderCheckStore {
    val directory = createTempDirectory(prefix = "reminders")
    return DataStoreReminderCheckStore(
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { directory.resolve("reminders.preferences_pb").toFile() },
        )
    )
  }

  @Test fun isEmptyAtFirst() = runTest { assertNull(store().lastCheckedAt()) }

  @Test
  fun keepsTheTimeAndClearEmptiesIt() = runTest {
    val store = store()
    store.setLastCheckedAt(1_000L)
    store.setLastCheckedAt(2_000L)
    assertEquals(2_000L, store.lastCheckedAt())

    store.clear()

    assertNull(store.lastCheckedAt())
  }

  @Test
  fun aReadThatFailsCountsAsNothingStored() = runTest {
    val broken: DataStore<Preferences> = mockk {
      every { data } returns flow { throw IOException("disk") }
    }

    assertNull(DataStoreReminderCheckStore(broken).lastCheckedAt())
  }
}
