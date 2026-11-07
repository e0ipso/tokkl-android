package com.github.e0ipso.tokkl.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitPomodoroOffered
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.navhost.label
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.math.abs
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.core.ui.R as UiR
import com.github.e0ipso.tokkl.feature.clients.R as ClientsR
import com.github.e0ipso.tokkl.feature.projects.R as ProjectsR
import com.github.e0ipso.tokkl.feature.reports.R as ReportsR
import com.github.e0ipso.tokkl.feature.settings.R as SettingsR
import com.github.e0ipso.tokkl.feature.tags.R as TagsR
import com.github.e0ipso.tokkl.feature.timer.R as TimerR
import com.github.e0ipso.tokkl.feature.trash.R as TrashR

/**
 * Every signed-in page in the real activity starts with the shared top bar: its title as a
 * heading, above and outside every part of the page that scrolls. The pages of the bottom bar and
 * the More sheet have no back button; the pages opened from another page, Licences, the project
 * and client forms and the entry form, have one beside the title. No page shows the wordmark.
 */
class PageTopBarTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    @Test
    fun thePagesOfTheBarAndTheSheetHaveTheBarWithoutABackButton() {
        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        composeRule.awaitPomodoroOffered(NETWORK_TIMEOUT_MS)
        assertTopBar(TimerR.string.nav_timer, back = false)

        for (page in listOf(SharedR.string.pomodoro_title, ProjectsR.string.nav_projects, SettingsR.string.settings)) {
            composeRule.onNode(barItem(page)).performClick()
            assertTopBar(page, back = false)
        }
        for (page in listOf(
            ReportsR.string.reports,
            TimerR.string.nav_templates,
            ClientsR.string.nav_clients,
            TagsR.string.nav_tags,
            TrashR.string.nav_trash,
        )) {
            composeRule.onNode(barItem(R.string.nav_more)).performClick()
            composeRule.waitUntil(
                "the More sheet opens",
                TIMEOUT_MS,
            ) { exists(isSelectable() and hasText(string(page))) }
            composeRule.onNode(isSelectable() and hasText(string(page))).performScrollTo().performClick()
            composeRule.waitUntil(
                "the More sheet closes",
                TIMEOUT_MS,
            ) { !exists(hasText(string(R.string.nav_more)) and isHeading()) }
            assertTopBar(page, back = false)
        }
    }

    @Test
    fun licencesHasTheBarWithABackButton() {
        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        composeRule.onNode(barItem(SettingsR.string.settings)).performClick()
        composeRule.onNode(
            hasText(string(SettingsR.string.licences)) and hasClickAction(),
        ).performScrollTo().performClick()

        assertTopBar(SettingsR.string.licences, back = true)
    }

    @Test
    fun theEntryFormHasTheBarWithABackButton() {
        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        openForm(TimerR.string.timer_add_entry)

        assertTopBar(TimerR.string.entry_form_new, back = true)
    }

    @Test
    fun theProjectAndClientFormsHaveTheBarWithABackButton() {
        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        composeRule.onNode(barItem(ProjectsR.string.nav_projects)).performClick()
        openForm(ProjectsR.string.projects_new)
        assertTopBar(ProjectsR.string.project_form_new, back = true)

        composeRule.onNode(hasContentDescription(string(UiR.string.back)) and hasClickAction()).performClick()
        assertTopBar(ProjectsR.string.nav_projects, back = false)

        composeRule.onNode(barItem(R.string.nav_more)).performClick()
        composeRule.waitUntil(
            "the More sheet opens",
            TIMEOUT_MS,
        ) { exists(isSelectable() and hasText(string(ClientsR.string.nav_clients))) }
        composeRule.onNode(isSelectable() and hasText(string(ClientsR.string.nav_clients))).performClick()
        openForm(ClientsR.string.clients_new)
        assertTopBar(ClientsR.string.client_form_new, back = true)
    }

    /** Taps the page's [button] for a new item once what the page loads has turned it on. */
    private fun openForm(@StringRes button: Int) {
        val matcher = hasText(string(button)) and hasClickAction() and isEnabled()
        composeRule.waitUntil("\"${string(button)}\" can be tapped", NETWORK_TIMEOUT_MS) { exists(matcher) }
        composeRule.onNode(matcher).performClick()
    }

    /**
     * The page titled [title] shows the bar: the title as a heading, above every part of the page
     * that scrolls, with the back button beside it when [back] says so, and no wordmark anywhere.
     */
    private fun assertTopBar(@StringRes title: Int, back: Boolean) {
        val name = string(title)
        composeRule.waitUntil("the $name page shows", TIMEOUT_MS) { exists(hasText(name) and isHeading()) }
        val heading = composeRule.onNode(hasText(name) and isHeading()).assertIsDisplayed()
        val headingBounds = heading.getBoundsInRoot()

        val scrolling = composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        for (node in scrolling) {
            val top = with(composeRule.density) { node.boundsInRoot.top.toDp() }
            assertTrue(
                "$name's title ends at ${headingBounds.bottom}, inside a part that scrolls from $top",
                headingBounds.bottom <= top + SLACK,
            )
        }

        val backs = composeRule.onAllNodes(hasContentDescription(string(UiR.string.back)) and hasClickAction())
        if (back) {
            backs.assertCountEquals(1)
            val button = backs[0].assertIsDisplayed().getBoundsInRoot()
            val buttonMiddle = (button.top + button.bottom) / 2
            val headingMiddle = (headingBounds.top + headingBounds.bottom) / 2
            assertTrue(
                "the back button sits beside $name's title",
                abs((buttonMiddle - headingMiddle).value) <= SLACK.value,
            )
            assertTrue("the back button comes before $name's title", button.right <= headingBounds.left + SLACK)
        } else {
            backs.assertCountEquals(0)
        }

        composeRule.onAllNodes(hasContentDescription(string(UiR.string.app_name))).assertCountEquals(0)
    }

    private fun exists(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(
        matcher,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun barItem(@StringRes label: Int): SemanticsMatcher =
        hasText(string(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val NETWORK_TIMEOUT_MS = 30_000L

        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK: Dp = 1.dp
    }
}
