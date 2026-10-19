package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.e0ipso.tokkl.reports.projectMark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
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

    /**
     * The scene overlap's worst cases match the rows of styles.css's contrast table, which rounds
     * down: text on the overlap, the halo on it in dark, the wordmark's outline on it in light and
     * the outline on the halo in dark.
     */
    @Test
    fun sceneOverlapMatchesStylesCss() {
        val light = sceneCorners(TokklLightColorScheme, TokklLightExtraColors)
        val dark = sceneCorners(TokklDarkColorScheme, TokklDarkExtraColors)

        assertEquals("each channel's lowest of bg and the light scene", Color(0xFFB9B698), light.darkest)
        assertEquals("each channel's highest of bg and the dark scene", Color(0xFF4F4354), dark.lightest)
        assertRatio(8.28, TokklLightColorScheme.onSurface, light.darkest)
        assertRatio(8.32, TokklDarkColorScheme.onSurface, dark.lightest)
        assertRatio(4.36, TokklDarkExtraColors.wordmarkHalo, dark.lightest)
        assertRatio(10.11, WORDMARK_OUTLINE, light.darkest)
        assertRatio(9.77, WORDMARK_OUTLINE, TokklDarkExtraColors.wordmarkHalo)
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
        return surfacePairs + fills + navigationBarPairs(s) + texturePairs(s, x) + sceneOverlapPairs(s, x) + reportPairs(s, x)
    }

    /**
     * The colours Tokkl gives a bottom bar item: the icon sits on the primary indicator, and the
     * label sits below it, on the bar, which is surfaceContainer.
     */
    private fun navigationBarPairs(s: ColorScheme): List<Pair> {
        val item = navigationBarItemColors(s)
        val bar = s.surfaceContainer
        return listOf(
            Pair("active nav icon on indicator", item.selectedIconColor, item.selectedIndicatorColor, Kind.NON_TEXT),
            Pair("active nav label on bar", item.selectedTextColor, bar, Kind.TEXT),
            Pair("inactive nav icon on bar", item.unselectedIconColor, bar, Kind.NON_TEXT),
            Pair("inactive nav label on bar", item.unselectedTextColor, bar, Kind.TEXT),
        )
    }

    /**
     * The pairs styles.css lists for the gradients behind the top bar strip and the sign-in
     * scene: text and muted text on each texture colour, text on each scene colour, and the
     * wordmark halo on the surfaces the wordmark sits on. Light draws no halo, so it has no halo
     * pairs. sceneOverlapPairs has the pairs where the scene's gradients overlap.
     */
    private fun texturePairs(s: ColorScheme, x: TokklExtraColors): List<Pair> {
        val texture = listOf("textureGlow" to x.textureGlow, "textureSwell" to x.textureSwell, "textureStripe" to x.textureStripe)
        val scene = listOf("sceneGlow" to x.sceneGlow, "sceneSwell" to x.sceneSwell, "sceneStripe" to x.sceneStripe)
        val text = texture.flatMap { (name, colour) ->
            listOf(
                Pair("onSurface on $name", s.onSurface, colour, Kind.TEXT),
                Pair("onSurfaceVariant on $name", s.onSurfaceVariant, colour, Kind.TEXT),
            )
        } + scene.map { (name, colour) -> Pair("onSurface on $name", s.onSurface, colour, Kind.TEXT) }
        val haloDrawn = x.wordmarkHalo.alpha > 0f
        val halo = if (haloDrawn) {
            (listOf("background" to s.background, "surface" to s.surface) + texture + scene)
                .map { (name, colour) -> Pair("wordmarkHalo on $name", x.wordmarkHalo, colour, Kind.NON_TEXT) }
        } else {
            emptyList()
        }
        return text + halo
    }

    /**
     * The pairs where the sign-in scene's gradients overlap. There every channel stays between its
     * lowest and its highest value in bg and the three scene colours (Scene.kt), so a foreground
     * darker than the [Corners.darkest] corner has its worst case there, and one lighter than the
     * [Corners.lightest] corner there. A foreground between the two could meet an overlap of its
     * own luminance, so its pair fails. On the scene sit only the wordmark and, for the overlap
     * row of styles.css, text. In light the wordmark's outline draws the letters' edge; in dark it
     * matches bg, so the halo traces the edge and the outline separates the halo from the letters.
     */
    private fun sceneOverlapPairs(s: ColorScheme, x: TokklExtraColors): List<Pair> {
        val corners = sceneCorners(s, x)
        val haloDrawn = x.wordmarkHalo.alpha > 0f
        val onScene = listOfNotNull(
            overlapPair("onSurface", s.onSurface, Kind.TEXT, corners),
            overlapPair("wordmarkHalo", x.wordmarkHalo, Kind.NON_TEXT, corners).takeIf { haloDrawn },
            overlapPair("the wordmark's outline", WORDMARK_OUTLINE, Kind.NON_TEXT, corners).takeUnless { haloDrawn },
        )
        val outlineOnHalo = Pair("the wordmark's outline on wordmarkHalo", WORDMARK_OUTLINE, x.wordmarkHalo, Kind.NON_TEXT)
        return onScene + listOfNotNull(outlineOnHalo.takeIf { haloDrawn })
    }

    /** The scene overlap's corners: each channel's lowest, and highest, value in bg and the scene colours. */
    private class Corners(val darkest: Color, val lightest: Color)

    private fun sceneCorners(s: ColorScheme, x: TokklExtraColors): Corners {
        val argb = listOf(s.background, x.sceneGlow, x.sceneSwell, x.sceneStripe).map { it.toArgb() }
        fun corner(pick: (List<Int>) -> Int) = Color(
            red = pick(argb.map { (it shr 16) and 0xFF }),
            green = pick(argb.map { (it shr 8) and 0xFF }),
            blue = pick(argb.map { it and 0xFF }),
        )
        return Corners(darkest = corner { it.min() }, lightest = corner { it.max() })
    }

    private fun overlapPair(name: String, colour: Color, kind: Kind, corners: Corners): Pair {
        val own = luminance(colour)
        return when {
            own <= luminance(corners.darkest) -> Pair("$name on the scene overlap's darkest corner", colour, corners.darkest, kind)
            own >= luminance(corners.lightest) -> Pair("$name on the scene overlap's lightest corner", colour, corners.lightest, kind)
            else -> Pair("$name, which some scene overlap matches", colour, colour, kind)
        }
    }

    /**
     * The pairs styles.css lists for the report chart and the project dots, on the report card's
     * surface and on the bar track, which is chartBand. A bar's billable part is solid
     * chartBillable, and the chartOther stripes draw a non-billable part's edge; the stripes on
     * their wash are texture inside one part and need no ratio. A project's dot is its colour mixed
     * with projectMix, as the reports screen draws it, for every colour of Tokkl's project palette
     * and for the muted dot of a project without one.
     */
    private fun reportPairs(s: ColorScheme, x: TokklExtraColors): List<Pair> {
        val marks = listOf(
            Pair("chartBillable on surface", x.chartBillable, s.surface, Kind.NON_TEXT),
            Pair("chartOther on surface", x.chartOther, s.surface, Kind.NON_TEXT),
            Pair("chartBillable on the bar track, chartBand", x.chartBillable, x.chartBand, Kind.NON_TEXT),
            Pair("chartOther on the bar track, chartBand", x.chartOther, x.chartBand, Kind.NON_TEXT),
        )
        val dots = (PROJECT_COLOURS + null).map { hex ->
            val dot = projectMark(hex, x.projectMix, s.onSurfaceVariant)
            Pair("project dot ${hex ?: "without a colour"} on surface", dot, s.surface, Kind.NON_TEXT)
        }
        return marks + dots
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

    private companion object {
        /** The near-black navy outline of the wordmark artwork, which styles.css names; not a token. */
        val WORDMARK_OUTLINE = Color(0xFF000013)

        /** Tokkl's project palette, PROJECT_COLORS in ../tokkl/packages/common/colors.ts. */
        val PROJECT_COLOURS = listOf(
            "#0b83d9", "#9e5bd9", "#d94182", "#e36a00", "#bf7000", "#c7af14", "#d92b2b",
            "#2da608", "#06a893", "#c9806b", "#465bb3", "#990099", "#566614", "#525266",
        )
    }
}
