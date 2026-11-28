// Written by MeiliV, with assistance from OpenAI Codex
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signIn

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.se.amber.R
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_BIG
import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_REGULAR
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.SURFACE_MEDIUM
import com.github.se.amber.ui.theme.SURFACE_SMALL
import com.github.se.amber.ui.theme.TEXT_FIELD_SMALL
import com.github.se.amber.ui.utils.DisplayInformation
import com.github.se.amber.ui.utils.PopupDialog

/** Test tags of the components shared by the authentication screens. */
internal object AuthenticationComponentTestTags {
  const val TERMS_FOOTER = "authentication_terms_footer"
  const val HIGHLIGHTS = "authentication_highlights"
  const val TERMS_DOCUMENT = "authentication_terms_document"
  const val TERMS_DOCUMENT_CLOSE = "authentication_terms_document_close"
  const val TERMS_DOCUMENT_ACTION = "authentication_terms_document_action"
  const val CONSENT_DIALOG = "authentication_consent_dialog"
  const val CONSENT_DECLINE = "authentication_consent_decline"
  const val CONSENT_ACCEPT = "authentication_consent_accept"
}

/** A document the user agrees to, opened from its link in [TermsFooter]. */
internal enum class TermsDocument(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
) {
  TERMS_OF_SERVICE(R.string.termsOfServiceTitle, R.string.termsOfServiceBody),
  PRIVACY_POLICY(R.string.privacyPolicyTitle, R.string.privacyPolicyBody),
}

/**
 * Provides the common layout shared by all authentication screens.
 *
 * The layout displays the Amber authentication header at the top, screen-specific [content] in the
 * center, and the terms and privacy notice at the bottom.
 *
 * @param modifier Modifier applied to the root layout.
 * @param spacing Vertical space between screen-specific content groups.
 * @param content Screen-specific composable content displayed in the center of the layout.
 */
@Composable
internal fun AuthenticationLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = CAREGIVER_PADDING_MEDIUM,
    content: @Composable () -> Unit = {},
) {
  // Surface will be the background/container for the screen
  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    // The header, the footer and the content are measured. When it fits, the same space as the
    // larger of the header and the footer is kept above and below the content, which centers it on
    // the whole screen; otherwise it only clears them. Either way it never covers them.
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var footerHeight by remember { mutableStateOf(0.dp) }
    var contentHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding() // Avoids drawing underneath Android's top system bar
                .navigationBarsPadding() // Avoid drawing underneath navigation area at the bottom
                .padding(horizontal = CAREGIVER_PADDING_BIG),
    ) {
      AmberHeader(
          modifier =
              Modifier.align(Alignment.TopCenter)
                  .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                  .padding(top = CAREGIVER_PADDING_ENORMOUS),
          horizontalAlignment = Alignment.CenterHorizontally,
      )
      val edge = maxOf(headerHeight, footerHeight)
      val isCentered = contentHeight + edge * 2 <= maxHeight
      // Centered when it fits, scrolled when it doesn't
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .padding(
                      top = if (isCentered) edge else headerHeight,
                      bottom = if (isCentered) edge else footerHeight,
                  ),
          contentAlignment = Alignment.Center,
      ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // After the scroll, so it's the content's whole height, not what shows
                    .onSizeChanged { contentHeight = with(density) { it.height.toDp() } }
                    .padding(vertical = CAREGIVER_PADDING_MEDIUM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing), // Remove abusive use of Spacers
            content = { content() },
        )
      }
      TermsFooter(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                  .padding(bottom = PADDING_MINI)
      )
    }
  }
}

/**
 * Displays a text input field styled consistently across authentication screens.
 *
 * The field supports different keyboard configurations and visual transformations so that it can be
 * reused for both email and password input. Its colors come from the theme.
 *
 * @param modifier Modifier applied to the text field.
 * @param value Current value displayed in the input field.
 * @param onValueChange Called whenever the field value changes.
 * @param placeholder Text displayed while the field is empty.
 * @param keyboardOptions Keyboard configuration appropriate for the expected input.
 * @param visualTransformation Transformation applied when displaying the text, such as hiding
 *   password characters.
 */
