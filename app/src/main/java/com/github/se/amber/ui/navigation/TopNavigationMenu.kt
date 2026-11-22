// Written by Lou-Anne Maier.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MINI

/** Stable tags for the top bar, its action label, the back control, and the divider. */
object TopNavigationMenuTestTags {
  const val TOP_NAVIGATION_MENU = "TopNavigationMenu_test_tag_menu"
  const val TITLE = "TopNavigationMenu_test_tag_title"
  const val BACK_BUTTON = "TopNavigationMenu_test_tag_back_button"
  const val DIVIDER = "TopNavigationMenu_test_tag_divider"
}

/**
 * Label shown in [TopNavigationMenu]. The click itself is [TopNavigationMenu]'s `onBackClick`; this
 * enum only chooses the words.
 *
 * @property label Text shown in the bar, as a string resource.
 */
enum class TopNavigationAction(@param:StringRes val label: Int) {
  CANCEL(R.string.navigationCancel),
  SIGN_OUT(R.string.navigationSignOut),
}

/**
 * Row with a back arrow, an action label, and a divider. Use it through [AmberTopBar], which adds
 * the screen title and keeps the row clear of the status bar.
 *
 * The arrow and the label both go back: the label says in words what the arrow does, and it is a
 * larger thing to press. Both use `primary`. Callers on a top-level destination should not show
 * this row; the back action there is easy to confuse with leaving the app.
 *
 * @param modifier Modifier applied to the column that holds the row and the divider.
 * @param action Label displayed in the row.
 * @param onBackClick Called when the back arrow or the label is tapped.
 */
@Composable
fun TopNavigationMenu(
    modifier: Modifier = Modifier,
    action: TopNavigationAction = TopNavigationAction.CANCEL,
    onBackClick: () -> Unit = {},
) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU)
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(PADDING_MINI),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
          onClick = onBackClick,
          modifier = Modifier.testTag(TopNavigationMenuTestTags.BACK_BUTTON),
      ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.navigationBack),
            tint = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
          text = stringResource(action.label),
          modifier =
              Modifier.clip(MaterialTheme.shapes.small)
                  .clickable(role = Role.Button, onClick = onBackClick)
                  .padding(CAREGIVER_PADDING_SMALL)
                  .testTag(TopNavigationMenuTestTags.TITLE),
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.titleLarge,
      )
    }
    HorizontalDivider(
        modifier = Modifier.testTag(TopNavigationMenuTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
  }
}

/** Stable tag for [AmberTopBar]. */
object AmberTopBarTestTags {
  const val TOP_BAR = "AmberTopBar_test_tag_top_bar"
}

/**
 * Top bar of every screen: an optional back row ([TopNavigationMenu]) above the screen title.
 *
 * It is as tall as its content, so a long or large title is never cut, and it clears the status bar
 * once for both parts.
 *
 * @param modifier Modifier applied to the bar.
 * @param action Label of the back row. Null on a top-level destination, which has no back row.
 * @param onBackClick Called when the back row is tapped.
 * @param title The screen title, usually `DisplayCaregiverTitle` or `DisplayPatientTitle`.
 */
@Composable
fun AmberTopBar(
    modifier: Modifier = Modifier,
    action: TopNavigationAction? = null,
    onBackClick: () -> Unit = {},
    title: @Composable () -> Unit = {},
) {
  Surface(
      modifier = modifier.fillMaxWidth().testTag(AmberTopBarTestTags.TOP_BAR),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    Column(modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
      if (action != null) TopNavigationMenu(action = action, onBackClick = onBackClick)
      Box(modifier = Modifier.padding(horizontal = CAREGIVER_PADDING_MEDIUM)) { title() }
    }
  }
}

/** Preview of [TopNavigationMenu] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun TopNavigationMenuPreview() {
  // Omit [action] so the default CANCEL branch is exercised by preview/tests.
  AmberAppTheme(dynamicColor = false) { TopNavigationMenu() }
}

/** Preview of [AmberTopBar] with its back row and a title, using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun AmberTopBarPreview() {
  AmberAppTheme(dynamicColor = false) {
    AmberTopBar(action = TopNavigationAction.CANCEL) {
      Text(
          text = stringResource(R.string.title_activity_main),
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.headlineSmall,
      )
    }
  }
}
