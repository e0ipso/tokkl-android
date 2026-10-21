package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.SectionHue
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The active tab's indicator takes its page's hue, in the bar and the rail, light and dark, as
 * the web app's active nav item since tokkl d012228 (TT-0299): Timer orange, Reports sky, Projects
 * violet, Settings yellow, and More pink while a page on the More sheet shows. With the timer idle, the other tabs
 * draw no hue at all. The running-dot checks cover its separate pink fill. TopLevelPageHueTest checks which hue each page has.
 */
class TabHueTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun inLightEachBarTabTakesItsPagesHue() = assertEveryTab(dark = false, rail = false)

    @Test
    fun inDarkEachBarTabTakesItsPagesHue() = assertEveryTab(dark = true, rail = false)

    @Test
    fun inLightEachRailTabTakesItsPagesHue() = assertEveryTab(dark = false, rail = true)

    @Test
    fun inDarkEachRailTabTakesItsPagesHue() = assertEveryTab(dark = true, rail = true)

    @Test
    fun inLightTheActiveMorePageTakesMoresHue() = assertMoreSheet(dark = false)

    @Test
    fun inDarkTheActiveMorePageTakesMoresHue() = assertMoreSheet(dark = true)

    @Test
    fun inLightTheBarKeepsTheRunningDotVisible() = assertRunningDot(dark = false, rail = false)

    @Test
    fun inDarkTheBarKeepsTheRunningDotVisible() = assertRunningDot(dark = true, rail = false)

    @Test
    fun inLightTheRailKeepsTheRunningDotVisible() = assertRunningDot(dark = false, rail = true)

    @Test
    fun inDarkTheRailKeepsTheRunningDotVisible() = assertRunningDot(dark = true, rail = true)

    private fun assertRunningDot(dark: Boolean, rail: Boolean) {
        var current by mutableStateOf(TopLevelPage.TIMER)
        var running by mutableStateOf(false)
        var unselectedDot = Color.Unspecified
        composeRule.setContent {
            TokklTheme(darkTheme = dark) {
                unselectedDot = TokklTheme.extraColors.running
                if (rail) AppNavigationRail(current = current, onOpen = {}, timerRunning = running)
                else AppNavigationBar(current = current, onOpen = {}, timerRunning = running)
            }
        }
        val timer = tab(context.getString(R.string.nav_timer))
        val density = context.resources.displayMetrics.density
        // At least half the 6dp badge's area must visibly change to its expected colour.
        val minimumDotPixels = (kotlin.math.PI * 3 * 3 * density * density / 2).roundToInt()
        for (page in TopLevelPage.entries) {
            composeRule.runOnIdle { current = page; running = false }
            val idle = timer.captureToImage().toPixelMap()
            composeRule.runOnIdle { running = true }
            val active = timer.captureToImage().toPixelMap()
            val expected = if (page == TopLevelPage.TIMER) page.hue.onFill else unselectedDot
            var dotPixels = 0
            for (x in 0 until active.width) {
                for (y in 0 until active.height) {
                    if (active[x, y].near(expected) && !idle[x, y].near(expected)) dotPixels++
                }
            }
            assertTrue("the running dot remains visible while $page shows: $dotPixels pixels", dotPixels >= minimumDotPixels)
        }
    }

    private fun assertMoreSheet(dark: Boolean) {
        var current by mutableStateOf(TopLevelPage.CLIENTS)
        composeRule.setContent {
            TokklTheme(darkTheme = dark) { AppNavigationBar(current = current, onOpen = {}) }
        }
        tab(context.getString(R.string.nav_more)).performClick()
        for (page in TopLevelPage.entries.filter { it.inMore }) {
            composeRule.runOnIdle { current = page }
            val item = tab(context.getString(page.label))
            item.assertIsSelected()
            assertTrue("the selected $page sheet item fills with More's hue", huePixels(item, page.hue) >= minimumIndicatorPixels(rail = false))
        }
    }

    /** For each page, shown as the current one: its tab, or More's, fills with its hue, and no other tab does. */
    private fun assertEveryTab(dark: Boolean, rail: Boolean) {
        var current by mutableStateOf(TopLevelPage.TIMER)
        composeRule.setContent {
            TokklTheme(darkTheme = dark) {
                if (rail) AppNavigationRail(current = current, onOpen = {}, timerRunning = false)
                else AppNavigationBar(current = current, onOpen = {}, timerRunning = false)
            }
        }
        val tabs = TopLevelPage.entries.filterNot { it.inMore }.map { context.getString(it.label) } + context.getString(R.string.nav_more)
        for (page in TopLevelPage.entries) {
            current = page
            composeRule.waitForIdle()
            val marked = if (page.inMore) context.getString(R.string.nav_more) else context.getString(page.label)
            for (label in tabs) {
                val pixels = huePixels(tab(label), page.hue)
                if (label == marked) {
                    assertTrue("the $label tab shows $pixels pixels of its hue while $page shows", pixels >= minimumIndicatorPixels(rail))
                } else {
                    assertEquals("the $label tab shows $page's hue while $page shows", 0, pixels)
                }
            }
        }
    }

    /** The bar or rail item named [label]. */
    private fun tab(label: String): SemanticsNodeInteraction = composeRule.onNode(isSelectable() and hasText(label))

    /** How many of the item's pixels are [hue]'s fill. */
    private fun huePixels(tab: SemanticsNodeInteraction, hue: SectionHue): Int {
        val image = tab.captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if (image[x, y].near(hue.fill)) count++
            }
        }
        return count
    }

    /**
     * Half the pixels of Material 3's indicator, 64 by 32dp on the bar and 56 by 32dp on the rail,
     * so the icon and the outline inside it leave plenty of hue.
     */
    private fun minimumIndicatorPixels(rail: Boolean): Int {
        val density = context.resources.displayMetrics.density
        val width = if (rail) RAIL_INDICATOR_WIDTH else BAR_INDICATOR_WIDTH
        return (width * density * INDICATOR_HEIGHT * density / 2).roundToInt()
    }

    private fun Color.near(other: Color): Boolean =
        listOf(red to other.red, green to other.green, blue to other.blue).all { (a, b) -> abs(a - b) * 255 <= TOLERANCE }

    private companion object {
        const val BAR_INDICATOR_WIDTH = 64f
        const val RAIL_INDICATOR_WIDTH = 56f
        const val INDICATOR_HEIGHT = 32f
        const val TOLERANCE = 2f
    }
}