@Composable
internal fun AuthenticationTextField(
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.fillMaxWidth().height(TEXT_FIELD_SMALL),
      singleLine = true,
      placeholder = {
        AuthenticationText(
            text = placeholder,
            style = MaterialTheme.typography.bodySmall,
        )
      },
      keyboardOptions = keyboardOptions,
      visualTransformation = visualTransformation,
      shape = RoundedCornerShape(ROUND_CORNER),
  )
}

/**
 * Displays the common primary button used throughout the authentication flow.
 *
 * @param modifier Modifier applied to the button.
 * @param text Text displayed inside the button.
 * @param onClick Called when the user presses the button.
 * @param enabled Whether the button reacts to clicks.
 * @param leadingIcon Optional icon displayed before [text], such as a provider logo.
 */
@Composable
internal fun AuthenticationButton(
    modifier: Modifier = Modifier,
    text: String = "",
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.fillMaxWidth().height(CAREGIVER_BUTTON_HEIGHT_BIG),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
  ) {
    leadingIcon?.let { icon ->
      Image(
          painter = icon,
          contentDescription = null, // Decorative: the button text already describes the action
          modifier = Modifier.padding(end = CAREGIVER_PADDING_REGULAR).size(SURFACE_SMALL),
      )
    }
    AuthenticationText(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
  }
}

/**
 * Displays text used by the authentication screens with optional presentation overrides.
 *
 * When [color] is unspecified, the text uses the style's color or the surrounding content color.
 *
 * @param modifier Modifier applied to the text.
 * @param text Content to display.
 * @param textAlign Alignment of lines within the text when it wraps.
 * @param style Typography style applied to the text.
 * @param color Optional theme color; defaults to [Color.Unspecified].
 */
@Composable
internal fun AuthenticationText(
    modifier: Modifier = Modifier,
    text: String = "",
    textAlign: TextAlign? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
  Text(
      text = text,
      textAlign = textAlign,
      modifier = modifier,
      style = style,
      color = color,
  )
}

/**
 * Displays the terms-of-service and privacy-policy notice shared by authentication screens. The
 * names of the two documents are links: each opens its text in a popup.
 *
 * @param modifier Modifier applied to the footer text.
 */
@Composable
internal fun TermsFooter(
    modifier: Modifier = Modifier,
) {
  var shownDocument by rememberSaveable { mutableStateOf<TermsDocument?>(null) }
  val linkStyle =
      TextLinkStyles(
          SpanStyle(
              color = MaterialTheme.colorScheme.primary,
              textDecoration = TextDecoration.Underline,
          )
      )
  val names = TermsDocument.entries.associateWith { stringResource(it.titleRes) }
  // The whole sentence is translated, then each document's name in it becomes its link
  val sentence =
      stringResource(
          R.string.signInTermsFooter,
          names.getValue(TermsDocument.TERMS_OF_SERVICE),
          names.getValue(TermsDocument.PRIVACY_POLICY),
      )
  val text = buildAnnotatedString {
    append(sentence)
    names.forEach { (document, name) ->
      val start = sentence.indexOf(name)
      if (start >= 0) {
        addLink(
            LinkAnnotation.Clickable(document.name, linkStyle) { shownDocument = document },
            start,
            start + name.length,
        )
      }
    }
  }
  Text(
      text = text,
      textAlign = TextAlign.Center,
      modifier = modifier.fillMaxWidth().testTag(AuthenticationComponentTestTags.TERMS_FOOTER),
      style = MaterialTheme.typography.bodySmall,
  )
  shownDocument?.let { TermsDocumentDialog(document = it, onClose = { shownDocument = null }) }
}

