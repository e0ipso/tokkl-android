package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The texture, scene and wordmark-halo colours against Tokkl's styles.css, which writes each
 * token once as light-dark(light, dark) in its first :root block.
 */
class TextureTokensTest {

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
