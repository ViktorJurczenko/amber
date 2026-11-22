// Written by LaMaier
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * App's primary colors
 *
 * Shades of orange are the dominant colors in the Amber app. They are use for lightMode. Shades of
 * cyan are used to provide contrast and for the darkMode.
 *
 * Screens never use these values directly: they read a role from `MaterialTheme.colorScheme`, which
 * [Theme] fills with them.
 */

// ------- BRAND COLORS, LIGHT MODE -------

/** In lightMode, for : selected chips, selected tab indicator, icon circles */
val backOrange = Color(0xFFFFCF9D)

/** In lightMode, for : accents */
val lightOrange = Color(0xFFFBB05D)

/** In lightMode, for : Secondary titles */
val orange = Color(0xFFCE5431)

/** In lightMode, for : Main titles, filled buttons, the back arrow and its label */
val darkOrange = Color(0xFF9D3121)

// ------- BRAND COLORS, DARK MODE -------

/** In darkMode, for : selected chips, selected tab indicator, icon circles */
val backCyan = Color(0xFF8FD789)

/** In darkMode, for : Secondary titles */
val lightCyan = Color(0xFFA6D9B2)

/** In darkMode, for : Main titles, filled buttons */
val cyan = Color(0xFF94F5D6)

/** In darkMode, for : raised surfaces such as dialogs */
val darkCyan = Color(0xFF255556)

/** In darkMode, for : accents and the inverse surface */
val lightGray = Color(0xFFE6E6E6)

/*
 * The colors below were added for the Material 3 redesign. Material components read about forty
 * color roles; the ones the theme does not set fall back to Material's purple baseline. Each value
 * is a tint of the brand hue (red-orange in light mode, cyan in dark mode), chosen so that text on
 * it stays readable. ThemeContrastTest checks the contrast of every pair.
 */

// ------- WARM NEUTRALS, LIGHT MODE -------

/** In lightMode, for : Background. A warm white is softer on the eyes than pure white. */
val warmWhite = Color(0xFFFFFBF9)

/** In lightMode, for : Text. A warm near-black glares less than pure black on a light screen. */
val warmBlack = Color(0xFF231917)

/** In lightMode, for : the quietest raised surface, and text on the inverse surface */
val warmSurfaceLow = Color(0xFFFFF1EB)

/** In lightMode, for : cards, list rows, the bottom navigation bar */
val warmSurface = Color(0xFFFCEAE3)

/** In lightMode, for : dialogs */
val warmSurfaceHigh = Color(0xFFF6E4DD)

/** In lightMode, for : the most prominent raised surface */
val warmSurfaceHighest = Color(0xFFF0DED7)

/** In lightMode, for : a surface dimmer than the background */
val warmSurfaceDim = Color(0xFFE8D6D0)

/** In lightMode, for : muted text and unselected icons */
val warmGray = Color(0xFF53433F)

/** In lightMode, for : borders that must be seen, such as a text field outline */
val warmOutline = Color(0xFF85736E)

/** In lightMode, for : decorative borders and dividers */
val warmOutlineSoft = Color(0xFFD8C2BB)

/** In lightMode, for : the inverse surface, e.g. a snackbar */
val warmCharcoal = Color(0xFF392E2B)

// ------- CONTAINERS AND ERRORS, LIGHT MODE -------

/** In lightMode, for : highlighted cards and error cards. In darkMode, for : text on error cards */
val paleRust = Color(0xFFFFDAD1)

/** In lightMode, for : text on [paleRust]. In darkMode, for : text on the error color */
val maroon = Color(0xFF3E0A02)

/** In lightMode, for : text and icons on the orange tints */
val deepBrown = Color(0xFF2B1700)

/** In lightMode, for : accent cards */
val paleAmber = Color(0xFFFFDDB8)

/** In lightMode, for : Errors. A true red, so that an error is not mistaken for a title. */
val red = Color(0xFFBA1A1A)

/** In darkMode, for : Errors. In lightMode, for : the primary color on the inverse surface */
val salmon = Color(0xFFFFB4AB)

// ------- SURFACES AND CONTAINERS, DARK MODE -------

/** In darkMode, for : Background, and text on the light cyan tints */
val deepCyan = Color(0xFF163A3B)

/** In darkMode, for : the surface darker than the background */
val cyanSurfaceLowest = Color(0xFF102E2F)

/** In darkMode, for : the quietest raised surface */
val cyanSurfaceLow = Color(0xFF1A3F40)

/** In darkMode, for : cards, list rows, the bottom navigation bar */
val cyanSurface = Color(0xFF1F4849)

/** In darkMode, for : the most prominent raised surface */
val cyanSurfaceHighest = Color(0xFF2E6263)

/** In darkMode, for : muted text and unselected icons */
val mistCyan = Color(0xFFC5D8D6)

/** In darkMode, for : borders that must be seen, such as a text field outline */
val cyanOutline = Color(0xFF8FA9A7)

/** In darkMode, for : decorative borders and dividers */
val cyanOutlineSoft = Color(0xFF3C6566)

/** In darkMode, for : highlighted cards */
val tealGreen = Color(0xFF1B5A4E)

/** In darkMode, for : error cards */
val darkRed = Color(0xFF93000A)
