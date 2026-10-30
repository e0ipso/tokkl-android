package com.github.e0ipso.tokkl.widget

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.awaitText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.reports.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** These are page opens, with no session action and no notification phase/account bypass. */
class WidgetNavigationTest {
    @get:Rule(order = 0) val session = SessionRule()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    @Test fun todayOpensOnColdStartAndReplacesRetainedWeeklySelection() {
        runBlocking { app.accountSettings.refresh().join() }
        ActivityScenario.launch<MainActivity>(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TODAY)).use { scenario ->
            assertToday(scenario)
            lateinit var model: ReportsModel
            scenario.onActivity { model = ViewModelProvider(it)[ReportsViewModel::class.java].model }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                model.selectTab(ReportTab.WEEKLY)
                model.select(RangePreset.LAST_WEEK)
            }
            context.startActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TODAY))
            assertToday(scenario)
            scenario.recreate()
            assertToday(scenario)
            // Consumed extras must not reopen Today after the user leaves Reports.
            context.startActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TIMER))
            compose.awaitTimerPage()
            scenario.recreate()
            compose.awaitTimerPage()
        }
    }

    @Test fun pomodoroPageRouteWorksOnColdAndRetainedActivityAndFallsBackWithoutCapability() {
        runBlocking { app.accountSettings.refresh().join() }
        assertTrue("the pinned server supports Timer state", app.capabilities.state.value.timerState)
        ActivityScenario.launch<MainActivity>(WidgetNavigation.intent(context, WidgetNavigation.OPEN_POMODORO)).use { scenario ->
            awaitPomodoroPage()
            context.startActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TIMER))
            compose.awaitTimerPage()
            context.startActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_POMODORO))
            awaitPomodoroPage()
            scenario.recreate()
            awaitPomodoroPage()
            app.capabilities.forget()
            context.startActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_POMODORO))
            compose.awaitTimerPage()
        }
    }

    @SignedOut
    @Test fun widgetRoutesWithoutAnAccountOpenSignInAndConsumeTheExtras() {
        for (page in listOf(WidgetNavigation.OPEN_TODAY, WidgetNavigation.OPEN_POMODORO, WidgetNavigation.OPEN_TIMER)) {
            val intent = WidgetNavigation.intent(context, page)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.awaitText("Sign in")
                scenario.onActivity { assertFalse(it.intent.hasExtra(page)) }
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
                val model = ViewModelProvider(it)[ReportsViewModel::class.java].model
                val selection = when (val state = model.state.value) {
                    is ReportsState.Loaded -> state.selection
                    is ReportsState.Loading -> state.selection
                    is ReportsState.Failed -> state.selection
                    else -> null
                }
                today = selection?.preset == RangePreset.TODAY && model.tab.value == ReportTab.SUMMARY
            }
            today
        }
        compose.onNodeWithText("Reports").assertIsDisplayed()
    }
}
