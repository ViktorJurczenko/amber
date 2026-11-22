// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that the Amber color schemes stay readable, with the WCAG 2 contrast ratio: 4.5:1 is the
 * minimum for text (level AA) and 7:1 the enhanced level (AAA), which patient screens aim for.
 */
class ThemeContrastTest {

  private val schemes = mapOf("light" to LightColorScheme, "dark" to DarkColorScheme)

  /** WCAG 2 contrast ratio between two opaque colors, from 1 (identical) to 21 (black on white). */
  private fun contrast(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
  }

  private fun assertContrast(name: String, first: Color, second: Color, minimum: Float) {
    val ratio = contrast(first, second)
    assertTrue("$name has a contrast of $ratio, below $minimum", ratio >= minimum)
  }

  /** Every container color of [scheme] with the color of the content drawn on it. */
  private fun contentPairs(scheme: ColorScheme): Map<String, Pair<Color, Color>> =
      mapOf(
          "primary" to (scheme.primary to scheme.onPrimary),
          "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
          "secondary" to (scheme.secondary to scheme.onSecondary),
          "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
          "tertiary" to (scheme.tertiary to scheme.onTertiary),
          "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
          "background" to (scheme.background to scheme.onBackground),
          "surface" to (scheme.surface to scheme.onSurface),
          "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
          "error" to (scheme.error to scheme.onError),
          "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
          "inverseSurface" to (scheme.inverseSurface to scheme.inverseOnSurface),
      )

  /** The raised surfaces of [scheme], on which cards, list rows and dialogs are drawn. */
  private fun surfaceContainers(scheme: ColorScheme): Map<String, Color> =
      mapOf(
          "surfaceContainerLowest" to scheme.surfaceContainerLowest,
          "surfaceContainerLow" to scheme.surfaceContainerLow,
          "surfaceContainer" to scheme.surfaceContainer,
          "surfaceContainerHigh" to scheme.surfaceContainerHigh,
          "surfaceContainerHighest" to scheme.surfaceContainerHighest,
      )

  @Test
  fun everyContentColorIsReadableOnItsContainer() {
    schemes.forEach { (mode, scheme) ->
      contentPairs(scheme).forEach { (role, pair) ->
        assertContrast("$mode $role", pair.first, pair.second, TEXT_CONTRAST)
      }
    }
  }

  @Test
  fun textIsReadableOnEveryRaisedSurface() {
    schemes.forEach { (mode, scheme) ->
      surfaceContainers(scheme).forEach { (role, container) ->
        assertContrast("$mode onSurface on $role", scheme.onSurface, container, TEXT_CONTRAST)
        assertContrast(
            "$mode onSurfaceVariant on $role",
            scheme.onSurfaceVariant,
            container,
            TEXT_CONTRAST,
        )
      }
    }
  }

  @Test
  fun buttonsBodyTextAndTitlesReachTheEnhancedContrast() {
    schemes.forEach { (mode, scheme) ->
      assertContrast("$mode button", scheme.primary, scheme.onPrimary, ENHANCED_CONTRAST)
      assertContrast("$mode body", scheme.background, scheme.onBackground, ENHANCED_CONTRAST)
      assertContrast("$mode title", scheme.background, scheme.primary, ENHANCED_CONTRAST)
      assertContrast("$mode card", scheme.surfaceContainer, scheme.onSurface, ENHANCED_CONTRAST)
      assertContrast(
          "$mode highlighted card",
          scheme.primaryContainer,
          scheme.onPrimaryContainer,
          ENHANCED_CONTRAST,
      )
    }
  }

  @Test
  fun patientCardsAndMessagesReachTheEnhancedContrast() {
    schemes.forEach { (mode, scheme) ->
      // The audio card and the details of a memory are one tone above their card
      assertContrast(
          "$mode nested card",
          scheme.surfaceContainerHigh,
          scheme.onSurface,
          ENHANCED_CONTRAST,
      )
      // The blocking error message of the patient lists
      assertContrast(
          "$mode error message",
          scheme.errorContainer,
          scheme.onErrorContainer,
          ENHANCED_CONTRAST,
      )
    }
  }

  @Test
  fun audioCardGraphicsAreVisible() {
    schemes.forEach { (mode, scheme) ->
      val card = scheme.surfaceContainerHigh
      assertContrast("$mode waveform", scheme.outline, card, GRAPHIC_CONTRAST)
      assertContrast("$mode played waveform", scheme.primary, card, GRAPHIC_CONTRAST)
      assertContrast("$mode error icon", scheme.error, card, GRAPHIC_CONTRAST)
    }
  }

  @Test
  fun errorTextIsReadableWhereItIsShown() {
    schemes.forEach { (mode, scheme) ->
      assertContrast("$mode error on background", scheme.error, scheme.background, TEXT_CONTRAST)
      assertContrast("$mode error on card", scheme.error, scheme.surfaceContainer, TEXT_CONTRAST)
    }
  }

  @Test
  fun bordersThatCarryMeaningAreVisible() {
    schemes.forEach { (mode, scheme) ->
      assertContrast("$mode outline", scheme.outline, scheme.background, GRAPHIC_CONTRAST)
      assertContrast("$mode button edge", scheme.primary, scheme.background, GRAPHIC_CONTRAST)
    }
  }

  @Test
  fun anErrorDoesNotLookLikeATitle() {
    schemes.forEach { (mode, scheme) ->
      assertNotEquals("$mode error", scheme.primary, scheme.error)
      assertNotEquals("$mode error", scheme.secondary, scheme.error)
    }
  }

  @Test
  fun noSurfaceOrContainerFallsBackToTheMaterialBaseline() {
    val baselines = mapOf("light" to lightColorScheme(), "dark" to darkColorScheme())
    schemes.forEach { (mode, scheme) ->
      val baseline = baselines.getValue(mode)
      val roles: Map<String, (ColorScheme) -> Color> =
          mapOf(
              "primaryContainer" to { it.primaryContainer },
              "secondaryContainer" to { it.secondaryContainer },
              "tertiaryContainer" to { it.tertiaryContainer },
              "surfaceVariant" to { it.surfaceVariant },
              "onSurfaceVariant" to { it.onSurfaceVariant },
              "surfaceTint" to { it.surfaceTint },
              "inverseSurface" to { it.inverseSurface },
              "inversePrimary" to { it.inversePrimary },
              "outline" to { it.outline },
              "outlineVariant" to { it.outlineVariant },
              "surfaceBright" to { it.surfaceBright },
              "surfaceDim" to { it.surfaceDim },
              "surfaceContainerLow" to { it.surfaceContainerLow },
              "surfaceContainer" to { it.surfaceContainer },
              "surfaceContainerHigh" to { it.surfaceContainerHigh },
              "surfaceContainerHighest" to { it.surfaceContainerHighest },
          )
      roles.forEach { (role, color) ->
        assertNotEquals("$mode $role is Material's default", color(baseline), color(scheme))
      }
    }
  }

  private companion object {
    /** Added to both luminances by the WCAG formula, so that black does not divide by zero. */
    const val LUMINANCE_OFFSET = 0.05f

    /** WCAG AA for meaningful graphics such as the border of a control. */
    const val GRAPHIC_CONTRAST = 3f

    /** WCAG AA for text. */
    const val TEXT_CONTRAST = 4.5f

    /** WCAG AAA for text. */
    const val ENHANCED_CONTRAST = 7f
  }
}
