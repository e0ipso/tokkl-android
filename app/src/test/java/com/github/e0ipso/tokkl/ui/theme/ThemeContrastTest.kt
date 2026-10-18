package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2 contrast for every pair the Tokkl theme defines, in light and dark: 4.5:1 for text and
 * 3:1 for other UI parts such as borders and state marks (docs/PRD.md, Visual direction, Contrast
 * and signals).
 */
class ThemeContrastTest {

    @Test
    fun lightSchemePairsMeetWcagAa() =
        assertPairsPass("light", TokklLightColorScheme, TokklLightExtraColors)

    @Test
    fun darkSchemePairsMeetWcagAa() =
        assertPairsPass("dark", TokklDarkColorScheme, TokklDarkExtraColors)

    @Test
    fun contrastMatchesWcagReferenceValues() {
        assertRatio(21.0, Color.Black, Color.White)
        assertRatio(1.0, Color.White, Color.White)
        // The contrast table at the top of styles.css records "text on bg" in light as 13.94,
        // rounded down.
        assertRatio(13.94, Color(0xFF1C1A2B), Color(0xFFF4E7CF))
    }

    private enum class Kind(val minimum: Double) { TEXT(4.5), NON_TEXT(3.0) }

    private class Pair(val name: String, val foreground: Color, val background: Color, val kind: Kind)

    private fun pairs(s: ColorScheme, x: TokklExtraColors): List<Pair> {
        val surfaces = listOf(
            "background" to s.background,
            "surface" to s.surface,
            "surfaceVariant" to s.surfaceVariant,
            "surfaceBright" to s.surfaceBright,
            "surfaceDim" to s.surfaceDim,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
        )
        val onEverySurface = listOf(
            Triple("onSurface", s.onSurface, Kind.TEXT),
            Triple("onSurfaceVariant", s.onSurfaceVariant, Kind.TEXT),
            Triple("primary", s.primary, Kind.TEXT),
            Triple("error", s.error, Kind.TEXT),
            Triple("secondary", s.secondary, Kind.NON_TEXT),
            Triple("tertiary", s.tertiary, Kind.NON_TEXT),
            Triple("outline", s.outline, Kind.NON_TEXT),
            Triple("outlineVariant", s.outlineVariant, Kind.NON_TEXT),
            Triple("running", x.running, Kind.NON_TEXT),
            Triple("billable", x.billable, Kind.NON_TEXT),
            Triple("accentLime", x.accentLime, Kind.NON_TEXT),
        )
        val surfacePairs = surfaces.flatMap { (surfaceName, surface) ->
            onEverySurface.map { (name, colour, kind) -> Pair("$name on $surfaceName", colour, surface, kind) }
        }
        val fills = listOf(
            Pair("onBackground on background", s.onBackground, s.background, Kind.TEXT),
            Pair("secondary on background", s.secondary, s.background, Kind.TEXT),
            Pair("secondary on surface", s.secondary, s.surface, Kind.TEXT),
            Pair("running on surface", x.running, s.surface, Kind.TEXT),
            Pair("onPrimary on primary", s.onPrimary, s.primary, Kind.TEXT),
            Pair("onSecondary on secondary", s.onSecondary, s.secondary, Kind.TEXT),
            Pair("onTertiary on tertiary", s.onTertiary, s.tertiary, Kind.TEXT),
            Pair("onError on error", s.onError, s.error, Kind.TEXT),
            Pair("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer, Kind.TEXT),
            Pair("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, Kind.TEXT),
            Pair("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, Kind.TEXT),
            Pair("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer, Kind.TEXT),
            Pair("inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface, Kind.TEXT),
            Pair("inversePrimary on inverseSurface", s.inversePrimary, s.inverseSurface, Kind.TEXT),
            Pair("onPrimaryFixed on primaryFixed", s.onPrimaryFixed, s.primaryFixed, Kind.TEXT),
            Pair("onPrimaryFixed on primaryFixedDim", s.onPrimaryFixed, s.primaryFixedDim, Kind.TEXT),
            Pair("onPrimaryFixedVariant on primaryFixed", s.onPrimaryFixedVariant, s.primaryFixed, Kind.TEXT),
            Pair("onSecondaryFixed on secondaryFixed", s.onSecondaryFixed, s.secondaryFixed, Kind.TEXT),
            Pair("onSecondaryFixed on secondaryFixedDim", s.onSecondaryFixed, s.secondaryFixedDim, Kind.TEXT),
            Pair("onSecondaryFixedVariant on secondaryFixed", s.onSecondaryFixedVariant, s.secondaryFixed, Kind.TEXT),
            Pair("onTertiaryFixed on tertiaryFixed", s.onTertiaryFixed, s.tertiaryFixed, Kind.TEXT),
            Pair("onTertiaryFixed on tertiaryFixedDim", s.onTertiaryFixed, s.tertiaryFixedDim, Kind.TEXT),
            Pair("onTertiaryFixedVariant on tertiaryFixed", s.onTertiaryFixedVariant, s.tertiaryFixed, Kind.TEXT),
        )
        return surfacePairs + fills
    }

    private fun assertPairsPass(scheme: String, colours: ColorScheme, extra: TokklExtraColors) {
        val failures = pairs(colours, extra).mapNotNull { pair ->
            val ratio = contrast(pair.foreground, pair.background)
            if (ratio >= pair.kind.minimum) {
                null
            } else {
                "%s: %.2f:1, needs %.1f:1 for %s".format(pair.name, ratio, pair.kind.minimum, pair.kind)
            }
        }
        assertTrue("$scheme scheme pairs below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun assertRatio(expected: Double, a: Color, b: Color) {
        val ratio = contrast(a, b)
        assertTrue("expected $expected, got $ratio", ratio >= expected && ratio < expected + 0.01)
    }

    /** The WCAG 2 contrast ratio, from the relative luminance of each sRGB colour. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(colour: Color): Double {
        val argb = colour.toArgb()
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
