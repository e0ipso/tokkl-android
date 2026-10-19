package com.github.e0ipso.tokkl

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.github.e0ipso.tokkl.ui.assertBrandReadsOnce
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
     * The page shows the brand, where the timer goes, and the List and Calendar switch with List
     * chosen. The bar holds the web app's phone items in its order, and marks Timer.
     */
    @Test
    fun opensOnTheTimerPageWithTheBottomBar() {
        composeRule.awaitTimerPage()

        // The brand's wordmark, an image that TalkBack reads as the app's name.
        composeRule.onNodeWithContentDescription(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.timer_view_list)) and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText(string(R.string.timer_view_calendar)) and isSelectable()).assertIsDisplayed()

        val tabs = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.left }
        val labels = tabs.map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { text -> text.text } }
        val expected = listOf(R.string.nav_timer, R.string.reports, R.string.nav_projects, R.string.settings, R.string.nav_more)
        assertEquals(expected.map(::string), labels)
        assertEquals(listOf(true, false, false, false, false), tabs.map { it.config.getOrNull(SemanticsProperties.Selected) })
    }

    /** TalkBack reads the brand on the Timer page, the wordmark alone, once, as "Tokkl". */
    @Test
    fun theBrandIsOneTalkBackNode() {
        composeRule.awaitTimerPage()

        composeRule.assertBrandReadsOnce()
    }

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)
}
