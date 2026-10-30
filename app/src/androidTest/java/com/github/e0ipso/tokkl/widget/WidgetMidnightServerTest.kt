package com.github.e0ipso.tokkl.widget

import androidx.activity.compose.setContent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timer.TimerTotals
import com.github.e0ipso.tokkl.timer.timerPeriodTotals
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain

class WidgetMidnightServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app
    @Before fun prepare() = fixture.prepare()
    @After fun cleanup() { app.widgetClock = Clock.systemUTC(); fixture.cleanup() }

    @Test fun unchangedRealRunningCheckRollsTheAccountDayAndMatchesTimerTotalsWithoutIdleWork() {
        // Clock injection avoids SET_TIME and leaves OS alarm/network time untouched.
        val settings = app.accountSettings.state.value!!
        val time = settings.time()!!
        val day = time.today()
        val midnight = day.plusDays(1).atStartOfDay(time.zone).toInstant()
        val clock = AdvancingClock(midnight.minusSeconds(2))
        app.widgetClock = clock
        fixture.account.createEntry("Stopped today", day.atTime(10, 0).atZone(time.zone).toInstant(), day.atTime(11, 0).atZone(time.zone).toInstant())
        // Native Chronometers use real wall time. Keep the start in the past for both
        // clocks, including CI runs before noon in the account's timezone.
        fixture.account.createEntry("Crosses midnight", day.atStartOfDay(time.zone).toInstant())
        fixture.refresh()
        app.followNoTimerEvents()
        NativeWidget().use { host ->
            host.awaitText("Crosses midnight")
            val before = ready()
            assertEquals(day, before.day!!.date)
            assertNotNull(before.day.running)
            val beforeSeconds = timerPeriodTotals(fixture.rows(), settings, clock.instant())!!.todaySeconds
            assertEquals(beforeSeconds, before.day.totalSeconds(clock.instant()))
            host.await("the running and day-total native clocks are drawn") { host.clockBaseValues().size >= 2 }
            val base = host.clockBaseValues()[1]
            val nativeWallBase = Instant.now().minusMillis(SystemClock.elapsedRealtime() - base)
            assertEquals("native day base matches the Timer page's same-cache total", beforeSeconds.toDouble(), Duration.between(nativeWallBase, clock.instant()).seconds.toDouble(), 1.0)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity { it.setContent { TokklTheme { TimerTotals(app.cache.timeEntries, app.accountSettings.state, clock) } } }
                compose.waitUntil(5_000) {
                    compose.onNodeWithTag("timer_totals_today").fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == settings.formatDuration(beforeSeconds) }
                }
            }
            val wm = WorkManager.getInstance(fixture.context)
            val check = runBlocking { withTimeout(15_000) { wm.getWorkInfosForUniqueWorkFlow(RunningCheck.WORK_NAME).first { it.singleOrNull()?.state == WorkInfo.State.ENQUEUED }.single() } }
            val rows = fixture.rows()
            val oldKey = before.key()
            clock.at = midnight.plusSeconds(1)
            // No new activity or cache write triggers rollover: only the existing worker's lifecycle.
            val driver = WorkManagerTestInitHelper.getTestDriver(fixture.context)!!
            driver.setInitialDelayMet(check.id); driver.setAllConstraintsMet(check.id)
            host.await("running check redraws a new account day") { WidgetStore(fixture.context).load() != oldKey }
            val after = ready()
            assertEquals(day.plusDays(1), after.day!!.date)
            assertEquals("unchanged response need not write Room", rows, fixture.rows())
            assertNull("overnight running entry belongs to its start day", after.day.running)
            assertEquals(0L, after.day.totalSeconds(clock.instant()))
            assertEquals(timerPeriodTotals(rows, settings, clock.instant())!!.todaySeconds, after.day.totalSeconds(clock.instant()))
            // Render the actual Timer-page totals component with the same real cache and clock.
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity { it.setContent { TokklTheme { TimerTotals(app.cache.timeEntries, app.accountSettings.state, clock) } } }
                compose.waitUntil(5_000) {
                    compose.onNodeWithTag("timer_totals_today").fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == settings.formatDuration(0) }
                }
            }
            assertEquals("only one running-check is active", 1, wm.getWorkInfosForUniqueWork(RunningCheck.WORK_NAME).get().count { !it.state.isFinished })
            fixture.stop()
            host.awaitDescription(fixture.context.getString(R.string.widget_start_description))
            host.await("idle cancels the existing worker") { wm.getWorkInfosForUniqueWork(RunningCheck.WORK_NAME).get().none { !it.state.isFinished } }
        }
    }
    private fun ready() = runBlocking { app.widgetState.first() } as WidgetState.Ready
    private class AdvancingClock(@Volatile var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = at
    }
}
