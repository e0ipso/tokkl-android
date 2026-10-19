package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The brand's halo and its fit (docs/PRD.md, Visual direction, Wordmark). The halo is checked
 * against the same brand drawn in light mode, which draws none, on the same background, and
 * against the wordmark alone on a marker colour, which shows where the artwork is.
 * LaunchScreenTest and SignInScreenTest check that TalkBack reads the brand once.
 */
class BrandTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    @Test
    fun inDarkModeAHaloInTheWordmarkHaloColourTracesTheWordmark() {
        var look by mutableStateOf(Look.DARK)
        composeRule.setContent {
            Box(Modifier.testTag(FRAME).background(if (look == Look.ON_MARKER) MARKER else DARK_BACKGROUND).padding(FRAME_PADDING)) {
                TokklTheme(darkTheme = look == Look.DARK) { Brand(BrandSize.LARGE) }
            }
        }
        val dark = capture()
        look = Look.LIGHT
        val light = capture()
        look = Look.ON_MARKER
        val marker = capture()
        assertEquals("the captures differ in size", dark.width to dark.height, marker.width to marker.height)

        val artwork = Mask(marker.width, marker.height) { x, y -> !marker[x, y].near(MARKER, EXACT) }
        val outside = (0 until marker.width).flatMap { x -> (0 until marker.height).map { y -> x to y } }
            .filterNot { (x, y) -> artwork[x, y] }
        assertTrue("light mode draws something around the brand", outside.all { (x, y) -> light[x, y].near(DARK_BACKGROUND, EXACT) })

        // The halo is the wordmark moved 1dp each way, so nothing changes further out.
        val reach = ceil(HALO_WIDTH.value * density).toInt() + 1
        val stray = outside.filter { (x, y) -> !dark[x, y].near(light[x, y], EXACT) && !artwork.anyWithin(x, y, reach) }
        assertTrue("the halo reaches beyond 1dp at ${stray.take(5)}", stray.isEmpty())

        // The icon, left of the wordmark and the gap, has no halo.
        val wordmarkLeft = ((wordmarkBounds().left - composeRule.onNodeWithTag(FRAME).getUnclippedBoundsInRoot().left).value * density).toInt() - reach
        val aroundWordmark = outside.filter { (x, _) -> x >= wordmarkLeft }
        val halo = aroundWordmark.count { (x, y) -> dark[x, y].near(HALO, EXACT) }
        assertTrue("only $halo pixels in the wordmark-halo colour", halo > MIN_HALO_PIXELS)

        // The pixels just outside the artwork, on every side of every letter, take the halo.
        val edge = aroundWordmark.filter { (x, y) -> artwork.anyWithin(x, y, 1) }
        val traced = edge.count { (x, y) -> dark[x, y].near(HALO, BLENDED) }
        assertTrue("the halo covers $traced of ${edge.size} pixels at the wordmark's edge", traced >= edge.size * MIN_TRACED_SHARE)
    }

    @Test
    fun aRowWideEnoughKeepsTheWordmarkAtItsWidth() {
        show(width = 400.dp)

        assertEquals(BrandSize.LARGE.wordmarkWidth.value, wordmarkBounds().let { it.right - it.left }.value, PIXEL_DP)
    }

    @Test
    fun aNarrowRowShrinksTheWordmarkBesideTheIconWithoutCuttingIt() {
        show(width = NARROW)

        val frame = composeRule.onNodeWithTag(FRAME).getUnclippedBoundsInRoot()
        val wordmark = wordmarkBounds()
        val width = wordmark.right - wordmark.left
        val height = wordmark.bottom - wordmark.top
        assertTrue("the wordmark is $width wide in a $NARROW row", width < BrandSize.LARGE.wordmarkWidth && width > 0.dp)
        assertTrue("the wordmark ends at ${wordmark.right}, past the row's ${frame.right}", wordmark.right <= frame.right + PIXEL_DP.dp)
        assertEquals("the wordmark keeps its shape", WORDMARK_ASPECT_RATIO, width / height, 0.02f)
        val icon = BrandSize.LARGE.iconSize + BrandSize.LARGE.gap
        assertEquals("the icon keeps its size", icon.value, (wordmark.left - frame.left).value, PIXEL_DP)
    }

    private fun show(width: Dp) {
        composeRule.setContent {
            TokklTheme(darkTheme = false) {
                Box(Modifier.width(width).testTag(FRAME)) { Brand(BrandSize.LARGE) }
            }
        }
        composeRule.waitForIdle()
    }

    /** The wordmark's own node, not the brand's merged one. */
    private fun wordmarkBounds() =
        composeRule.onNode(hasContentDescription(string(R.string.app_name)), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun capture(): PixelMap {
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
    }

    private fun Color.near(other: Color, tolerance: Int): Boolean =
        abs(red8 - other.red8) <= tolerance && abs(green8 - other.green8) <= tolerance && abs(blue8 - other.blue8) <= tolerance

    private val Color.red8 get() = (red * 255).roundToInt()
    private val Color.green8 get() = (green * 255).roundToInt()
    private val Color.blue8 get() = (blue * 255).roundToInt()

    private class Mask(private val width: Int, private val height: Int, set: (Int, Int) -> Boolean) {
        private val bits = BooleanArray(width * height) { set(it % width, it / width) }

        operator fun get(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && bits[y * width + x]

        fun anyWithin(x: Int, y: Int, distance: Int): Boolean =
            (-distance..distance).any { dx -> (-distance..distance).any { dy -> get(x + dx, y + dy) } }
    }

    private enum class Look { DARK, LIGHT, ON_MARKER }

    private companion object {
        const val FRAME = "frame"

        /** Room around the brand for the halo, which draws outside the wordmark's bounds. */
        val FRAME_PADDING = 8.dp

        val HALO_WIDTH = 1.dp
        val NARROW = 240.dp
        val DARK_BACKGROUND = TokklDarkColorScheme.background
        val HALO = TokklDarkExtraColors.wordmarkHalo

        /** A colour the artwork does not use, so any other pixel on it is the artwork's. */
        val MARKER = Color(0xFFFF00FF)

        /** Rounding in the capture. */
        const val EXACT = 2

        /** The halo's own edge is blended with the background. */
        const val BLENDED = 48

        const val MIN_HALO_PIXELS = 1000
        const val MIN_TRACED_SHARE = 0.9

        /** Width over height of the wordmark images, 1645x709 px. */
        const val WORDMARK_ASPECT_RATIO = 1645f / 709f

        /** About a pixel, in dp. */
        const val PIXEL_DP = 0.5f
    }
}

/** TalkBack finds the brand once, as the app's name: one node, which the decorative icon adds nothing to. */
fun ComposeTestRule.assertBrandReadsOnce() {
    val name = string(R.string.app_name)
    val nodes = onAllNodes(hasContentDescription(name) or hasText(name)).fetchSemanticsNodes()
    assertEquals("nodes that read \"$name\"", 1, nodes.size)
    assertEquals(listOf(name), nodes.single().config.getOrNull(SemanticsProperties.ContentDescription))
}

private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
