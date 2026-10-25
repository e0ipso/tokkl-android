package com.github.e0ipso.tokkl.trash

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The trash page, its question and its offline state at Android's largest font scale, 200%: no
 * text is cut off, and each row's Restore and Delete forever stay on the screen without
 * overlapping, wrapping onto lines of their own when they must.
 */
class TrashFitTest {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST, largestDisplaySize = true)).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theListFits() {
        show(LOADED)
        assertNoTextCutOff("the trash")
        for (entry in ENTRIES) {
            val name = entry.description ?: context.getString(R.string.trash_no_description)
            val restore = control(R.string.trash_restore_named, name)
            val purge = control(R.string.trash_purge_named, name)
            // The list is lazy: an entry below the fold is not laid out yet, and performScrollTo
            // cannot bring it in. The list scrolls by index until the control shows.
            val list = composeRule.onNode(hasScrollToIndexAction())
            list.performScrollToNode(controlMatcher(R.string.trash_restore_named, name))
            list.performScrollToNode(controlMatcher(R.string.trash_purge_named, name))
            val screen = composeRule.onRoot().getBoundsInRoot()
            val boxes = listOf(restore, purge).map { it.assertIsDisplayed().getBoundsInRoot() }
            for (box in boxes) assertTrue("a control of $name leaves the screen", box.inside(screen))
            assertFalse("Restore overlaps Delete forever for $name", boxes[0].overlaps(boxes[1]))
        }
    }

    @Test
    fun theEmptyQuestionFits() {
        show(LOADED.copy(confirmingEmpty = true))
        assertNoTextCutOff("the question before emptying the trash")
    }

    @Test
    fun theOfflineNoticeFits() {
        show(TrashState(loadProblem = ServerProblem.Unreachable), offline = true)
        assertNoTextCutOff("the trash offline")
    }

    private fun show(state: TrashState, offline: Boolean = false) {
        composeRule.setContent { TokklTheme(darkTheme = false) { Content(state, offline) } }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Content(state: TrashState, offline: Boolean) {
        TrashContent(state = state, offline = offline, settings = SETTINGS, actions = TrashActions())
    }

    private fun control(@StringRes label: Int, name: String) = composeRule.onNode(controlMatcher(label, name))

    private fun controlMatcher(@StringRes label: Int, name: String) =
        hasContentDescription(context.getString(label, name)) and hasClickAction()

    /** No text shown, in the page or in a dialog over it, is cut off in height. */
    private fun assertNoTextCutOff(where: String) {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("the scan found text in $where", nodes.isNotEmpty())
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: continue
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: layout.layoutInput.text.text
            assertFalse("text cut off in $where: $text", layout.didOverflowHeight)
        }
    }

    private fun DpRect.inside(outer: DpRect): Boolean =
        left >= outer.left && top >= outer.top && right <= outer.right && bottom <= outer.bottom

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        val SETTINGS = AccountSettings(timeZone = "Europe/Madrid", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)
        val ENTRIES = listOf(
            TrashEntry(
                id = 1,
                description = "Review the quarterly report with the whole team",
                start = "2026-10-19T07:00:00+00:00",
                stop = "2026-10-19T08:30:00+00:00",
                duration = 5400,
                projectId = 3,
                projectName = "Website redesign for the new brand",
                deletedAt = "2026-10-20T08:00:00+00:00",
            ),
            TrashEntry(id = 2, description = null, start = "2025-12-30T16:00:00+00:00", duration = -1767110400, deletedAt = "2026-01-02T09:15:00+00:00"),
        )
        val LOADED = TrashState(entries = ENTRIES)
    }
}
