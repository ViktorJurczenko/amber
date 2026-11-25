package com.github.se.amber.ui.media

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Maximum time to wait for [MediaCardViewModel] to load the people of a card. */
const val MEDIA_CARD_LOAD_TIMEOUT_MS = 5_000L

/**
 * Expands the card of [mediaId], waits until its related person number [index] is displayed, then
 * clicks it.
 */
fun ComposeContentTestRule.clickRelatedPerson(mediaId: String, index: Int = 0) {
  onNodeWithTag(MediaCardTestTags.toggleButtonTextShow(mediaId)).performClick()
  waitUntil(timeoutMillis = MEDIA_CARD_LOAD_TIMEOUT_MS) {
    onAllNodesWithTag(MediaCardTestTags.relatedItem(mediaId, index))
        .fetchSemanticsNodes()
        .isNotEmpty()
  }
  onNodeWithTag(MediaCardTestTags.relatedItem(mediaId, index)).performClick()
}

/** Runs every coroutine of `Dispatchers.Main` immediately, so loading never depends on timing. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnconfinedMainDispatcherRule : TestWatcher() {
  override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())

  override fun finished(description: Description) = Dispatchers.resetMain()
}
