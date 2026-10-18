package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Checks the clock and totals styles at Android's largest font scale before any screen uses
 * them. The longest realistic clock and a multi-digit total sit one under the other in a narrow
 * column. Every glyph must show in full, and neither sample may overlap the other.
 *
 * The column is narrower than either sample, so both wrap. A style's line height matters only
 * between lines. Compose keeps the font's own ascent above the first line and its descent below
 * the last, so a single line keeps its glyphs whatever line height the style sets. Each line's
 * glyphs have to stay inside that line's box. A line height shorter than the glyphs fails,
 * because neighbouring lines then run into each other.
 *
 * Both samples share one layout. Each check draws one sample and leaves the other transparent,
 * so every inked pixel belongs to the sample under test.
 */
class ClockAndTotalStylesFitTest {
    private val composeRule = createComposeRule()

    /** The font scale is set before the test activity starts, so it never has to restart. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST)).around(composeRule)

    private var inked by mutableStateOf(CLOCK)
    private val layouts = mutableMapOf<String, TextLayoutResult>()
    private var fontScale = 0f

    @Test
    fun clockAndTotalFitInANarrowColumn() {
        composeRule.setContent {
            fontScale = LocalDensity.current.fontScale
            TokklTheme {
                // The margin around the column shows glyphs that reach past their box.
                Box(Modifier.testTag(HOST).background(MaterialTheme.colorScheme.background).padding(MARGIN)) {
                    Column(Modifier.width(NARROW_WIDTH)) {
                        Sample(CLOCK, CLOCK_SAMPLE, TokklTheme.clockTextStyle)
                        Sample(TOTAL, TOTAL_SAMPLE, TokklTheme.totalTextStyle)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("font scale", SystemFontScale.LARGEST, fontScale)

        for (sample in listOf(CLOCK, TOTAL)) {
            inked = sample
            composeRule.waitForIdle()
            val layout = layouts.getValue(sample)
            assertTrue("$sample fits on one line, so its line height goes untested", layout.lineCount > 1)
            assertFalse("$sample layout overflows its height", layout.didOverflowHeight)
            assertGlyphsInsideTheirLines(sample, bounds(sample), layout)
        }
        assertFalse("$CLOCK overlaps $TOTAL", bounds(CLOCK).overlaps(bounds(TOTAL)))
    }

    @Composable
    private fun Sample(tag: String, text: String, style: TextStyle) {
        Text(
            text = text,
            style = style,
            color = if (inked == tag) MaterialTheme.colorScheme.onBackground else Color.Transparent,
            onTextLayout = { layouts[tag] = it },
            modifier = Modifier.testTag(tag),
        )
    }

    /**
     * Every pixel that differs from the margin's colour lies inside [box]. The rows that hold
     * such pixels form one band per line, and each band lies inside its line's box. The samples
     * are digits and colons, so a gap of empty rows separates the glyphs of lines that keep
     * apart.
     */
    private fun assertGlyphsInsideTheirLines(sample: String, box: Rect, layout: TextLayoutResult) {
        val host = bounds(HOST)
        val pixels = composeRule.onNodeWithTag(HOST).captureToImage().toPixelMap()
        val background = pixels[0, 0]
        val outside = mutableListOf<Pair<Int, Int>>()
        val inkedRows = (0 until pixels.height).filter { y ->
            var inked = false
            for (x in 0 until pixels.width) {
                if (pixels[x, y] == background) continue
                inked = true
                // The centre of the pixel, in window coordinates like the boxes.
                if (!box.containsPoint(host.left + x + 0.5f, host.top + y + 0.5f)) outside += x to y
            }
            inked
        }
        assertTrue(
            "$sample has ${outside.size} glyph pixels outside its box $box, such as ${outside.take(5)} " +
                "in the host at $host",
            outside.isEmpty(),
        )

        val bands = inkedRows.bands().map { (first, last) -> host.top + first..host.top + last + 1f }
        val lines = (0 until layout.lineCount).map { box.top + layout.getLineTop(it)..box.top + layout.getLineBottom(it) }
        assertEquals("$sample glyph rows $bands for lines $lines", lines.size, bands.size)
        for ((i, band) in bands.withIndex()) {
            val line = lines[i]
            assertTrue(
                "$sample line $i glyphs at $band leave the line's box $line",
                band.start >= line.start && band.endInclusive <= line.endInclusive,
            )
        }
    }

    /** Runs of consecutive row numbers, as first and last row. */
    private fun List<Int>.bands(): List<Pair<Int, Int>> =
        fold(mutableListOf<Pair<Int, Int>>()) { runs, row ->
            val last = runs.lastOrNull()
            if (last != null && last.second == row - 1) runs[runs.lastIndex] = last.first to row else runs += row to row
            runs
        }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow

    private fun Rect.containsPoint(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    private companion object {
        const val HOST = "host"
        const val CLOCK = "clock"
        const val TOTAL = "total"

        /** The running clock just before it turns a day. */
        const val CLOCK_SAMPLE = "23:59:59"

        /** A report total over several years, in Tokkl's h:mm:ss format (packages/common/duration.ts). */
        const val TOTAL_SAMPLE = "9999:59:59"

        /** Narrower than either sample at the largest font scale, so both wrap. */
        val NARROW_WIDTH = 100.dp
        val MARGIN = 24.dp
    }
}
