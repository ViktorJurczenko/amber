// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AddressLookupRepositoryProviderTest {

  private val fake = AddressLookupRepository { _, _ -> Result.success(null) }

  @Test
  fun repositoryIsBuiltOnlyOnceByFactory() {
    var builds = 0
    val provider = AddressLookupRepositoryProvider {
      builds++
      fake
    }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(1, builds)
  }

  @Test
  fun repositoryIsRebuiltAfterFactoryFailure() {
    var attempts = 0
    val provider = AddressLookupRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    assertThrows(IllegalStateException::class.java) { provider.repository }
    assertSame(fake, provider.repository)
  }
}
