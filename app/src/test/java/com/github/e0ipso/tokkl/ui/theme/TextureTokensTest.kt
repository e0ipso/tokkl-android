package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The texture, scene and wordmark-halo colours against Tokkl's styles.css, which writes each
 * token once as light-dark(light, dark) in its first :root block.
 */
class TextureTokensTest {

    private class Token(
        val name: String,
        val light: Color,
        val dark: Color,
        val actual: (TokklExtraColors) -> Color,
    )

    /** Copied from `../tokkl/packages/web/src/styles.css`, tokens `--texture-*`, `--scene-*` and `--wordmark-halo`. */
    private val stylesCss = listOf(
        Token("--texture-glow", Color(0xFFFDE8DB), Color(0xFF351D28)) { it.textureGlow },
        Token("--texture-swell", Color(0xFFE1F4F4), Color(0xFF122A42)) { it.textureSwell },
        Token("--texture-stripe", Color(0xFFC7EBEB), Color(0xFF103E53)) { it.textureStripe },
        Token("--scene-glow", Color(0xFFFBCFB2), Color(0xFF4D2116)) { it.sceneGlow },
        Token("--scene-swell", Color(0xFFC2E8E3), Color(0xFF063A4B)) { it.sceneSwell },
        Token("--scene-stripe", Color(0xFFFDE6D4), Color(0xFF2B131B)) { it.sceneStripe },
        Token("--wordmark-halo", Color.Transparent, Color(0xFFB9B5D6)) { it.wordmarkHalo },
    )

    @Test
    fun lightValuesMatchStylesCss() =
        assertValues(TokklLightExtraColors) { it.light }

    @Test
    fun darkValuesMatchStylesCss() =
        assertValues(TokklDarkExtraColors) { it.dark }

    @Test
    fun onlyDarkDrawsTheWordmarkHalo() {
        assertEquals(0f, TokklLightExtraColors.wordmarkHalo.alpha, 0f)
        assertEquals(1f, TokklDarkExtraColors.wordmarkHalo.alpha, 0f)
    }

    /**
     * styles.css computes the texture values as primary at 14% (glow) and secondary at 12% (swell)
     * and 22% (stripe) over surface, and the scene values as primary at 28% (glow) and 14% (stripe)
     * and secondary at 24% (swell) over bg, all mixed in sRGB. It says to recompute them when a
     * colour changes, so a palette change that skips this fails here. Every channel matches its
     * mix rounded to the nearest whole number.
     */
    @Test
    fun lightMixesMatchTheirFormulas() = assertMixes(TokklLightColorScheme, TokklLightExtraColors)

    @Test
    fun darkMixesMatchTheirFormulas() = assertMixes(TokklDarkColorScheme, TokklDarkExtraColors)

    private fun assertValues(extra: TokklExtraColors, expected: (Token) -> Color) {
        val failures = stylesCss.mapNotNull { token -> mismatch(token.name, expected(token), token.actual(extra)) }
        assertTrue("Values that differ from styles.css:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun assertMixes(s: ColorScheme, x: TokklExtraColors) {
        val failures = listOfNotNull(
            mismatch("--texture-glow", mix(x.primaryFill, 0.14, s.surface), x.textureGlow),
            mismatch("--texture-swell", mix(s.secondary, 0.12, s.surface), x.textureSwell),
            mismatch("--texture-stripe", mix(s.secondary, 0.22, s.surface), x.textureStripe),
            mismatch("--scene-glow", mix(x.primaryFill, 0.28, s.background), x.sceneGlow),
            mismatch("--scene-swell", mix(s.secondary, 0.24, s.background), x.sceneSwell),
            mismatch("--scene-stripe", mix(x.primaryFill, 0.14, s.background), x.sceneStripe),
        )
        assertTrue("Values that differ from their mix:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun mismatch(name: String, expected: Color, actual: Color): String? =
        if (expected.toArgb() == actual.toArgb()) null else "$name: expected ${hex(expected)}, got ${hex(actual)}"

    private fun hex(colour: Color) = "#%08X".format(colour.toArgb())

    /** CSS `color-mix(in srgb, top share, base)` for opaque colours, rounded per channel. */
    private fun mix(top: Color, share: Double, base: Color): Color {
        fun channel(shift: Int): Int {
            val t = (top.toArgb() shr shift) and 0xFF
            val b = (base.toArgb() shr shift) and 0xFF
            return (t * share + b * (1 - share)).roundToInt()
        }
        return Color(red = channel(16), green = channel(8), blue = channel(0))
    }
}
