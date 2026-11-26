// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS_PATIENT
import com.github.se.amber.ui.theme.DISABLED_ALPHA
import com.github.se.amber.ui.theme.MAX_CHAR_DEFAULT
import com.github.se.amber.ui.theme.MAX_LINE_DEFAULT
import com.github.se.amber.ui.theme.MIN_LINE_DEFAULT
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE

object PatientUtilsTestTags {
  const val TITLE = "PatientUtils_test_tag_title"
  const val BUTTON = "PatientUtils_test_tag_button"
  const val BUTTON_WITH_ICON = "PatientUtils_test_tag_button_with_icon"
  const val BUTTON_ICON = "PatientUtils_test_tag_button_icon"
  const val ICON_BUTTON = "PatientUtils_test_tag_icon_button"
  const val TEXT_FIELD = "PatientUtils_test_tag_text_field"
  const val TEXT_FIELD_ERROR = "PatientUtils_test_tag_text_field_error"
}

/**
 * Displays the title for patient's Screen
 *
 * @param modifier modifier to apply to the text
 * @param title title to display
 * @param titleStyle typography style of the title
 * @param testTag test tag of the title
 */
@NonRestartableComposable
@Composable
fun DisplayPatientTitle(
    modifier: Modifier = Modifier,
    title: String = "",
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    testTag: String = PatientUtilsTestTags.TITLE,
) {
  Text(
      text = title,
      color = MaterialTheme.colorScheme.primary,
      style = titleStyle,
      modifier =
          modifier
              .fillMaxWidth()
              .padding(top = PATIENT_PADDING_SMALL, bottom = PATIENT_PADDING_MEDIUM)
              .testTag(testTag),
      textAlign = TextAlign.Center,
  )
}

/**
 * Displays a button with a label, an optional icon, and a click action.
 *
 * This button needs to be displayed in Scrollable Screens only ! (because it contains a fixed
 * height)
 *
 * @param modifier modifier applied to the button
 * @param buttonHeight height of the button
 * @param buttonWidth fraction of the available width taken by the button. Ignored when [fillWidth]
 *   is false.
 * @param outlined if true, the button is transparent with a visible outline instead of the filled
 *   style. Use it for secondary actions.
 * @param fillWidth if true, the button fills the available width (scaled by [buttonWidth]) and is
 *   centered. If false, the button only wraps its content.
 * @param text label displayed on the button.
 * @param textStyle typography style of the button label
 * @param icon icon displayed after the label. No icon is displayed when null.
 * @param description content description for the icon. Only used when [icon] is not null.
 * @param enabled say if the button is clickable or not
 * @param onClick callback invoked when the button is selected.
 * @param testTag test tag of the button. Defaults to [PatientUtilsTestTags.BUTTON_WITH_ICON] when
 *   an icon is displayed, and to [PatientUtilsTestTags.BUTTON] otherwise.
 * @param iconTestTag test tag of the icon. Only used when [icon] is not null.
 */
@NonRestartableComposable
@Composable
fun DisplayPatientButton(
    modifier: Modifier = Modifier,
    buttonHeight: Dp = PATIENT_BUTTON_HEIGHT_MEDIUM,
    buttonWidth: Float = PATIENT_BUTTON_WIDTH,
    outlined: Boolean = false,
    fillWidth: Boolean = true,
    text: String = "",
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    icon: ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    testTag: String? = null,
    iconTestTag: String = PatientUtilsTestTags.BUTTON_ICON,
) {
  val sizeModifier =
      if (fillWidth) {
        Modifier.fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .fillMaxWidth(buttonWidth)
      } else Modifier
  val resolvedTestTag =
      testTag
          ?: if (icon != null) PatientUtilsTestTags.BUTTON_WITH_ICON
          else PatientUtilsTestTags.BUTTON
  Button(
      onClick = onClick,
      modifier = modifier.then(sizeModifier).height(buttonHeight).testTag(resolvedTestTag),
      enabled = enabled,
      shape = RoundedCornerShape(ROUND_CORNER),
      border =
          if (outlined) BorderStroke(BORDER_THICKNESS_PATIENT, MaterialTheme.colorScheme.tertiary)
          else null,
      colors =
          if (outlined) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.tertiary,
            )
          } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
          },
  ) {
    Row(
        modifier = if (fillWidth) Modifier.fillMaxSize() else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text,
          style = textStyle,
          textAlign = TextAlign.Center,
          modifier = if (fillWidth) Modifier.weight(WEIGHT_ONE) else Modifier,
      )
      icon?.let {
        Icon(
            imageVector = it,
            contentDescription = description,
            modifier =
                (if (fillWidth) Modifier else Modifier.padding(start = PATIENT_PADDING_SMALL))
                    .testTag(iconTestTag),
        )
      }
    }
  }
}

/**
 * Displays a circular patient button holding an icon, or a loading spinner.
 *
 * [DisplayPatientButton] always includes a text label and a width fraction, so an icon-only control
 * uses its own container color, content color, and height. The content description is set on the
 * button itself so that screen readers announce the button, not its icon.
 *
 * @param modifier modifier applied to the button
 * @param icon icon displayed on the button
 * @param description content description of the button
 * @param enabled say if the button is clickable or not
 * @param showSpinner replaces the icon with a loading spinner when true
 * @param onClick callback invoked when the button is selected
 * @param testTag test tag of the button
 * @param spinnerTestTag test tag of the spinner. No tag is set when empty.
 */
