package com.github.e0ipso.tokkl.ui.theme

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Tokkl's type scale (docs/PRD.md, Visual direction, Spacing and type scale): each named role's
 * size and weight, and the font each Anybody role really draws with. Compose falls back to the
 * nearest registered weight, so a 900 role with only the 800 instance registered draws at 800.
 * Shaping a glyph with the resolved typeface reports the variable font's axes as drawn.
 */
class TypeScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun namedRolesFollowTheScale() {
        assertRole("page title", TokklTheme.pageTitleTextStyle, size = 28, weight = 900, letterSpacingEm = 0.02f)
        assertRole("clock", TokklTheme.clockTextStyle, size = 36, weight = 800)
        assertRole("totals", TokklTheme.totalTextStyle, size = 28, weight = 800)
        assertRole("day total", TokklTheme.dayTotalTextStyle, size = 22, weight = 800)
        assertRole("day header", TokklTheme.dayHeaderTextStyle, size = 17, weight = 900)
        assertRole("card title", TokklTheme.cardTitleTextStyle, size = 18, weight = 800)
        assertRole("section heading", TokklTheme.sectionHeadingTextStyle, size = 16, weight = 800)
        assertRole("button label", TokklTheme.buttonLabelTextStyle, size = 14, weight = 700)
        assertEquals("tabular clock digits", "tnum", TokklTheme.clockTextStyle.fontFeatureSettings)
        assertEquals("tabular total digits", "tnum", TokklTheme.totalTextStyle.fontFeatureSettings)
        assertEquals("tabular day total digits", "tnum", TokklTheme.dayTotalTextStyle.fontFeatureSettings)
        assertEquals("monospace family", FontFamily.Monospace, TokklTheme.monospaceTextStyle.fontFamily)

        lateinit var typography: Typography
        lateinit var smallCaps: TextStyle
        composeRule.setContent {
            TokklTheme {
                typography = MaterialTheme.typography
                smallCaps = TokklTheme.smallCapsLabelTextStyle
            }
        }
        composeRule.waitForIdle()
        assertRole("small-caps label", smallCaps, size = 12, weight = 700, letterSpacingEm = 0.06f)
        assertSame("headlineLarge is the page title", TokklTheme.pageTitleTextStyle, typography.headlineLarge)
        assertSame("titleMedium is the section heading", TokklTheme.sectionHeadingTextStyle, typography.titleMedium)
        assertSame("labelLarge is the button label", TokklTheme.buttonLabelTextStyle, typography.labelLarge)
        for ((name, style) in listOf("bodyLarge" to typography.bodyLarge, "bodyMedium" to typography.bodyMedium, "bodySmall" to typography.bodySmall)) {
            assertEquals("$name weight", FontWeight.Normal, style.fontWeight)
        }
        assertEquals("body sizes", listOf(16.sp, 14.sp, 12.sp), listOf(typography.bodyLarge, typography.bodyMedium, typography.bodySmall).map { it.fontSize })
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    fun anybodyRolesDrawAtTheirWeightAndWidth85() {
        val drawn = mutableMapOf<String, Map<String, Float>>()
        val roles = mapOf(
            "page title" to TokklTheme.pageTitleTextStyle,
            "day header" to TokklTheme.dayHeaderTextStyle,
            "clock" to TokklTheme.clockTextStyle,
            "section heading" to TokklTheme.sectionHeadingTextStyle,
        )
        composeRule.setContent {
            val resolver = LocalFontFamilyResolver.current
            for ((name, style) in roles) {
                val typeface = resolver.resolve(style.fontFamily, checkNotNull(style.fontWeight)).value as Typeface
                drawn[name] = axesOf(typeface)
            }
        }
        composeRule.waitForIdle()

        for ((name, style) in roles) {
            val axes = drawn.getValue(name)
            assertEquals("$name weight as drawn", checkNotNull(style.fontWeight).weight.toFloat(), axes["wght"])
            assertEquals("$name width as drawn", ANYBODY_WIDTH, axes["wdth"])
        }
    }

    private fun assertRole(name: String, style: TextStyle, size: Int, weight: Int, letterSpacingEm: Float? = null) {
        assertEquals("$name size", size.sp, style.fontSize)
        assertEquals("$name weight", FontWeight(weight), style.fontWeight)
        if (letterSpacingEm != null) assertEquals("$name tracking", letterSpacingEm.em, style.letterSpacing)
    }

    /** The variation axes of the font that draws "A" in [typeface]. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun axesOf(typeface: Typeface): Map<String, Float> {
        val paint = Paint().apply { this.typeface = typeface }
        val glyphs = TextRunShaper.shapeTextRun("A", 0, 1, 0, 1, 0f, 0f, false, paint)
        return glyphs.getFont(0).axes.orEmpty().associate { it.tag to it.styleValue }
    }
}
