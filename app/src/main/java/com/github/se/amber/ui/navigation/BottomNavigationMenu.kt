// Written by Lou-Anne Maier.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE

/** Stable tab keys, used in test tags. They are never shown; the visible text is [Tab.label]. */
internal const val HOME_TAB_KEY = "Home"
internal const val UPLOAD_TAB_KEY = "Upload"
internal const val SETTING_TAB_KEY = "Setting"
internal const val MEDIAS_TAB_KEY = "Medias"

/** Stable tags for the bottom bar, its divider, and one tag per [Tab]. */
object BottomNavigationMenuTestTags {
  const val BOTTOM_NAVIGATION_MENU = "BottomNavigationMenu_test_tag_menu"
  const val DIVIDER = "BottomNavigationMenu_test_tag_divider"

  /**
   * Tag for [tab], unique across caregiver and patient menus. The default is the caregiver home
   * tab, the same default selection as [BottomNavigationMenu].
   */
  fun tabTag(tab: Tab = CaregiverTab.Home) = "BottomNavigationMenu_test_tag_tab_${tab.id}${tab.key}"
}

/**
 * One destination in a bottom navigation menu.
 *
 * @property key Stable name of the tab, used in its test tag. It is not shown, so it is not
 *   translated.
 * @property label Name of the tab shown under its icon, as a string resource.
 * @property icon Icon drawn for this tab.
 * @property iconSize Drawn size of [icon]. Caregiver and patient tabs use different sizes.
 * @property destination Screen opened when the tab is selected.
 * @property id Prefix that keeps a caregiver tag distinct from a patient tag with the same name.
 */
sealed interface Tab {
  val key: String
  @get:StringRes val label: Int
  val icon: ImageVector
  val iconSize: Dp
  val destination: Screen
  val id: String
}

/**
 * Caregiver bottom navigation tabs.
 *
 * @property key Stable name of the tab, used in its test tag.
 * @property label Name of the tab shown under its icon, as a string resource.
 * @property icon Icon drawn for this tab.
 * @property destination Screen opened when the tab is selected.
 * @property iconSize Drawn size of [icon]. Defaults to [CAREGIVER_ICON_SIZE].
 * @property id Prefix shared by every caregiver tab.
 */
sealed class CaregiverTab(
    override val key: String,
    @param:StringRes override val label: Int,
    override val icon: ImageVector,
    override val destination: CaregiverScreen,
    override val iconSize: Dp = CAREGIVER_ICON_SIZE,
    override val id: String = "caregiverId_",
) : Tab {
  /** Caregiver home. */
  data object Home :
      CaregiverTab(
          HOME_TAB_KEY,
          R.string.navigationTabHome,
          Icons.Outlined.Home,
          CaregiverScreen.Home,
      )

  /** Caregiver upload. */
  data object Upload :
      CaregiverTab(
          UPLOAD_TAB_KEY,
          R.string.navigationTabUpload,
          Icons.Outlined.PhotoCamera,
          CaregiverScreen.Upload,
      )

  /**
   * Caregiver parameters. The key stays "Setting"; the destination is [CaregiverScreen.Parameters].
   */
  data object Setting :
      CaregiverTab(
          SETTING_TAB_KEY,
          R.string.navigationTabSetting,
          Icons.Outlined.Settings,
          CaregiverScreen.Parameters,
      )

  companion object {
    /** Tabs shown on caregiver screens, in bar order. */
    val caregiverTabs: List<CaregiverTab>
      get() = listOf(Home, Upload, Setting)
  }
}

/**
 * Patient bottom navigation tabs.
 *
 * @property key Stable name of the tab, used in its test tag.
 * @property label Name of the tab shown under its icon, as a string resource.
 * @property icon Icon drawn for this tab.
 * @property destination Screen opened when the tab is selected.
 * @property iconSize Drawn size of [icon]. Defaults to [PATIENT_ICON_SIZE].
 * @property id Prefix shared by every patient tab.
 */
sealed class PatientTab(
    override val key: String,
    @param:StringRes override val label: Int,
    override val icon: ImageVector,
    override val destination: PatientScreen,
    override val iconSize: Dp = PATIENT_ICON_SIZE,
    override val id: String = "patientId_",
) : Tab {
  /** Patient home. */
  data object Home :
      PatientTab(HOME_TAB_KEY, R.string.navigationTabHome, Icons.Outlined.Home, PatientScreen.Home)

  /** Patient picture list. */
  data object SeeMedias :
      PatientTab(
          MEDIAS_TAB_KEY,
          R.string.navigationTabMedias,
          Icons.Outlined.Photo,
          PatientScreen.SeeMedias,
      )

  data object Setting :
      PatientTab(
          SETTING_TAB_KEY,
          R.string.navigationTabSetting,
          Icons.Outlined.Settings,
          PatientScreen.Parameters,
      )

  companion object {
    /** Tabs shown on patient screens, in bar order. */
    val patientTabs: List<PatientTab>
      get() = listOf(Home, SeeMedias, Setting)
  }
}

/**
 * Bottom bar for the tabs of one role.
 *
 * Every tab shows its icon and its name: an icon alone has to be remembered, a word can be read.
 * The selected tab is marked by a `secondaryContainer` pill behind its icon and by darker text, so
 * the selection does not rely on color alone.
 *
 * @param modifier Modifier applied to the column that holds the divider and the bar.
 * @param selectedTab Tab that is currently selected. Ignored when it is not in [tabs].
 * @param onTabSelected Called with the tab the user taps.
 * @param tabs Tabs to show, in order. Defaults to the caregiver tabs.
 */
@Composable
fun BottomNavigationMenu(
    modifier: Modifier = Modifier,
    selectedTab: Tab = CaregiverTab.Home,
    onTabSelected: (Tab) -> Unit = {},
    tabs: List<Tab> = CaregiverTab.caregiverTabs,
) {
  Column(
      modifier =
          modifier.fillMaxWidth().testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
  ) {
    HorizontalDivider(
        modifier = Modifier.testTag(BottomNavigationMenuTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = MaterialTheme.colorScheme.outlineVariant,
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = {
          tabs.forEach { tab ->
            NavigationBarItem(
                // The icon is decorative: the label next to it already names the tab.
                icon = {
                  Icon(
                      imageVector = tab.icon,
                      contentDescription = null,
                      modifier = Modifier.size(tab.iconSize),
                  )
                },
                label = {
                  Text(
                      text = stringResource(tab.label),
                      style = MaterialTheme.typography.labelLarge,
                  )
                },
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.testTag(BottomNavigationMenuTestTags.tabTag(tab)),
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
          }
        },
    )
  }
}

/** Preview of [BottomNavigationMenu] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun BottomNavigationMenuPreview() {
  AmberAppTheme { BottomNavigationMenu() }
}
