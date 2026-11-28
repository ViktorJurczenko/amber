// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.consent

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [TermsConsentRepository] in memory: accepted when [accepted] says so. [failAccept] makes
 * [accept] fail, as a full disk would.
 */
class FakeTermsConsentRepository(accepted: Boolean = false) : TermsConsentRepository {
  val accepted = MutableStateFlow(accepted)
  var failAccept = false

  override val hasAccepted = this.accepted

  override suspend fun accept() {
    if (failAccept) error("disk full")
    accepted.value = true
  }
}
