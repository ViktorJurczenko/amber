// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Checks that the time of the last check is kept in a real DataStore file, for each user. */
class ReminderCheckStoreTest {

  private val file = createTempDirectory(prefix = "reminders").resolve("r.preferences_pb").toFile()

  private fun TestScope.store(file: File = this@ReminderCheckStoreTest.file): ReminderCheckStore =
      DataStoreReminderCheckStore(
          PreferenceDataStoreFactory.create(
              corruptionHandler = reminderCheckCorruptionHandler(),
              scope = backgroundScope,
              produceFile = { file },
          )
      )

  @Test fun isEmptyAtFirst() = runTest { assertNull(store().lastCheckedAt(ANNA)) }

  @Test
  fun keepsTheTimeAndClearEmptiesIt() = runTest {
    val store = store()
    store.setLastCheckedAt(ANNA, 1_000L)
    store.setLastCheckedAt(ANNA, 2_000L)
    assertEquals(2_000L, store.lastCheckedAt(ANNA))

    store.clear()

    assertNull(store.lastCheckedAt(ANNA))
  }

  @Test
  fun eachAccountHasItsOwnLastCheck() = runTest {
    val store = store()
    store.setLastCheckedAt(ANNA, 1_000L)
    store.setLastCheckedAt(BEN, 2_000L)

    assertEquals(1_000L, store.lastCheckedAt(ANNA))
    assertEquals(2_000L, store.lastCheckedAt(BEN))

    // sign-out forgets every account's time
    store.clear()
    assertNull(store.lastCheckedAt(ANNA))
    assertNull(store.lastCheckedAt(BEN))
  }

  @Test
  fun keepsTheShownRemindersOfEachUserUntilTheirNextCheck() = runTest {
    val store = store()
    assertEquals(emptySet<String>(), store.shownSinceLastCheck(ANNA))
    store.addShown(ANNA, setOf("a"))
    store.addShown(ANNA, emptySet())
    store.addShown(ANNA, setOf("b"))
    store.addShown(BEN, setOf("c"))

    assertEquals(setOf("a", "b"), store.shownSinceLastCheck(ANNA))

    // a new last check forgets only that user's shown reminders
    store.setLastCheckedAt(ANNA, 1_000L)
    assertEquals(emptySet<String>(), store.shownSinceLastCheck(ANNA))
    assertEquals(setOf("c"), store.shownSinceLastCheck(BEN))

    store.clear()
    assertEquals(emptySet<String>(), store.shownSinceLastCheck(BEN))
  }

  @Test
  fun aReadThatFailsIsPassedOn() = runTest {
    val broken: DataStore<Preferences> = mockk {
      every { data } returns flow { throw IOException("disk") }
    }

    assertThrows(IOException::class.java) {
      kotlinx.coroutines.runBlocking { DataStoreReminderCheckStore(broken).lastCheckedAt(ANNA) }
    }
  }

  @Test
  fun aCorruptFileCountsAsNothingStored() = runTest {
    file.writeBytes(byteArrayOf(-1, -1, -1, -1))

    assertNull(store().lastCheckedAt(ANNA))
  }

  private companion object {
    const val ANNA = "anna"
    const val BEN = "ben"
  }
}
