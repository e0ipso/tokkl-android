package com.github.e0ipso.tokkl.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * The Anybody headlines and the text around them at Android's largest font scale, 200%: no
 * text is cut off, leaves the screen or overlaps another.
 *
 * The clock and totals styles have no screen yet, so this covers the headline styles only.
 */
class LargestFontScaleTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /** The font scale is set before the activity starts, so it never has to restart. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(LARGEST_FONT_SCALE)).around(composeRule)

    @Test
    fun launchScreenFits() {
        assertEquals(LARGEST_FONT_SCALE, composeRule.activity.resources.configuration.fontScale)

        assertFitWithoutOverlap(
            text(R.string.app_name),
            text(R.string.launch_message),
            text(R.string.licences),
        )
    }

    @Test
    fun licencesHeadingsFit() {
        assertEquals(LARGEST_FONT_SCALE, composeRule.activity.resources.configuration.fontScale)
        composeRule.onNodeWithText(string(R.string.licences)).performClick()

        assertFitWithoutOverlap(
            "Back button" to composeRule.onNodeWithContentDescription(string(R.string.back)),
            text(R.string.licences),
            text(R.string.licence_anybody_title),
            text(R.string.licence_anybody_licence),
        )
    }

    private fun assertFitWithoutOverlap(vararg nodes: Pair<String, SemanticsNodeInteraction>) {
        val screen = composeRule.onRoot().getBoundsInRoot()
        val bounds = nodes.map { (name, node) ->
            node.assertIsDisplayed()
            node.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = node.getBoundsInRoot()
            val inside = box.left >= screen.left && box.top >= screen.top &&
                box.right <= screen.right && box.bottom <= screen.bottom
            assertTrue("leaves the screen: $name", inside)
            name to box
        }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse("${first.first} overlaps ${second.first}", first.second.overlaps(second.second))
            }
        }
    }

    /** The text node itself, not the button it may sit in, so its layout result is reachable. */
    private fun text(@StringRes id: Int): Pair<String, SemanticsNodeInteraction> =
        string(id).let { it to composeRule.onNodeWithText(it, useUnmergedTree = true) }

    /**
     * The layout result that semantics hands out is laid out again at the full available width,
     * so its didOverflowWidth is true for any text narrower than its container. Only the height
     * is compared: soft wrapping keeps every line inside the width.
     */
    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult? {
        if (SemanticsActions.GetTextLayoutResult !in fetchSemanticsNode().config) return null
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)

    /**
     * Sets the device's font scale, waits until the app's configuration has it, and puts the
     * previous setting back afterwards, again waiting for the app to see it so the next test
     * starts at the usual scale.
     */
    private class SystemFontScale(private val scale: Float) : ExternalResource() {
        private var previous: String = NOT_SET

        override fun before() {
            previous = DeviceShell.run("settings get system font_scale").trim()
            DeviceShell.run("settings put system font_scale $scale")
            awaitAppFontScale(scale)
        }

        override fun after() {
            if (previous == NOT_SET) {
                DeviceShell.run("settings delete system font_scale")
            } else {
                DeviceShell.run("settings put system font_scale $previous")
            }
            awaitAppFontScale(previous.toFloatOrNull() ?: 1f)
        }

        private fun awaitAppFontScale(expected: Float) {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            val deadline = System.nanoTime() + CONFIGURATION_TIMEOUT_NS
            while (resources.configuration.fontScale != expected) {
                check(System.nanoTime() < deadline) {
                    "the app's font scale is ${resources.configuration.fontScale}, not $expected"
                }
                Thread.sleep(POLL_MS)
            }
        }

        private companion object {
            /** What `settings get` prints for a setting that was never written. */
            const val NOT_SET = "null"
            const val CONFIGURATION_TIMEOUT_NS = 10_000_000_000L
            const val POLL_MS = 50L
        }
    }

    private companion object {
        /** The largest step of Android's font size setting since Android 14. */
        const val LARGEST_FONT_SCALE = 2f
    }
}
