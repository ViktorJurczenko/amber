// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.utils

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable

/**
 * Colors of the selectable chips. An unselected chip is Material's: no fill, an outline. A selected
 * chip is filled with the theme's `secondaryContainer`, its label and icon in the matching
 * `onSecondaryContainer`.
 */
@Composable
internal fun selectableChipColors(): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
