package com.github.e0ipso.tokkl.reports

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
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
 * The report filters at Android's largest font scale, 200%, with every kind of filter active, one
 * of them negated: the text on the reports screen that names each active filter, and the filter
 * sheet with its top row, its headings, "Is" and "Is not", every choice and Clear filters. No text is cut off or leaves its control or
 * the screen, no two parts of the sheet's top row overlap, and every control keeps a 48dp touch
 * target. ReportFiltersTest checks what the filters do against the server.
 */
class ReportFiltersFitTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST)).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theActiveFiltersFitOnTheReportsScreen() {
        show()
        val button = "Filters button" to filtersButton()
        // In the unmerged tree the button holds its texts as children.
        val inButton = hasAnyAncestor(hasClickAction() and hasAnyDescendant(hasText(string(R.string.reports_filters))))
        val lines = listOf(
            string(R.string.reports_filters),
            line(R.string.reports_filter_project, "$WEBSITE, ${string(R.string.reports_without_project)}"),
            string(R.string.reports_filter_line_not, string(R.string.reports_filter_client), ACME),
            line(R.string.reports_filter_tag, "$DEEP_WORK, $MEETINGS, ${string(R.string.reports_filter_without_tags)}"),
            string(R.string.reports_filter_non_billable_only),
        ).map { text -> text to composeRule.onNode(hasText(text) and inButton, useUnmergedTree = true) }