/**
 * Displays the text of a [TermsDocument] in the app's popup, to read and close.
 *
 * @param document The document to show.
 * @param onClose Called by the close button, the bottom button and a tap outside.
 */
@Composable
internal fun TermsDocumentDialog(
    document: TermsDocument = TermsDocument.TERMS_OF_SERVICE,
    onClose: () -> Unit = {},
) {
  PopupDialog(
      title = stringResource(document.titleRes),
      onDismiss = onClose,
      actionText = stringResource(R.string.termsClose),
      onAction = onClose,
      dialogTestTag = AuthenticationComponentTestTags.TERMS_DOCUMENT,
      dismissTestTag = AuthenticationComponentTestTags.TERMS_DOCUMENT_CLOSE,
      actionTestTag = AuthenticationComponentTestTags.TERMS_DOCUMENT_ACTION,
  ) {
    AuthenticationText(text = stringResource(document.bodyRes))
  }
}

/**
 * Asks to accept the Terms of Service and Privacy Policy before the first sign-in, saying in short
 * what the user agrees to, with links to both documents.
 *
 * @param onAccept Called by the bottom button.
 * @param onDecline Called by the close button and a tap outside.
 */
@Composable
internal fun ConsentDialog(
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
) {
  PopupDialog(
      title = stringResource(R.string.consentTitle),
      onDismiss = onDecline,
      actionText = stringResource(R.string.consentAccept),
      onAction = onAccept,
      dialogTestTag = AuthenticationComponentTestTags.CONSENT_DIALOG,
      dismissTestTag = AuthenticationComponentTestTags.CONSENT_DECLINE,
      actionTestTag = AuthenticationComponentTestTags.CONSENT_ACCEPT,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
      AuthenticationText(
          text = stringResource(R.string.consentIntro),
          style = MaterialTheme.typography.titleSmall,
      )
      listOf(
              R.string.consentPointData,
              R.string.consentPointShare,
              R.string.consentPointLocation,
              R.string.consentPointDelete,
          )
          .forEach {
            DisplayInformation(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = "", // the sentence says it all
                sentences = listOf(stringResource(it)),
            )
          }
      TermsFooter(modifier = Modifier.padding(top = CAREGIVER_PADDING_SMALL))
      AuthenticationText(
          text = stringResource(R.string.consentDeclineHint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
      )
    }
  }
}

/**
 * Displays a row of icons, each in a yellow box like the map's recenter button, to show at a glance
 * what a screen is about. Decorative: the text next to it says the same.
 *
 * @param modifier Modifier applied to the row.
 * @param icons The icons, from start to end.
 */
@Composable
internal fun AuthenticationHighlights(
    modifier: Modifier = Modifier,
    icons: List<ImageVector> = emptyList(),
) {
  Row(
      modifier = modifier.testTag(AuthenticationComponentTestTags.HIGHLIGHTS),
      horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
  ) {
    icons.forEach { icon ->
      Box(
          modifier =
              Modifier.size(SURFACE_MEDIUM)
                  .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(ROUND_CORNER)),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(CAREGIVER_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onTertiary,
        )
      }
    }
  }
}

/**
 * Preview of [AuthenticationLayout] with every shared component and the consent popup, using the
 * Amber theme.
 */
@Preview(showBackground = true)
@Composable
internal fun AuthenticationComponentsPreview() {
  AmberAppTheme {
    AuthenticationLayout {
      AuthenticationText(text = "Screen heading", style = MaterialTheme.typography.titleMedium)
      AuthenticationTextField(placeholder = "email@domain.com")
      AuthenticationButton(text = "Continue")
      AuthenticationButton(text = "Disabled", enabled = false)
      AuthenticationHighlights(
          icons = listOf(Icons.Outlined.CheckCircle, Icons.Outlined.CheckCircle)
      )
    }
    ConsentDialog()
  }
}
