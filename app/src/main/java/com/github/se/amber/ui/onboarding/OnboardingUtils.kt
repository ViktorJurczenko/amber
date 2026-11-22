// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.R
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_LEFT
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_RIGHT
import com.github.se.amber.ui.onboarding.DividerTestTags.ROW
import com.github.se.amber.ui.onboarding.DividerTestTags.TEXT
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Test tags for [Divider]. */
object DividerTestTags {
  const val HORIZONTAL_DIVIDER_LEFT = "divider_horitonzal_divider_left"
  const val HORIZONTAL_DIVIDER_RIGHT = "divider_horitonzal_divider_right"
  const val TEXT = "divider_text"
  const val ROW = "divider_container"
}

/**
 * Single-line outlined field used by onboarding forms.
 *
 * Its outline, label, cursor and error colors are Material's, from the theme: the field that has
 * the focus is outlined in `primary`, a field with an error in `error`.
 *
 * @param modifier modifier applied to the text field.
 * @param value current text.
 * @param onValueChange called when the text changes.
 * @param label border label shown above the field.
 * @param placeholder hint shown inside an empty focused field, or null when the label is enough.
 * @param placeholderTestTag optional test tag on the placeholder text.
 * @param error supporting error text, or null when the field is valid.
 * @param errorTestTag optional test tag on the supporting error text.
 * @param enabled whether the field accepts input.
 * @param keyboardOptions the keyboard to show, e.g. one in capitals for a code.
 */
@Composable
fun OnboardingOutlinedTextField(
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    label: String = "",
    placeholder: String? = null,
    placeholderTestTag: String? = null,
    error: String? = null,
    errorTestTag: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.fillMaxWidth(),
      enabled = enabled,
      keyboardOptions = keyboardOptions,
      // No style on the label: Material shrinks it itself when it moves up to the outline
      label = { Text(text = label) },
      placeholder =
          placeholder?.let { hint ->
            {
              Text(
                  text = hint,
                  modifier = placeholderTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                  style = MaterialTheme.typography.bodyLarge,
              )
            }
          },
      supportingText =
          error?.let { message ->
            {
              Text(
                  text = message,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = errorTestTag?.let { Modifier.testTag(it) } ?: Modifier,
              )
            }
          },
      isError = error != null,
      singleLine = true,
      shape = MaterialTheme.shapes.medium,
  )
}

/**
 * Displays two horizontal dividers separated by a centered text label.
 *
 * @param modifier modifier applied to the divider row.
 * @param text the text display in between. Defaults to the translated "or".
 * @param style text style used for the "or" label.
 */
@Composable
fun Divider(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.onboardingDividerOr),
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
  Row(
      modifier = modifier.fillMaxWidth().testTag(ROW),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    HorizontalDivider(
        modifier = Modifier.weight(WEIGHT_ONE).testTag(HORIZONTAL_DIVIDER_LEFT),
        color = MaterialTheme.colorScheme.outlineVariant,
    )

    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = style,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CAREGIVER_PADDING_SMALL).testTag(TEXT),
    )

    HorizontalDivider(
        modifier = Modifier.weight(WEIGHT_ONE).testTag(HORIZONTAL_DIVIDER_RIGHT),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
  }
}
