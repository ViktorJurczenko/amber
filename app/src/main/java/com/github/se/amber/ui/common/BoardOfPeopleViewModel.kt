// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state of the Board of people screen.
 *
 * @property careCircleName The name of the care circle, shown as is.
 */
data class BoardOfPeopleUIState(val careCircleName: String = "")

/** ViewModel of the Board of people screen. */
class BoardOfPeopleViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(BoardOfPeopleUIState())
  val uiState: StateFlow<BoardOfPeopleUIState> = _uiState.asStateFlow()
}
