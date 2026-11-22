// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Amber Color Scheme for dark Mode
 *
 * Every role a Material component reads is set, so none falls back to Material's purple baseline.
 * See [LightColorScheme] for what each role is used for.
 */
internal val DarkColorScheme =
    darkColorScheme(
        primary = cyan,
        onPrimary = deepCyan,
        primaryContainer = tealGreen,
        onPrimaryContainer = Color.White,
        inversePrimary = tealGreen,
        secondary = lightCyan,
        onSecondary = deepCyan,
        secondaryContainer = backCyan,
        onSecondaryContainer = deepCyan,
        tertiary = lightGray,
        onTertiary = darkCyan,
        tertiaryContainer = tealGreen,
        onTertiaryContainer = Color.White,
        background = deepCyan,
        onBackground = Color.White,
        surface = deepCyan,
        onSurface = Color.White,
        surfaceVariant = cyanSurfaceHighest,
        onSurfaceVariant = mistCyan,
        surfaceTint = cyan,
        inverseSurface = lightGray,
        inverseOnSurface = darkCyan,
        error = salmon,
        onError = maroon,
        errorContainer = darkRed,
        onErrorContainer = paleRust,
        outline = cyanOutline,
        outlineVariant = cyanOutlineSoft,
        scrim = Color.Black,
        surfaceBright = cyanSurfaceHighest,
        surfaceDim = deepCyan,
        surfaceContainerLowest = cyanSurfaceLowest,
        surfaceContainerLow = cyanSurfaceLow,
        surfaceContainer = cyanSurface,
        surfaceContainerHigh = darkCyan,
        surfaceContainerHighest = cyanSurfaceHighest,
    )

/**
 * Amber Color Scheme for light Mode
 *
 * A container color always goes with its `on` color: text on `primary` is `onPrimary`.
 */
internal val LightColorScheme =
    lightColorScheme(
        /** Main title, filled button */
        primary = darkOrange,
        onPrimary = Color.White,

        /** Highlighted card */
        primaryContainer = paleRust,
        onPrimaryContainer = maroon,
        inversePrimary = salmon,

        /** Secondary title */
        secondary = orange,
        onSecondary = Color.Black,

        /** Selected chip, selected tab indicator, icon circle */
        secondaryContainer = backOrange,
        onSecondaryContainer = deepBrown,

        /** Accent */
        tertiary = lightOrange,
        onTertiary = deepBrown,
        tertiaryContainer = paleAmber,
        onTertiaryContainer = deepBrown,
        background = warmWhite,
        onBackground = warmBlack,
        surface = warmWhite,
        onSurface = warmBlack,
        surfaceVariant = warmSurfaceHighest,

        /** Muted text, unselected icon */
        onSurfaceVariant = warmGray,
        surfaceTint = darkOrange,
        inverseSurface = warmCharcoal,
        inverseOnSurface = warmSurfaceLow,
        error = red,
        onError = Color.White,
        errorContainer = paleRust,
        onErrorContainer = maroon,

        /** Border that must be seen, e.g. around a text field */
        outline = warmOutline,

        /** Decorative border, divider */
        outlineVariant = warmOutlineSoft,
        scrim = Color.Black,
        surfaceBright = warmWhite,
        surfaceDim = warmSurfaceDim,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = warmSurfaceLow,

        /** Card, list row, bottom navigation bar */
        surfaceContainer = warmSurface,

        /** Dialog */
        surfaceContainerHigh = warmSurfaceHigh,
        surfaceContainerHighest = warmSurfaceHighest,
    )

/** Corner radii of the Material components, from the smallest component to the largest. */
private val AmberShapes =
    Shapes(
        extraSmall = RoundedCornerShape(ROUND_CORNER_MINI),
        small = RoundedCornerShape(ROUND_CORNER_SMALL),
        medium = RoundedCornerShape(ROUND_CORNER),
        large = RoundedCornerShape(ROUND_CORNER_BIG),
        extraLarge = RoundedCornerShape(ROUND_CORNER_ENORMOUS),
    )

@Composable
fun AmberAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Dynamic color is available on Android 12+
    content: @Composable () -> Unit,
) {
  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
      }
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }

  MaterialTheme(
      colorScheme = colorScheme,
      shapes = AmberShapes,
      typography = AmberTypography,
      content = content,
  )
}
