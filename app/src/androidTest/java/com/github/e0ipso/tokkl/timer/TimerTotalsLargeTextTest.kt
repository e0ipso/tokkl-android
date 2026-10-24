package com.github.e0ipso.tokkl.timer

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** List header ordering, text wrapping and scroll-away at Android's largest font size. */
class TimerTotalsLargeTextTest {
    private val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST)).around(compose)

    @SignedOut @Test
    fun listTotalsFitAndScrollAwayWithTheTimer() {
        val rows = MutableStateFlow(emptyList<CachedTimeEntry>())
        val settings = MutableStateFlow<AccountSettings?>(AccountSettings("UTC", DayOfWeek.SUNDAY, null))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.enableEdgeToEdge(activity.window)
                activity.setContent { TokklTheme {
                    TimerScreen(
                        view = TimerView.LIST, onViewChange = {},
                        timer = { Column {
                            TimerBarContent(
                                TimerState.Idle(EntryFields(), EntryLabels(), 1), 0,
                                {}, {}, {}, {}, {}, {}, {}, { _, _, _, _, _ -> },
                            )
                            TimerTotals(rows, settings, Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC))
                        } },
                        listContent = { LazyColumn { items(60) { Text("Entry $it", Modifier.testTag("history_$it")) } } },
                        calendarContent = {},
                    )
                } }
            }
            for (tag in listOf("timer_totals_today", "timer_totals_week")) {
                val node = compose.onNodeWithTag(tag).performScrollTo()
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode()
                assertEquals(bounds.size.height.toFloat(), bounds.boundsInRoot.height, 1f)
                // Check the actual child text layout, not merely the merged semantics bounds.
                val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasText("0:00:00"), useUnmergedTree = true)
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                texts[0].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertTrue(layouts.none { it.didOverflowHeight })
            }
            compose.onRoot().performTouchInput { swipeUp() }
            compose.onRoot().performTouchInput { swipeUp() }
            compose.onNodeWithTag("timer_totals_today").assertIsNotDisplayed()
            compose.onNodeWithTag("timer_totals_week").performScrollTo().assertIsDisplayed()
        }
    }
}
