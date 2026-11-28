// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class BoardOfPeopleViewModelTest {

  private lateinit var viewModel: BoardOfPeopleViewModel

  @Before
  fun setUp() {
    viewModel = BoardOfPeopleViewModel()
  }

  @Test
  fun initialState_isDefaultAndFlowInstanceIsStable() {
    assertEquals(BoardOfPeopleUIState(), viewModel.uiState.value)
    assertSame(viewModel.uiState, viewModel.uiState)
  }

  @Test
  fun uiStateDataClass_copyAndEquality() {
    val circleName = "Family circle"
    val state = BoardOfPeopleUIState(careCircleName = circleName)

    assertEquals(BoardOfPeopleUIState(careCircleName = circleName), state)
    assertEquals(BoardOfPeopleUIState(), state.copy(careCircleName = ""))
    assertEquals(circleName, state.component1())
  }
}
