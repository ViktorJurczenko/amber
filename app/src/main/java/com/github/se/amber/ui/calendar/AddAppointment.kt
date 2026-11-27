// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.calendar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_CHAR_NAME
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.TEXT_FIELD_DEFAULT
import java.time.LocalDate

/** Test tags used by the Add appointment screen. */
object AddAppointmentTestTags {
  const val TITLE = "add_appointment_title"
  const val TITLE_FIELD = "add_appointment_title_field"
  const val NOTE_FIELD = "add_appointment_note_field"
  const val ADD_BUTTON = "add_appointment_add_button"
}

/**
 * Displays the add appointment screen, with a title field and a note field.
 *
 * What is typed is sent to [viewModel], and the add button asks it to add the event.
 *
 * @param viewModel the view model that owns the appointment being typed.
 * @param date the day of the appointment, the day clicked on the calendar. Today by default.
 * @param goBack called when the caregiver clicks cancel.
 * @param onAddEvent called once the appointment has been added, never when the add fails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppointmentScreen(
    viewModel: AddAppointmentViewModel = viewModel(),
    date: LocalDate = LocalDate.now(),
    goBack: () -> Unit = {},
    onAddEvent: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val errorMsgRes = uiState.errorMsgRes
  val context = LocalContext.current
  val resources = LocalResources.current

  LaunchedEffect(errorMsgRes) {
    if (errorMsgRes != null) {
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  val currentOnAddEvent by rememberUpdatedState(onAddEvent)
  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        AddAppointmentEvent.Added -> currentOnAddEvent()
      }
    }
  }

  Scaffold(
      topBar = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
          DisplayCaregiverTitle(
              Modifier.testTag(AddAppointmentTestTags.TITLE),
              stringResource(R.string.addAppointmentTitle),
          )
        }
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
        ) {
          Column(Modifier.testTag(AddAppointmentTestTags.TITLE_FIELD)) {
            DisplayTextField(
                modifier = Modifier.height(TEXT_FIELD_DEFAULT),
                value = uiState.title,
                onValueChange = viewModel::setTitle,
                label = stringResource(R.string.addAppointmentTitleLabel),
                placeholder = stringResource(R.string.addAppointmentTitlePlaceholder),
                emptyErrorMessage = stringResource(R.string.addAppointmentErrorTitleEmpty),
                maxChar = MAX_CHAR_NAME,
                singleLine = true,
            )
          }

          Column(Modifier.testTag(AddAppointmentTestTags.NOTE_FIELD)) {
            DisplayTextField(
                modifier = Modifier.height(TEXT_FIELD_BIG),
                value = uiState.note,
                onValueChange = viewModel::setNote,
                label = stringResource(R.string.addAppointmentNoteLabel),
                placeholder = stringResource(R.string.addAppointmentNotePlaceholder),
                maxLines = MAX_LINE_DESCRIPTION,
                maxChar = MAX_CHAR_DESCRIPTION,
            )
          }
        }
      },
      bottomBar = {
        DisplayCaregiverButton(
            onClick = { viewModel.addAppointment(date) },
            enabled = uiState.canAdd,
            modifier =
                Modifier.navigationBarsPadding()
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .testTag(AddAppointmentTestTags.ADD_BUTTON),
            text = stringResource(R.string.addAppointmentAddButton),
            widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
        )
      },
  )
}

/** Preview of [AddAppointmentScreen] in light mode. */
// Not restartable: the restart code that Compose generates adds branches the tests can't reach,
// which lowers the coverage. For the preview, this aspect doesn't matter.
@NonRestartableComposable
@Preview(showBackground = true, showSystemUi = true, name = "Add appointment light")
@Composable
internal fun AddAppointmentScreenPreview(
    viewModel: AddAppointmentViewModel = remember { AddAppointmentViewModel() },
) {
  AmberAppTheme(dynamicColor = false, darkTheme = false) {
    AddAppointmentScreen(viewModel = viewModel)
  }
}
