package com.github.e0ipso.tokkl.history

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * A group of similar entries in the history (docs/PRD.md H4): one line with the count, the total
 * and the time from the oldest start to the newest stop, which a tap expands to its members. To
 * TalkBack the line is a button whose state, Expanded or Collapsed, changes with each tap, which
 * TalkBack announces. At the largest font scale no text of the line, or of an entry's menu, is
 * cut off.
 */
class HistoryGroupUiTest {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST, largestDisplaySize = true)).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val edits = CopyOnWriteArrayList<Long>()

    @Test
    fun aGroupShowsItsCountAndTotalAndExpandsToItsMembersWithTheStateForTalkBack() {
        show()
        val group = composeRule.onNode(
            hasText("Group work") and hasContentDescription(entries(3)) and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )

        assertEquals(context.getString(R.string.history_group_collapsed), group.stateDescription())
        assertEquals(context.getString(R.string.history_group_show), group.clickLabel())
        composeRule.onNode(hasText("3")).assertExists()
        composeRule.onNode(hasText(SETTINGS.formatDuration(3 * 3600))).assertExists()
        composeRule.onNode(hasText(context.getString(R.string.history_time_range, "09:00", "12:00"))).assertExists()
        composeRule.onAllNodes(hasContentDescription(context.getString(R.string.history_more_actions, "Group work"))).assertCountEquals(0)

        group.performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(context.getString(R.string.history_group_expanded), group.stateDescription())
        assertEquals(context.getString(R.string.history_group_hide), group.clickLabel())
        val members = composeRule.onAllNodes(hasText("Group work") and hasClickAction() and hasContentDescription(entries(3)).not())
        members.assertCountEquals(3)
        composeRule.onAllNodes(hasContentDescription(context.getString(R.string.history_more_actions, "Group work"))).assertCountEquals(3)
        composeRule.onNode(hasText(context.getString(R.string.history_time_range, "10:00", "11:00")) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("a member opens for editing", listOf(2L), edits)

        group.performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(context.getString(R.string.history_group_collapsed), group.stateDescription())
        members.assertCountEquals(0)
    }

    @Test
    fun noTextOfAGroupIsCutOffAtTheLargestFontScale() {
        show()

        for (text in listOf("Group work", "3", SETTINGS.formatDuration(3 * 3600), context.getString(R.string.history_time_range, "09:00", "12:00"))) {
            val node = composeRule.onNode(hasText(text), useUnmergedTree = true)
            node.textLayout()?.let { assertFalse("text is cut off: $text", it.didOverflowHeight) }
        }
    }

    /** The entry menu, Save as template included, keeps every item whole and tappable at the largest font scale. */
    @Test
    fun noItemOfTheEntryMenuIsCutOffAtTheLargestFontScale() {
        show(pin = PinAction(enabled = true) {})
        composeRule.onNode(hasContentDescription(context.getString(R.string.history_more_actions, "Solo work")) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)

        for (item in listOf(R.string.history_duplicate, R.string.history_split, R.string.history_pin, R.string.history_delete)) {
            val text = context.getString(item)
            composeRule.onNode(hasText(text) and hasClickAction()).assertExists()
            val node = composeRule.onNode(hasText(text), useUnmergedTree = true)
            node.textLayout()?.let { layout ->
                assertFalse("text is cut off: $text", layout.didOverflowHeight)
                // didOverflowWidth is true for one-line items that fit in this menu, so this
                // checks that every line ends inside the text's own width instead.
                for (line in 0 until layout.lineCount) {
                    assertFalse("text runs past its edge: $text", layout.getLineRight(line) > layout.size.width)
                }
            }
        }
    }

    private fun show(pin: PinAction? = null) {
        val entries = listOf(
            entry(4, "Solo work", 12, 13),
            entry(3, "Group work", 11, 12),
            entry(2, "Group work", 10, 11),
            entry(1, "Group work", 9, 10),
        )
        val state = HistoryState.Shown(
            days = listOf(HistoryDay(DAY, entries, rowsForDay(entries, collapse = true))),
            today = DAY,
            zone = ZoneOffset.UTC,
            settings = SETTINGS,
            older = OlderDays.End,
        )
        composeRule.setContent {
            TokklTheme {
                HistoryContent(state = state, onLoadOlder = {}, onEdit = { edits += it }, pin = pin)
            }
        }
    }

    private fun entries(count: Int): String = context.resources.getQuantityString(R.plurals.history_group_entries, count, count)

    private fun SemanticsNodeInteraction.stateDescription(): String? =
        fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    private fun SemanticsNodeInteraction.clickLabel(): String? =
        fetchSemanticsNode().config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult? {
        if (SemanticsActions.GetTextLayoutResult !in fetchSemanticsNode().config) return null
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 15)
        val SETTINGS = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        fun entry(id: Long, description: String, startHour: Int, stopHour: Int) = HistoryEntry(
            id = id,
            description = description,
            project = null,
            projectColor = null,
            task = null,
            tags = emptyList(),
            billable = false,
            start = Instant.parse("2026-10-15T%02d:00:00Z".format(startHour)),
            stop = Instant.parse("2026-10-15T%02d:00:00Z".format(stopHour)),
            unsent = false,
        )
    }
}
