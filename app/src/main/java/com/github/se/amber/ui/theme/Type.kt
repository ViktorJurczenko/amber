// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

/** Size of the text of a button. Material's 14 sp is small for the main action of a screen. */
private val BUTTON_TEXT_SIZE = 16.sp
private val BUTTON_TEXT_LINE_HEIGHT = 24.sp

/**
 * Amber typography: Material's default scale, with only two overrides.
 * - Headlines and titles are semi-bold, so a screen has a clear first thing to read.
 * - `labelLarge`, the text of buttons and chips, is larger.
 */
internal val AmberTypography =
    defaults.copy(
        headlineLarge = defaults.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge =
            defaults.labelLarge.copy(
                fontSize = BUTTON_TEXT_SIZE,
                lineHeight = BUTTON_TEXT_LINE_HEIGHT,
            ),
    )
