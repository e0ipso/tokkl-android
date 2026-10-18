package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
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
        Token("--texture-glow", Color(0xFFF3DFD1), Color(0xFF352A38)) { it.textureGlow },
        Token("--texture-swell", Color(0xFFE0EAE3), Color(0xFF17324B)) { it.textureSwell },
        Token("--texture-stripe", Color(0xFFC7DCD7), Color(0xFF1A475D)) { it.textureStripe },
        Token("--scene-glow", Color(0xFFDFB698), Color(0xFF4F332E)) { it.sceneGlow },
        Token("--scene-swell", Color(0xFFB9CBBB), Color(0xFF134354)) { it.sceneSwell },
        Token("--scene-stripe", Color(0xFFE9CEB3), Color(0xFF2D212A)) { it.sceneStripe },
        Token("--wordmark-halo", Color.Transparent, Color(0xFFA9B0D6)) { it.wordmarkHalo },
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
     * colour changes, so a palette change that skips this fails here. A channel may differ by one,
     * because the light --scene-stripe green mixes to 206.5 and styles.css stores 206.
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
            mismatch("--texture-glow", mix(s.primary, 0.14, s.surface), x.textureGlow, 1),
            mismatch("--texture-swell", mix(s.secondary, 0.12, s.surface), x.textureSwell, 1),
            mismatch("--texture-stripe", mix(s.secondary, 0.22, s.surface), x.textureStripe, 1),
            mismatch("--scene-glow", mix(s.primary, 0.28, s.background), x.sceneGlow, 1),
            mismatch("--scene-swell", mix(s.secondary, 0.24, s.background), x.sceneSwell, 1),
            mismatch("--scene-stripe", mix(s.primary, 0.14, s.background), x.sceneStripe, 1),
        )
        assertTrue("Values that differ from their mix:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun mismatch(name: String, expected: Color, actual: Color, tolerance: Int = 0): String? {
        val close = listOf(24, 16, 8, 0).all { shift ->
            val e = (expected.toArgb() shr shift) and 0xFF
            val a = (actual.toArgb() shr shift) and 0xFF
            abs(e - a) <= tolerance
        }
        return if (close) null else "$name: expected ${hex(expected)}, got ${hex(actual)}"
    }

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
