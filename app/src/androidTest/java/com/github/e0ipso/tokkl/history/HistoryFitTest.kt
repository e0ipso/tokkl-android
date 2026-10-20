package com.github.e0ipso.tokkl.history

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The history's entry actions and grouped rows at Android's largest font scale, 200%, at the
 * Timer page's width: the row menu with Duplicate and Delete, the message with Undo after a
 * delete and the longer one when Undo needs the server, and a group of similar entries, collapsed
 * and expanded. No text is cut off or leaves the screen, no two parts overlap, and every button
 * keeps a 48dp touch target. EntryActionsUiTest and HistoryGroupUiTest check what TalkBack reads.
 */
class HistoryFitTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST)).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The message over the list, which a test changes after the first check. */
    private var notice by mutableStateOf<EntryNotice?>(null)

    @Test
    fun theRowMenuFits() {
        show(SOLO)
        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, SOLO_DESCRIPTION)) and hasClickAction())
            .performScrollTo()
            .performClick()
        val items = listOf(R.string.history_duplicate, R.string.history_delete).map { label ->
            "${string(label)} item" to composeRule.onNode(hasText(string(label)) and hasClickAction())
        }
        val labels = listOf(R.string.history_duplicate, R.string.history_delete).map { label -> inside(label, hasClickAction()) }
        val menu = composeRule.onNode(isRoot() and hasAnyDescendant(hasText(string(R.string.history_duplicate))))
        val page = composeRule.onNode(isRoot() and hasAnyDescendant(hasContentDescription(string(R.string.history_more_actions, SOLO_DESCRIPTION))))

        val window = menu.screenRect()
        assertTrue("the menu's window $window leaves the screen ${page.screenRect()}", window.inside(page.screenRect()))
        assertFit(items + labels, area = menu.getBoundsInRoot())
        assertNoOverlap(items)
        assertWithin(labels, items)
        assertTouchTargets(items)
    }

    @Test
    fun theUndoMessagesFit() {
        show(SOLO)

        notice = EntryNotice.Deleted(serial = 1, entryId = SOLO_ID, undo = true)
        assertMessageFits(R.string.history_deleted)

        notice = EntryNotice.UndoNeedsServer(serial = 2, entryId = SOLO_ID)
        assertMessageFits(R.string.history_undo_needs_server)
    }

    @Test
    fun aGroupFitsCollapsedAndExpanded() {
        show(GROUP)
        val group = composeRule.onNode(hasContentDescription(entries(3)) and hasClickAction())

        val collapsed = groupLine()
        assertFit(collapsed, area = history())
        assertNoOverlap(collapsed)

        group.performScrollTo().performClick()
        composeRule.onAllNodes(memberMenu()).assertCountEquals(3)

        val expanded = collapsed + (0 until 3).flatMap { member(it) }
        assertFit(expanded, area = history())
        assertNoOverlap(expanded)
        assertTouchTargets((0 until 3).map { "member $it menu button" to composeRule.onAllNodes(memberMenu())[it] })
    }

    /** [entries] under today's heading, at the width the Timer page gives its history, with [notice] over them. */
    private fun show(entries: List<HistoryEntry>) {
        val state = HistoryState.Shown(
            days = listOf(HistoryDay(DAY, entries, rowsForDay(entries, collapse = true))),
            today = DAY,
            zone = ZoneOffset.UTC,
            settings = SETTINGS,
            older = OlderDays.End,
        )
        composeRule.setContent {
            TokklTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .screenContent(),
                ) {
                    HistoryContent(state = state, onLoadOlder = {})
                    EntryActionsHost(
                        state = EntryActionsState(notice = notice),
                        onUndo = {},
                        onNoticeDone = { done -> if (notice == done) notice = null },
                        onConfirmDelete = {},
                        onCancelDelete = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
    }

    /**
     * The message [text] and its buttons, Undo and the one that dismisses it: each shows in full
     * on the screen, the message is not cut off, Undo's label stays whole inside it, nothing
     * overlaps, and both buttons keep a touch target.
     */
    private fun assertMessageFits(@StringRes text: Int) {
        val snackbar = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
            hasAnyDescendant(hasText(string(text)))
        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(snackbar).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
        val buttons = composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(snackbar)).fetchSemanticsNodes()
        assertEquals("Undo and the button that dismisses the message", 2, buttons.size)
        val undo = "Undo button" to composeRule.onNode(hasText(string(R.string.history_undo)) and hasClickAction() and hasAnyAncestor(snackbar))
        val dismiss = "dismiss button" to composeRule.onNode(
            hasClickAction() and hasAnyAncestor(snackbar) and hasText(string(R.string.history_undo)).not(),
        )
        val message = "message" to composeRule.onNode(hasText(string(text)) and hasAnyAncestor(snackbar), useUnmergedTree = true)
        val undoLabel = "Undo label" to composeRule.onNode(
            hasText(string(R.string.history_undo)) and hasAnyAncestor(hasClickAction() and hasAnyAncestor(snackbar)),
            useUnmergedTree = true,
        )

        assertFit(listOf(message, undo, dismiss, undoLabel), area = history())
        assertNoOverlap(listOf(message, undo, dismiss))
        assertWithin(listOf(undoLabel), listOf(undo))
        assertTouchTargets(listOf(undo, dismiss))
    }

    /** The group's own line: its count, description, total, project, time range, tags and billable mark. */
    private fun groupLine(): List<Pair<String, SemanticsNodeInteraction>> =
        listOf(
            "count badge" to text("3"),
            "description" to composeRule.onAllNodes(hasText(GROUP_DESCRIPTION), useUnmergedTree = true)[0],
            // The day's heading shows the same total.
            "total" to composeRule.onNode(hasText(SETTINGS.formatDuration(3 * HOUR)) and hasAnyAncestor(isHeading()).not(), useUnmergedTree = true),
            "project" to composeRule.onAllNodes(hasText(PROJECT), useUnmergedTree = true)[0],
            "time range" to text(string(R.string.history_time_range, "09:00", "12:00")),
            "tags" to composeRule.onAllNodes(hasText(TAG), useUnmergedTree = true)[0],
            "billable mark" to composeRule.onAllNodes(hasText(string(R.string.history_billable)), useUnmergedTree = true)[0],
        )

    /** The parts of the group's member [index], newest first, each found after the group's own line. */
    private fun member(index: Int): List<Pair<String, SemanticsNodeInteraction>> {
        val (start, stop) = MEMBER_TIMES[index]
        return listOf(
            "member $index description" to composeRule.onAllNodes(hasText(GROUP_DESCRIPTION), useUnmergedTree = true)[index + 1],
            "member $index duration" to composeRule.onAllNodes(hasText(SETTINGS.formatDuration(HOUR)), useUnmergedTree = true)[index],
            "member $index project" to composeRule.onAllNodes(hasText(PROJECT), useUnmergedTree = true)[index + 1],
            "member $index time range" to text(string(R.string.history_time_range, start, stop)),
            "member $index tags" to composeRule.onAllNodes(hasText(TAG), useUnmergedTree = true)[index + 1],
            "member $index billable mark" to composeRule.onAllNodes(hasText(string(R.string.history_billable)), useUnmergedTree = true)[index + 1],
            "member $index menu button" to composeRule.onAllNodes(memberMenu())[index],
        )
    }

    private fun memberMenu(): SemanticsMatcher =
        hasContentDescription(string(R.string.history_more_actions, GROUP_DESCRIPTION)) and hasClickAction()

    /**
     * Scrolls each of [parts] into view, where it has to show in full inside [area], in its own
     * window's coordinates, and, for text, not be cut off.
     */
    private fun assertFit(parts: List<Pair<String, SemanticsNodeInteraction>>, area: DpRect) {
        for ((name, node) in parts) {
            if (scrolls(node)) node.performScrollTo()
            node.assertIsDisplayed()
            node.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = node.getUnclippedBoundsInRoot()
            assertTrue("$name at $box leaves $area", box.inside(area))
        }
    }

    /** No two of [parts] overlap. Unclipped bounds share one scroll position, so they compare even off screen. */
    private fun assertNoOverlap(parts: List<Pair<String, SemanticsNodeInteraction>>) {
        val bounds = parts.map { (name, node) -> name to node.getUnclippedBoundsInRoot() }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse(
                    "${first.first} ${first.second} overlaps ${second.first} ${second.second}",
                    first.second.overlaps(second.second),
                )
            }
        }
    }

    /** Each label in [labels] stays inside the control at the same place in [controls]. */
    private fun assertWithin(labels: List<Pair<String, SemanticsNodeInteraction>>, controls: List<Pair<String, SemanticsNodeInteraction>>) {
        for ((label, control) in labels.zip(controls)) {
            val box = control.second.getUnclippedBoundsInRoot()
            assertTrue("${label.first} leaves the ${control.first}", label.second.getUnclippedBoundsInRoot().inside(box))
        }
    }

    /**
     * Each button takes taps on at least 48dp by 48dp, the smallest touch target Android's
     * accessibility guidance allows. Compose widens a smaller button's touch area to that size, so
     * this reads the touch bounds, not the drawn ones: an icon button draws 40dp.
     */
    private fun assertTouchTargets(buttons: List<Pair<String, SemanticsNodeInteraction>>) {
        for ((name, node) in buttons) {
            val touch = node.fetchSemanticsNode().touchBoundsInRoot
            val (width, height) = with(composeRule.density) { touch.width.toDp() to touch.height.toDp() }
            assertTrue("$name takes taps on $width by $height", width >= MIN_TOUCH_TARGET - SLACK && height >= MIN_TOUCH_TARGET - SLACK)
        }
    }

    /** The history's area, the width the Timer page gives it, which holds the list and the message. */
    private fun history(): DpRect = composeRule.onNode(hasScrollAction()).getBoundsInRoot()

    private fun scrolls(node: SemanticsNodeInteraction): Boolean = hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())

    /** The root's place on the screen, in pixels, to compare windows with each other. */
    private fun SemanticsNodeInteraction.screenRect(): Rect = fetchSemanticsNode().let { Rect(it.positionOnScreen, it.size.toSize()) }

    private fun Rect.inside(other: Rect): Boolean =
        left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom

    private fun DpRect.inside(other: DpRect): Boolean =
        left >= other.left - SLACK && top >= other.top - SLACK && right <= other.right + SLACK && bottom <= other.bottom + SLACK

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /**
     * The layout result that semantics hands out is laid out again at the full available width,
     * so only its height is compared: soft wrapping keeps every line inside the width.
     */
    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult? {
        if (SemanticsActions.GetTextLayoutResult !in fetchSemanticsNode().config) return null
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    /** The text node itself, not the row it sits in, so its layout result is reachable. */
    private fun text(value: String): SemanticsNodeInteraction = composeRule.onNode(hasText(value), useUnmergedTree = true)

    /** The text node of [id] inside a control that merges its children. */
    private fun inside(@StringRes id: Int, control: SemanticsMatcher): Pair<String, SemanticsNodeInteraction> =
        string(id).let { "$it label" to composeRule.onNode(hasText(it) and hasAnyAncestor(control), useUnmergedTree = true) }

    private fun entries(count: Int): String = context.resources.getQuantityString(R.plurals.history_group_entries, count, count)

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val HOUR = 3600L

        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK = 0.5.dp
        val MIN_TOUCH_TARGET = 48.dp

        val DAY: LocalDate = LocalDate.of(2026, 10, 15)
        val SETTINGS = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        const val PROJECT = "Tokkl for Android"
        const val TAG = "code review"
        const val SOLO_ID = 9L
        const val SOLO_DESCRIPTION = "Write the release notes for the next version"
        const val GROUP_DESCRIPTION = "Review the pull requests"

        /** An entry with every mark a row shows: a project and task, tags, billable and not sent. */
        val SOLO = listOf(
            HistoryEntry(
                id = SOLO_ID,
                description = SOLO_DESCRIPTION,
                project = PROJECT,
                projectColor = "#0b83d9",
                task = "Release",
                tags = listOf("writing", "documentation"),
                billable = true,
                start = Instant.parse("2026-10-15T13:00:00Z"),
                stop = Instant.parse("2026-10-15T14:30:00Z"),
                unsent = true,
                projectId = 7,
                taskId = 70,
            ),
        )

        /** Each member's start and stop, newest first. */
        val MEMBER_TIMES = listOf("11:00" to "12:00", "10:00" to "11:00", "09:00" to "10:00")

        /** Three similar entries, newest first, which the history shows as one group. */
        val GROUP = MEMBER_TIMES.mapIndexed { index, (start, stop) ->
            HistoryEntry(
                id = 3L - index,
                description = GROUP_DESCRIPTION,
                project = PROJECT,
                projectColor = "#0b83d9",
                task = null,
                tags = listOf(TAG),
                billable = true,
                start = Instant.parse("2026-10-15T$start:00Z"),
                stop = Instant.parse("2026-10-15T$stop:00Z"),
                unsent = false,
                projectId = 7,
            )
        }
    }
}
