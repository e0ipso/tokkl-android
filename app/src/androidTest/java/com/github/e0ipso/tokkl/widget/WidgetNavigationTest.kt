package com.github.e0ipso.tokkl.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.components.accountRefresh
import com.github.e0ipso.tokkl.components.capabilities
import com.github.e0ipso.tokkl.components.reportsViewModel
import com.github.e0ipso.tokkl.navigation.PageTap
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.reports.RangePreset
import com.github.e0ipso.tokkl.reports.ReportTab
import com.github.e0ipso.tokkl.reports.ReportsState
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** These are page opens, with no session action and no notification phase/account bypass. */
class WidgetNavigationTest {
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    @Test fun todayOpensOnColdStartAndReplacesRetainedWeeklySelection() {
        runBlocking { app.accountRefresh.refresh().join() }
        ActivityScenario.launch<MainActivity>(
            PageTap.TODAY_REPORT.into(mainActivityIntent(context)),
        ).use { scenario ->
            assertToday(scenario)
            lateinit var model: ReportsViewModel
            scenario.onActivity { model = reportsViewModel(it) }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                model.selectTab(ReportTab.WEEKLY)
                model.select(RangePreset.LAST_WEEK)
            }
            context.startActivity(PageTap.TODAY_REPORT.into(mainActivityIntent(context)))
            assertToday(scenario)
            scenario.recreate()
            assertToday(scenario)
            // Consumed extras must not reopen Today after the user leaves Reports.
            context.startActivity(PageTap.TIMER_WIDGET.into(mainActivityIntent(context)))
            compose.awaitTimerPage()
            scenario.recreate()
            compose.awaitTimerPage()
        }
    }

    @Test fun pomodoroPageRouteWorksOnColdAndRetainedActivityAndFallsBackWithoutCapability() = runBlocking<Unit> {
        app.accountRefresh.refresh().join()
        assertTrue("the pinned server supports Timer state", app.capabilities.state.value.timerState)
        ActivityScenario.launch<MainActivity>(
            PageTap.POMODORO_WIDGET.into(mainActivityIntent(context)),
        ).use { scenario ->
            awaitPomodoroPage()
            context.startActivity(PageTap.TIMER_WIDGET.into(mainActivityIntent(context)))
            compose.awaitTimerPage()
            context.startActivity(PageTap.POMODORO_WIDGET.into(mainActivityIntent(context)))
            awaitPomodoroPage()
            scenario.recreate()
            awaitPomodoroPage()
            app.capabilities.forget()
            context.startActivity(PageTap.POMODORO_WIDGET.into(mainActivityIntent(context)))
            compose.awaitTimerPage()
        }
    }

    @SignedOut
    @Test fun widgetRoutesWithoutAnAccountOpenSignInAndConsumeTheExtras() {
        for (page in listOf(PageTap.TODAY_REPORT, PageTap.POMODORO_WIDGET, PageTap.TIMER_WIDGET)) {
            val intent = page.into(mainActivityIntent(context))
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.awaitText("Sign in")
                scenario.onActivity { assertFalse(it.intent.hasExtra(page.extra)) }
                scenario.recreate()
                compose.awaitText("Sign in")
            }
        }
    }

    private fun awaitPomodoroPage() {
        val heading = hasText("Pomodoro") and isHeading()
        // The widget route reads the stored session on IO, beyond Compose's idle boundary.
        compose.waitUntil("the widget opens the Pomodoro page", 5_000) {
            compose.onAllNodes(heading).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(heading).assertIsDisplayed()
    }

    private fun assertToday(scenario: ActivityScenario<MainActivity>) {
        compose.waitUntil(15_000) {
            var today = false
            scenario.onActivity {
                val model = reportsViewModel(it)
                val selection = when (val state = model.page.value.report) {
                    is ReportsState.Loaded -> state.selection
                    is ReportsState.Loading -> state.selection
                    is ReportsState.Failed -> state.selection
                    else -> null
                }
                today = selection?.preset == RangePreset.TODAY && model.page.value.tab == ReportTab.SUMMARY
            }
            today
        }
        compose.onNodeWithText("Reports").assertIsDisplayed()
    }
}
