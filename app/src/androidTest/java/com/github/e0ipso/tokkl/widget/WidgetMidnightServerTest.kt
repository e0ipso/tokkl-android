package com.github.e0ipso.tokkl.widget

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.components.accountSettings
import com.github.e0ipso.tokkl.components.cache
import com.github.e0ipso.tokkl.components.testClock
import com.github.e0ipso.tokkl.components.widgetState
import com.github.e0ipso.tokkl.feature.widget.R
import com.github.e0ipso.tokkl.followNoTimerEvents
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timer.TimerTotalsOf
import com.github.e0ipso.tokkl.timerstate.timerPeriodTotals
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.Duration
import java.time.Instant

class WidgetMidnightServerTest {
    private val compose = createEmptyComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app

    @Before fun prepare() = fixture.prepare()

    @After fun cleanup() {
        app.testClock.at = null
        fixture.cleanup()
    }

    @Test fun unchangedRealRunningCheckRollsTheAccountDayAndMatchesTimerTotalsWithoutIdleWork() {
        // Clock injection avoids SET_TIME and leaves OS alarm/network time untouched.
        val settings = app.accountSettings.state.value!!
        val time = settings.time()!!
        val day = time.today()
        val midnight = day.plusDays(1).atStartOfDay(time.zone).toInstant()
        val clock = app.testClock
        clock.at = midnight.minusSeconds(2)
        fixture.account.createEntry(
            "Stopped today",
            day.atTime(10, 0).atZone(time.zone).toInstant(),
            day.atTime(11, 0).atZone(time.zone).toInstant(),
        )
        // Native Chronometers use real wall time. Keep the start in the past for both
        // clocks, including CI runs before noon in the account's timezone.
        fixture.account.createEntry("Crosses midnight", day.atStartOfDay(time.zone).toInstant())
        fixture.refresh()
        app.followNoTimerEvents()
        NativeWidget().use { host ->
            host.awaitText("Crosses midnight")
            val before = ready()
            val beforeDay = before.day!!
            assertEquals(day, beforeDay.date)
            assertNotNull(beforeDay.running)
            val beforeSeconds = timerPeriodTotals(fixture.rows(), settings, clock.instant())!!.todaySeconds
            assertEquals(beforeSeconds, beforeDay.totalSeconds(clock.instant()))
            host.await("the running and day-total native clocks are drawn") { host.clockBaseValues().size >= 2 }
            val base = host.clockBaseValues()[1]
            val nativeWallBase = Instant.now().minusMillis(SystemClock.elapsedRealtime() - base)
            assertEquals(
                "native day base matches the Timer page's same-cache total",
                beforeSeconds.toDouble(),
                Duration.between(nativeWallBase, clock.instant()).seconds.toDouble(),
                1.0,
            )
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity {
                    it.setContent {
                        TokklTheme {
                            TimerTotalsOf(
                                app.cache.timeEntries,
                                app.accountSettings.state,
                                clock = clock,
                            )
                        }
                    }
                }
                compose.awaitTodayTotal(settings.formatDuration(beforeSeconds))
            }
            val wm = WorkManager.getInstance(fixture.context)
            val check = runBlocking {
                withTimeout(
                    15_000,
                ) {
                    wm.getWorkInfosForUniqueWorkFlow(
                        RunningCheck.WORK_NAME,
                    ).first { it.singleOrNull()?.state == WorkInfo.State.ENQUEUED }.single()
                }
            }
            val rows = fixture.rows()
            val oldKey = before.key()
            clock.at = midnight.plusSeconds(1)
            // No new activity or cache write triggers rollover: only the existing worker's lifecycle.
            val driver = WorkManagerTestInitHelper.getTestDriver(fixture.context)!!
            driver.setInitialDelayMet(check.id)
            driver.setAllConstraintsMet(check.id)
            host.await("running check redraws a new account day") {
                runBlocking { WidgetStore(fixture.context, Dispatchers.IO).load() } != oldKey
            }
            val after = ready()
            val afterDay = after.day!!
            assertEquals(day.plusDays(1), afterDay.date)
            assertEquals("unchanged response need not write Room", rows, fixture.rows())
            assertNull("overnight running entry belongs to its start day", afterDay.running)
            assertEquals(0L, afterDay.totalSeconds(clock.instant()))
            assertEquals(
                timerPeriodTotals(rows, settings, clock.instant())!!.todaySeconds,
                afterDay.totalSeconds(clock.instant()),
            )
            // Render the actual Timer-page totals component with the same real cache and clock.
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity {
                    it.setContent {
                        TokklTheme {
                            TimerTotalsOf(
                                app.cache.timeEntries,
                                app.accountSettings.state,
                                clock = clock,
                            )
                        }
                    }
                }
                compose.awaitTodayTotal(settings.formatDuration(0))
            }
            assertEquals(
                "only one running-check is active",
                1,
                wm.getWorkInfosForUniqueWork(RunningCheck.WORK_NAME).get().count { !it.state.isFinished },
            )
            fixture.stop()
            host.awaitDescription(fixture.context.getString(R.string.widget_start_description))
            host.await(
                "idle cancels the existing worker",
            ) { wm.getWorkInfosForUniqueWork(RunningCheck.WORK_NAME).get().none { !it.state.isFinished } }
        }
    }

    /**
     * TimerTotals composes nothing until the cache's first emission, so right after setContent
     * the total is missing for a moment. Missing means not shown yet, not a failure.
     */
    private fun ComposeTestRule.awaitTodayTotal(text: String) = waitUntil("today's total shows $text", 5_000) {
        onAllNodes(
            hasTestTag("timer_totals_today") and hasText(text),
        ).fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1
    }
    private fun ready() = runBlocking { app.widgetState.first() } as WidgetState.Ready
}
