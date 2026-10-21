package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The card's 2dp frame and 3dp hard shadow, including the pixels outside its bounds. */
class StickerCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightCardHasAnInkFrameAndHardOffsetShadow() = assertCard(dark = false)

    @Test
    fun darkCardHasALavenderFrameAndHardOffsetShadow() = assertCard(dark = true)

    private fun assertCard(dark: Boolean) {
        composeRule.setContent {
            TokklTheme(darkTheme = dark) {
                Box(Modifier.size(112.dp).background(MaterialTheme.colorScheme.background).testTag("card-canvas")) {
                    StickerCard(Modifier.padding(12.dp).size(80.dp), shape = RectangleShape) {
                        Box(Modifier.size(80.dp))
                    }
                }
            }
        }
        val pixels = composeRule.onNodeWithTag("card-canvas").captureToImage().toPixelMap()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val edge = Color(if (dark) 0xFF8C86C0 else 0xFF08061F)
        val surface = Color(if (dark) 0xFF14112E else 0xFFFFFFFF)
        val background = Color(if (dark) 0xFF08061F else 0xFFFFFDF7)
        fun check(x: Float, y: Float, expected: Color) {
            val actual = pixels[(x * density).roundToInt(), (y * density).roundToInt()]
            assertTrue("pixel at $x,$y is $actual, expected $expected", actual.near(expected))
        }
        // At the middle of a straight edge, the first 2dp belong to the frame.
        check(13f, 50f, edge)
        check(14.5f, 50f, surface)
        // The shadow ends exactly 3dp beyond the right and bottom, with no blurred pixels.
        check(94f, 50f, edge)
        check(96f, 50f, background)
        check(50f, 94f, edge)
        check(50f, 96f, background)
        // Nothing is offset above or to the left.
        check(11f, 50f, background)
        check(50f, 11f, background)
    }

    private fun Color.near(other: Color): Boolean =
        listOf(red to other.red, green to other.green, blue to other.blue).all { (a, b) -> abs(a - b) * 255 <= 2 }
}
