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
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
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
 * page asks for one, and the texture glow strip along its bottom, a gradient under the controls.
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
    fun inLightTheGlowStripIsAGradientUnderTheControls() = assertGlowStrip(dark = false)

    @Test
    fun inDarkTheGlowStripIsAGradientUnderTheControls() = assertGlowStrip(dark = true)

    /**
     * Along the strip the colour runs from the texture glow at the start edge to the bar's surface
     * at 70% of the width, and stays the surface after that. The back button and the title end
     * above the strip.
     */
    private fun assertGlowStrip(dark: Boolean) {
        show(onBack = {}, dark = dark)
        val glow = if (dark) TokklDarkExtraColors.textureGlow else TokklLightExtraColors.textureGlow
        val surface = if (dark) TokklDarkColorScheme.surface else TokklLightColorScheme.surface

        val image = composeRule.onNodeWithTag(BAR).captureToImage().toPixelMap()
        val stripBottom = image.height - BORDER.px()
        val stripTop = stripBottom - TokklSpacing.space1.px()
        val y = (stripTop + stripBottom) / 2
        val start = image[0, y]
        val middle = image[(image.width * MIDDLE).roundToInt(), y]
        val end = image[(image.width * PAST_GLOW).roundToInt(), y]
        assertTrue("the strip starts as the glow: $start, not $glow", start.near(glow))
        assertTrue("the strip ends as the surface: $end, not $surface", end.near(surface))
        assertTrue("half way to its end the strip is a mix: $middle", middle.between(glow, surface))

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

    private fun Color.near(other: Color): Boolean = channels().zip(other.channels()).all { (a, b) -> abs(a - b) <= TOLERANCE }

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
        const val MIDDLE = 0.35f
        const val PAST_GLOW = 0.85f
        val MIN_TOUCH_TARGET = 48.dp
        val BORDER = 1.dp
    }
}
