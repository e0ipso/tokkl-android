package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The shared top bar on its own: its height, the title as a heading, the back button only when a
 * page asks for one, and the spectrum strip along its bottom, a gradient under the controls.
 * PageTopBarTest checks the pages that use it, and LicencesScreenTest that it stays put.
 */
class TopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density

    @Test
    fun theBarIs64dpTallWithItsTitleAsAHeading() {
        show(onBack = null)

        val bar = composeRule.onNodeWithTag(BAR).getUnclippedBoundsInRoot()
        assertEquals(TopBarHeight.value, (bar.bottom - bar.top).value, PIXEL_DP)
        composeRule.onNode(hasText(TITLE) and isHeading()).assertExists()
    }

    @Test
    fun aPageWithoutBackHasNoBackButton() {
        show(onBack = null)

        composeRule.onAllNodesWithContentDescription(context.getString(R.string.back)).assertCountEquals(0)
    }

    @Test
    fun theBackButtonIs48dpSaysBackAndGoesBack() {
        var backs = 0
        show(onBack = { backs++ })

        val back = composeRule.onNode(
            hasContentDescription(context.getString(R.string.back)) and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        val bounds = back.getUnclippedBoundsInRoot()
        assertTrue("the back button is ${bounds.right - bounds.left} wide", bounds.right - bounds.left >= MIN_TOUCH_TARGET - PIXEL_DP.dp)
        assertTrue("the back button is ${bounds.bottom - bounds.top} tall", bounds.bottom - bounds.top >= MIN_TOUCH_TARGET - PIXEL_DP.dp)
        back.performClick()
        assertEquals(1, backs)
    }

    @Test
    fun inLightTheSpectrumStripIsAGradientUnderTheControls() = assertSpectrumStrip(dark = false)

    @Test
    fun inDarkTheSpectrumStripIsAGradientUnderTheControls() = assertSpectrumStrip(dark = true)

    /**
     * Along the strip the colour runs through the wordmark's gradient, `--spectrum-strip` in
     * styles.css, the same in light and dark: yellow at the left edge, the cyan stop half way and
     * magenta at the right edge, with a mix of the pink and the cyan between them. The back button
     * and the title end above the strip.
     */
    private fun assertSpectrumStrip(dark: Boolean) {
        show(onBack = {}, dark = dark)

        val image = composeRule.onNodeWithTag(BAR).captureToImage().toPixelMap()
        val stripBottom = image.height - BORDER.px()
        val stripTop = stripBottom - TokklSpacing.space1.px()
        val y = (stripTop + stripBottom) / 2
        val start = image[0, y]
        val between = image[(image.width * BETWEEN_STOPS).roundToInt(), y]
        val middle = image[image.width / 2, y]
        val end = image[image.width - 1, y]
        assertTrue("the strip starts as yellow: $start, not $FIRST_STOP", start.near(FIRST_STOP))
        assertTrue("half way the strip is the cyan stop: $middle, not $MIDDLE_STOP", middle.near(MIDDLE_STOP, STOP_TOLERANCE))
        assertTrue("the strip ends as magenta: $end, not $LAST_STOP", end.near(LAST_STOP, STOP_TOLERANCE))
        assertTrue("between the pink and the cyan stops the strip is a mix: $between", between.between(PINK_STOP, MIDDLE_STOP))

        val controlsBottom = listOf(
            composeRule.onNode(hasContentDescription(context.getString(R.string.back))),
            composeRule.onNode(hasText(TITLE) and isHeading()),
        ).maxOf { it.getUnclippedBoundsInRoot().bottom.value }
        val bar = composeRule.onNodeWithTag(BAR).getUnclippedBoundsInRoot()
        val stripTopDp = bar.top.value + stripTop / density
        assertTrue("the controls end at $controlsBottom, below the strip's top at $stripTopDp", controlsBottom <= stripTopDp + PIXEL_DP)
    }

    private fun show(onBack: (() -> Unit)?, dark: Boolean = false) {
        composeRule.setContent {
            TokklTheme(darkTheme = dark) {
                TokklTopBar(TITLE, Modifier.testTag(BAR), onBack = onBack, windowInsets = WindowInsets(0))
            }
        }
    }

    private fun Dp.px(): Int = (value * density).roundToInt()

    private fun Color.near(other: Color, tolerance: Int = TOLERANCE): Boolean =
        channels().zip(other.channels()).all { (a, b) -> abs(a - b) <= tolerance }

    /** Whether each channel lies between [a]'s and [b]'s, and the colour is clearly neither. */
    private fun Color.between(a: Color, b: Color): Boolean =
        channels().zip(a.channels().zip(b.channels())).all { (c, ends) ->
            c in minOf(ends.first, ends.second) - TOLERANCE..maxOf(ends.first, ends.second) + TOLERANCE
        } && !near(a) && !near(b)

    private fun Color.channels(): List<Int> = listOf(red, green, blue).map { (it * 255).roundToInt() }

    private companion object {
        const val BAR = "bar"
        const val TITLE = "Licences"
        const val PIXEL_DP = 1f
        const val TOLERANCE = 2

        /** Half way from the pink stop, at a third of the width, to the cyan one, at half of it. */
        const val BETWEEN_STOPS = 5f / 12

        /** A stop's pixel may sit a pixel off the stop, where the gradient changes by a few levels. */
        const val STOP_TOLERANCE = 6
        val FIRST_STOP = Color(0xFFFFE900)
        val PINK_STOP = Color(0xFFFF008C)
        val MIDDLE_STOP = Color(0xFF00DFD5)
        val LAST_STOP = Color(0xFFEB00D5)
        val MIN_TOUCH_TARGET = 48.dp
        val BORDER = 1.dp
    }
}