        assertFit(listOf(button) + lines, area = report())
        val box = button.second.getUnclippedBoundsInRoot()
        for ((name, node) in lines) assertTrue("$name leaves the Filters button", node.getUnclippedBoundsInRoot().inside(box))
        assertNoOverlap(lines)
        assertTouchTargets(listOf(button))
    }

    @Test
    fun theFilterSheetFits() {
        show()
        filtersButton().performScrollTo().performClick()
        composeRule.onNode(isDialog()).assertIsDisplayed()
        val dialog = composeRule.onNode(isDialog()).getUnclippedBoundsInRoot()

        val topRow = listOf(
            "close button" to composeRule.onNode(hasContentDescription(string(R.string.reports_cancel)) and hasClickAction() and inDialog()),
            "heading" to composeRule.onNode(hasText(string(R.string.reports_filters)) and isHeading() and inDialog()),
            "Apply button" to composeRule.onNode(hasText(string(R.string.reports_apply)) and hasClickAction() and inDialog()),
        )
        assertFit(topRow, area = dialog)
        assertNoOverlap(topRow + ("list" to list()))
        val apply = "Apply label" to composeRule.onNode(
            hasText(string(R.string.reports_apply)) and hasAnyAncestor(hasClickAction() and inDialog()),
            useUnmergedTree = true,
        )
        assertFit(listOf(apply), area = topRow[2].second.getUnclippedBoundsInRoot())
        assertTouchTargets(listOf(topRow[0], topRow[2]))

        val area = list().getBoundsInRoot()
        for (heading in HEADINGS) {
            assertFitInList("${string(heading)} heading", hasText(string(heading)) and isHeading() and inDialog(), area)
        }
        for (choice in CHECKBOXES.map { string(it) } + listOf(WEBSITE, INTERNAL, ACME, DEEP_WORK, MEETINGS)) {
            assertControlFits(choice, hasText(choice) and isToggleable() and inDialog(), area)
        }
        for (choice in RADIO_BUTTONS) {
            assertControlFits(string(choice), hasText(string(choice)) and isSelectable() and inDialog(), area)
        }
        // "Is" and "Is not" of each id filter, which differ only in what TalkBack reads with them.
        // Material's segments share their 1dp border, so only their labels must not overlap.
        for (filter in HEADINGS.dropLast(1)) {
            val segments = MODES.map { (label, description) ->
                val name = string(description, string(filter))
                val segment = assertFitInList(name, hasContentDescription(name) and isSelectable() and inDialog(), area)
                val text = composeRule.onNode(hasText(string(label)) and hasAnyAncestor(hasContentDescription(name)), useUnmergedTree = true)
                text.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
                assertTrue("the label leaves its segment: $name", text.getUnclippedBoundsInRoot().inside(segment.getUnclippedBoundsInRoot()))
                Triple(name, segment, text)
            }
            assertNoOverlap(segments.map { (name, _, text) -> "$name label" to text })
            assertTouchTargets(segments.map { (name, segment, _) -> name to segment })
        }
        assertControlFits(string(R.string.reports_filters_clear), hasText(string(R.string.reports_filters_clear)) and hasClickAction() and inDialog(), area)
    }

    /** The summary of an empty week, as with filters no entry matches, with [FILTERS] active. */
    private fun show() {
        composeRule.setContent {
            TokklTheme {
                ReportsContent(
                    state = ReportsState.Loaded(RangeSelection(WEEK, RangePreset.THIS_WEEK), SummaryReport.of(emptyList(), WEEK, DayOfWeek.MONDAY)),
                    tab = ReportTab.SUMMARY,
                    settings = ACCOUNT,
                    time = ACCOUNT.time(TODAY),
                    filters = FILTERS,
                    filterOptions = OPTIONS,
                    onTab = {},
                    onShift = {},
                    onPreset = {},
                    onCustom = {},
                    onFilters = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
    }

    /** Scrolls the sheet's list to the control [matcher] finds, which has to fit there, its label whole inside it. */
    private fun assertControlFits(name: String, matcher: SemanticsMatcher, area: DpRect) {
        val control = assertFitInList(name, matcher, area)
        // In the unmerged tree the row holds its label as a child, and each label shows once in the sheet.
        val label = composeRule.onNode(hasText(name) and inDialog(), useUnmergedTree = true)
        label.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
        assertTrue("the label leaves its control: $name", label.getUnclippedBoundsInRoot().inside(control.getUnclippedBoundsInRoot()))
        assertTouchTargets(listOf(name to control))
    }

    /** Scrolls the sheet's list to the node [matcher] finds, which then has to show in full inside the list's [area]. */
    private fun assertFitInList(name: String, matcher: SemanticsMatcher, area: DpRect): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction() and inDialog()).performScrollToNode(matcher)
        val node = composeRule.onNode(matcher)
        assertFit(listOf(name to node), area)
        return node
    }

    /** Each of [parts], scrolled into view, shows in full inside [area] and, for text, is not cut off. */
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

    /**
     * Each control takes taps on at least 48dp by 48dp, the smallest touch target Android's
     * accessibility guidance allows. Compose widens a smaller control's touch area to that size, so
     * this reads the touch bounds, not the drawn ones: an icon button draws 40dp.
     */
    private fun assertTouchTargets(controls: List<Pair<String, SemanticsNodeInteraction>>) {
        for ((name, node) in controls) {
            val touch = node.fetchSemanticsNode().touchBoundsInRoot
            val (width, height) = with(composeRule.density) { touch.width.toDp() to touch.height.toDp() }
            assertTrue("$name takes taps on $width by $height", width >= MIN_TOUCH_TARGET - SLACK && height >= MIN_TOUCH_TARGET - SLACK)
        }
    }

    /** The button under the range that names the active filters and opens the sheet. */
    private fun filtersButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.reports_filters)) and hasClickAction())

    /** The reports screen's scrolling column, under its top bar. */
    private fun report(): DpRect =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).getBoundsInRoot()

    /** The sheet's list of choices, under its top row. */
    private fun list(): SemanticsNodeInteraction = composeRule.onNode(hasScrollToNodeAction() and inDialog())

    private fun inDialog(): SemanticsMatcher = hasAnyAncestor(isDialog())

    private fun line(@StringRes name: Int, value: String): String = string(R.string.reports_filter_line, string(name), value)

    private fun scrolls(node: SemanticsNodeInteraction): Boolean = hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())

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

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK = 0.5.dp
        val MIN_TOUCH_TARGET = 48.dp

        val ACCOUNT = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /** A Thursday at noon, in the middle of [WEEK]. */
        val TODAY: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
        val WEEK = ReportRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18))

        const val WEBSITE = "Website redesign for the Acme Corporation"
        const val INTERNAL = "Internal tooling"
        const val ACME = "Acme Corporation"
        const val DEEP_WORK = "deep work"
        const val MEETINGS = "meetings"

        val OPTIONS = FilterOptions(
            projects = listOf(FilterOption(1, WEBSITE), FilterOption(2, INTERNAL)),
            clients = listOf(FilterOption(10, ACME)),
            tags = listOf(FilterOption(20, DEEP_WORK), FilterOption(21, MEETINGS)),
            negation = true,
        )

        /** Every kind of filter at once, each with the "without" choice where it has one. */
        val FILTERS = ReportFilters(
            projects = listOf(1, null),
            clients = listOf(10),
            tags = listOf(20, 21, null),
            billable = BillableFilter.NON_BILLABLE,
            clientsNot = true,
        )

        val HEADINGS = listOf(R.string.reports_filter_project, R.string.reports_filter_client, R.string.reports_filter_tag, R.string.reports_filter_billable)

        /** The "without" choice of each list. */
        val CHECKBOXES = listOf(R.string.reports_without_project, R.string.reports_filter_without_client, R.string.reports_filter_without_tags)

        /** The label and the TalkBack text of "Is" and "Is not". */
        val MODES = listOf(
            R.string.reports_filter_mode_is to R.string.reports_filter_mode_is_description,
            R.string.reports_filter_mode_not to R.string.reports_filter_mode_not_description,
        )

        val RADIO_BUTTONS = listOf(
            R.string.reports_filter_billable_all,
            R.string.reports_filter_billable_only,
            R.string.reports_filter_non_billable_only,
        )
    }
}