@Composable
fun DisplayPatientCircularIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    description: String = "",
    enabled: Boolean = true,
    showSpinner: Boolean = false,
    onClick: () -> Unit = {},
    testTag: String = PatientUtilsTestTags.ICON_BUTTON,
    spinnerTestTag: String = "",
) {
  val contentColor = MaterialTheme.colorScheme.onTertiary
  FilledIconButton(
      onClick = onClick,
      enabled = enabled,
      modifier =
          modifier
              .size(PATIENT_BUTTON_HEIGHT_SMALL)
              .semantics { contentDescription = description }
              .testTag(testTag),
      shape = CircleShape,
      colors =
          IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = contentColor,
              disabledContainerColor =
                  MaterialTheme.colorScheme.tertiary.copy(alpha = DISABLED_ALPHA),
              disabledContentColor = contentColor.copy(alpha = DISABLED_ALPHA),
          ),
  ) {
    if (showSpinner) {
      CircularProgressIndicator(
          modifier =
              Modifier.size(PATIENT_ICON_SIZE)
                  .then(
                      if (spinnerTestTag.isNotEmpty()) Modifier.testTag(spinnerTestTag)
                      else Modifier
                  ),
          color = LocalContentColor.current,
      )
    } else {
      Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(PATIENT_ICON_SIZE),
      )
    }
  }
}

/**
 * Displays a patient text field using theme colors for its text, border, and validation messages.
 * Character and line limits produce guidance after the field has received focus; callers own the
 * actual input value.
 *
 * @param modifier Modifier applied to the text field.
 * @param value Current text supplied by the caller.
 * @param onValueChange Called whenever the user edits the text.
 * @param label Optional label displayed above the input while focused or filled.
 * @param placeholder Optional hint displayed while the input is empty.
 * @param emptyErrorMessage Message shown after focus when the input is blank.
 * @param minLines Minimum number of visible input lines.
 * @param maxLines Maximum number of visible input lines when [singleLine] is false.
 * @param maxChar Character threshold for the guidance message; the caller enforces any hard limit.
 * @param singleLine Whether the keyboard should keep the input on one line.
 * @param error Validation message shown instead of [emptyErrorMessage] when present.
 * @param leadingIcon Optional icon before the input text.
 * @param keyboardOptions Keyboard type and IME action for the field.
 * @param keyboardActions Callbacks for IME actions such as Search.
 * @param testTag Tag used to find this field in UI tests.
 */
@Composable
fun DisplayPatientTextField(
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    label: String? = null,
    placeholder: String? = null,
    emptyErrorMessage: String? = null,
    minLines: Int = MIN_LINE_DEFAULT,
    maxLines: Int = MAX_LINE_DEFAULT,
    maxChar: Int = MAX_CHAR_DEFAULT,
    singleLine: Boolean = false,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String = PatientUtilsTestTags.TEXT_FIELD,
) {
  require(maxChar > 0) { "maxChar must be positive, was $maxChar" }
  require(minLines > 0) { "minLines must be positive, was $minLines" }
  require(singleLine || minLines <= maxLines) {
    "minLines ($minLines) must not exceed maxLines ($maxLines)"
  }

  var isFocused by remember { mutableStateOf(false) }
  var hasBeenFocused by rememberSaveable { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label =
            label?.let {
              {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
              }
            },
        placeholder =
            placeholder?.let {
              {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                )
              }
            },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        modifier =
            Modifier.fillMaxWidth()
                .border(
                    width = BORDER_THICKNESS_PATIENT,
                    color =
                        if (isFocused) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(ROUND_CORNER),
                )
                .onFocusChanged { focus ->
                  isFocused = focus.isFocused
                  if (focus.isFocused) hasBeenFocused = true
                }
                .testTag(testTag),
        shape = RoundedCornerShape(ROUND_CORNER),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) MIN_LINE_DEFAULT else maxLines,
        isError = error != null,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )

    if (value.length >= maxChar && hasBeenFocused) {
      Text(
          text = pluralStringResource(R.plurals.textFieldMaxCharReached, maxChar, maxChar),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    if (!singleLine && value.lines().size >= maxLines && hasBeenFocused) {
      Text(
          text = pluralStringResource(R.plurals.textFieldMaxLinesReached, maxLines, maxLines),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    val shownError = error ?: emptyErrorMessage?.takeIf { value.isBlank() && hasBeenFocused }
    if (shownError != null) {
      Text(
          text = shownError,
          modifier = Modifier.testTag(PatientUtilsTestTags.TEXT_FIELD_ERROR),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Preview(showBackground = true, name = "Patient text field")
@Composable
internal fun DisplayPatientTextFieldPreview() {
  AmberAppTheme(darkTheme = false) {
    DisplayPatientTextField(placeholder = stringResource(R.string.patientMapSearchHint))
  }
}

@Preview(showBackground = true, name = "Patient utils light")
@Composable
internal fun DisplayPatientButtonsPreview() {
  AmberAppTheme(darkTheme = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PATIENT_PADDING_SMALL),
        verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      DisplayPatientButton(
          text = "Button",
          onClick = {},
          modifier = Modifier,
      )
      DisplayPatientButton(
          text = "Add",
          icon = Icons.Default.Add,
          description = "Add patient",
          fillWidth = false,
          onClick = {},
      )
      DisplayPatientCircularIconButton(
          description = "Add patient",
          onClick = {},
      )
    }
  }
}

@Preview(
    showBackground = true,
    name = "Patient utils dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun DisplayPatientButtonsDarkPreview() {
  AmberAppTheme(darkTheme = true) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PATIENT_PADDING_SMALL),
        verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      DisplayPatientButton(
          text = "Button",
          onClick = {},
          modifier = Modifier,
      )
      DisplayPatientButton(
          text = "Add",
          icon = Icons.Default.Add,
          description = "Add patient",
          fillWidth = false,
          onClick = {},
      )
      DisplayPatientCircularIconButton(
          description = "Add patient",
          onClick = {},
      )
    }
  }
}
