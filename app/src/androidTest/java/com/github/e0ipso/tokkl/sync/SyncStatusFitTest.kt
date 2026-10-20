package com.github.e0ipso.tokkl.sync

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SignedInFrame
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * The status element above the bottom bar at Android's largest font scale, 200%, with its longest
 * lines and its "needs attention" button: no text is cut off, leaves the window or overlaps
 * another part or the bottom bar, TalkBack reads the count and the time as one, and the button
 * keeps a 48dp touch target and opens the list it stands for. SyncStatusServerTest checks what the
 * element says against the server.
 */
class SyncStatusFitTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST)).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theStatusFitsAboveTheBarAndOpensTheNeedsAttentionList() {
        var opened = 0
        composeRule.setContent {
            TokklTheme {
                SignedInFrame(
                    current = TopLevelPage.TIMER,
                    onOpen = {},
                    syncStatus = STATUS,
                    onOpenNeedsAttention = { opened++ },
                    syncStatusClock = NOW,
                ) {}
            }
        }
        val count = context.resources.getQuantityString(R.plurals.sync_unsent, STATUS.unsent, STATUS.unsent)
        // Four days before NOW, the line names the day.
        val answered = context.getString(R.string.sync_last_answered_day, "Mon, 12 Oct 2026", "09:15")
        val attention = context.resources.getQuantityString(R.plurals.sync_needs_attention, STATUS.needsAttention, STATUS.needsAttention)

        composeRule.onNode(hasText(count) and hasText(answered)).assertIsDisplayed()
        val parts = listOf(
            "count" to text(count),
            "last answer" to text(answered),
            "needs attention label" to text(attention),
        )
        val window = composeRule.onNode(isRoot()).getUnclippedBoundsInRoot()
        for ((name, node) in parts) {
            node.assertIsDisplayed()
            assertFalse("text is cut off: $name", node.textLayout().didOverflowHeight)
            val box = node.getUnclippedBoundsInRoot()
            assertTrue("$name at $box leaves $window", box.inside(window))
        }
        val bar = BAR_ITEMS.map { label -> "${context.getString(label)} item" to composeRule.onNode(bottomBarItem(label)) }
        assertNoOverlap(parts + bar)

        val button = composeRule.onNode(hasText(attention) and hasClickAction())
        val touch = button.fetchSemanticsNode().touchBoundsInRoot
        val (width, height) = with(composeRule.density) { touch.width.toDp() to touch.height.toDp() }
        assertTrue("the button takes taps on $width by $height", width >= MIN_TOUCH_TARGET - SLACK && height >= MIN_TOUCH_TARGET - SLACK)
        button.performClick()
        assertEquals("the button opens the list once", 1, opened)
    }

    private fun assertNoOverlap(parts: List<Pair<String, SemanticsNodeInteraction>>) {
        val bounds = parts.map { (name, node) -> name to node.getUnclippedBoundsInRoot() }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse("${first.first} ${first.second} overlaps ${second.first} ${second.second}", first.second.overlaps(second.second))
            }
        }
    }

    /** The text node itself, not the row TalkBack reads, so its layout result is reachable. */
    private fun text(value: String): SemanticsNodeInteraction = composeRule.onNode(hasText(value), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun DpRect.inside(other: DpRect): Boolean =
        left >= other.left - SLACK && top >= other.top - SLACK && right <= other.right + SLACK && bottom <= other.bottom + SLACK

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK = 0.5.dp
        val MIN_TOUCH_TARGET = 48.dp

        val BAR_ITEMS = listOf(R.string.nav_timer, R.string.reports, R.string.nav_projects, R.string.settings, R.string.nav_more)

        val NOW: Clock = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), ZoneOffset.UTC)

        /** The longest status: a two-digit count, a day before yesterday, and changes the server refused. */
        val STATUS = SyncStatus(
            unsent = 12,
            lastAnswered = Instant.parse("2026-10-12T09:15:00Z"),
            needsAttention = 3,
            settings = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null),
        )
    }
}
