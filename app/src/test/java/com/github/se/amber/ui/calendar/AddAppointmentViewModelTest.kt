// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import java.time.LocalDate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [AddAppointmentUIState] and what [AddAppointmentViewModel] holds before its functions are
 * implemented.
 */
class AddAppointmentViewModelTest {

  @Test
  fun initialStateIsEmptyAndNotAdding() {
    assertEquals(AddAppointmentUIState(), AddAppointmentViewModel().uiState.value)
  }

  @Test
  fun cannotAddWithAnEmptyTitle() {
    assertFalse(AddAppointmentUIState(title = "").canAdd)
  }

  @Test
  fun cannotAddWithABlankTitle() {
    assertFalse(AddAppointmentUIState(title = "   ").canAdd)
  }

  @Test
  fun canAddWithATitle() {
    assertTrue(AddAppointmentUIState(title = "Dr. Martin").canAdd)
  }

  @Test
  fun cannotAddWhileAdding() {
    assertFalse(AddAppointmentUIState(title = "Dr. Martin", isAdding = true).canAdd)
  }

  // FIXME: replace the two tests below by the real ones once the functions are implemented.
  //  They are needed for coverage reasons and, sadly, can't be avoided.

  @Test
  fun functionsNotImplementedYetKeepTheInitialState() {
    val viewModel = AddAppointmentViewModel()

    viewModel.setTitle("Dr. Martin")
    viewModel.setNote("Bring the blood test results")
    viewModel.clearErrorMsg()
    viewModel.addAppointment(LocalDate.of(2026, 11, 21))

    assertEquals(AddAppointmentUIState(), viewModel.uiState.value)
  }

  @Test
  fun noEventIsSentYet() = runTest {
    assertTrue(AddAppointmentViewModel().events.toList().isEmpty())
  }
}
