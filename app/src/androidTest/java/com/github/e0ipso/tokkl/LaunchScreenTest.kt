package com.github.e0ipso.tokkl

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The first screen of a signed-in session, the Timer page, with the bottom bar. */
class LaunchScreenTest {
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The page shows its title, where the timer goes, and the List and Calendar switch with List
     * chosen. The bar holds Timer, Pomodoro, Projects, Settings and More, and marks Timer.
     */
    @Test
    fun opensOnTheTimerPageWithTheBottomBar() {
        composeRule.awaitTimerPage()

        composeRule.onNode(hasText(string(R.string.nav_timer)) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.timer_view_list)) and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText(string(R.string.timer_view_calendar)) and isSelectable()).assertIsDisplayed()

        val tabs = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.left }
        val labels = tabs.map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { text -> text.text } }
        val expected = listOf(
            R.string.nav_timer,
            R.string.pomodoro_title,
            R.string.nav_projects,
            R.string.settings,
            R.string.nav_more,
        )
        assertEquals(expected.map(::string), labels)
        assertEquals(listOf(true, false, false, false, false), tabs.map { it.config.getOrNull(SemanticsProperties.Selected) })
    }

    /**
     * The page title shows in capitals, and TalkBack reads it once, as the heading "Timer" in
     * sentence case. No wordmark shows: it appears only on sign-in and the launch splash.
     */
    @Test
    fun theTitleIsOneTalkBackHeadingAndNoWordmarkShows() {
        composeRule.awaitTimerPage()

        val title = string(R.string.nav_timer)
        val headings = composeRule.onAllNodes(hasText(title) and isHeading())
        headings.assertCountEquals(1)
        val heading = headings[0]
        assertEquals(listOf(title), heading.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.map { it.text })
        val drawn = mutableListOf<TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(drawn) }
        assertEquals(title.uppercase(), drawn.single().layoutInput.text.text)
        composeRule.onAllNodes(hasContentDescription(string(R.string.app_name))).assertCountEquals(0)
    }

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)
}
