package com.github.e0ipso.tokkl.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.awaitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * The Anybody headlines and the text around them at Android's largest font scale, 200%: no
 * text is cut off, leaves the screen or overlaps another. The sign-in form scrolls, so each of
 * its parts has to fit once scrolled into view.
 *
 * The clock and totals styles have no screen yet, so this covers the headline styles only.
 */
class LargestFontScaleTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The session and the font scale are set before the activity starts, so it never has to
     * restart. Tests start signed in unless marked [SignedOut].
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(LARGEST_FONT_SCALE))
        .around(composeRule)

    @Test
    fun launchScreenFits() {
        assertEquals(LARGEST_FONT_SCALE, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.launch_message))

        assertFitWithoutOverlap(
            text(R.string.app_name),
            text(R.string.launch_message),
            text(R.string.licences),
        )
    }

    @Test
    fun licencesHeadingsFit() {
        assertEquals(LARGEST_FONT_SCALE, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.licences))
        composeRule.onNodeWithText(string(R.string.licences)).performClick()

        assertFitWithoutOverlap(
            "Back button" to composeRule.onNodeWithContentDescription(string(R.string.back)),
            text(R.string.licences),
            text(R.string.licence_anybody_title),
            text(R.string.licence_anybody_licence),
        )
    }

    @SignedOut
    @Test
    fun signInWithApiTokenFits() {
        assertEquals(LARGEST_FONT_SCALE, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))

        assertFitWhenScrolledTo(
            blocks = signInBlocks("API token field" to field(R.string.sign_in_api_token)),
            texts = signInTexts() + listOf(
                fieldLabel(R.string.sign_in_api_token),
                text(R.string.sign_in_api_token_hint),
            ),
        )
    }

    @SignedOut
    @Test
    fun signInWithEmailAndPasswordFits() {
        assertEquals(LARGEST_FONT_SCALE, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))
        method(R.string.sign_in_method_email_password).performClick()

        assertFitWhenScrolledTo(
            blocks = signInBlocks(
                "email field" to field(R.string.sign_in_email),
                "password field" to field(R.string.sign_in_password),
            ),
            texts = signInTexts() + listOf(
                fieldLabel(R.string.sign_in_email),
                fieldLabel(R.string.sign_in_password),
                text(R.string.sign_in_password_hint),
            ),
        )
    }

    /** The parts of the sign-in form that stack, which must not overlap. */
    private fun signInBlocks(
        vararg credentialFields: Pair<String, SemanticsNodeInteraction>,
    ): List<Pair<String, SemanticsNodeInteraction>> =
        listOf(
            "heading" to composeRule.onNode(hasText(string(R.string.sign_in_title)) and isHeading()),
            "server URL field" to field(R.string.sign_in_server_url),
            text(R.string.sign_in_method),
            "API token choice" to method(R.string.sign_in_method_api_token),
            "email and password choice" to method(R.string.sign_in_method_email_password),
        ) + credentialFields + listOf(
            "sign-in button" to composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()),
        )

    /** Text inside the blocks, checked for clipping only. */
    private fun signInTexts(): List<Pair<String, SemanticsNodeInteraction>> =
        listOf(
            fieldLabel(R.string.sign_in_server_url),
            text(R.string.sign_in_server_url_hint),
            inside(R.string.sign_in_method_api_token, isSelectable()),
            inside(R.string.sign_in_method_email_password, isSelectable()),
            inside(R.string.sign_in_submit, hasClickAction()),
        )

    private fun field(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(label)))

    private fun method(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun fieldLabel(@StringRes id: Int): Pair<String, SemanticsNodeInteraction> =
        inside(id, hasSetTextAction())

    /** The text node itself, inside a control that merges its children. */
    private fun inside(@StringRes id: Int, control: SemanticsMatcher): Pair<String, SemanticsNodeInteraction> =
        string(id).let { "$it text" to composeRule.onNode(hasText(it) and hasAnyAncestor(control), useUnmergedTree = true) }

    /**
     * Scrolls to each node in turn, which then has to show in full: inside the screen and, for
     * text, not cut off. Then no two [blocks] overlap.
     */
    private fun assertFitWhenScrolledTo(
        blocks: List<Pair<String, SemanticsNodeInteraction>>,
        texts: List<Pair<String, SemanticsNodeInteraction>>,
    ) {
        val screen = composeRule.onRoot().getBoundsInRoot()
        for ((name, node) in blocks + texts) {
            node.performScrollTo()
            node.assertIsDisplayed()
            node.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = node.getUnclippedBoundsInRoot()
            val inside = box.left >= screen.left && box.top >= screen.top &&
                box.right <= screen.right && box.bottom <= screen.bottom
            assertTrue("does not fit on the screen: $name", inside)
        }
        // Unclipped bounds share one scroll position, so they compare even off screen.
        val bounds = blocks.map { (name, node) -> name to node.getUnclippedBoundsInRoot() }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse("${first.first} overlaps ${second.first}", first.second.overlaps(second.second))
            }
        }
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
