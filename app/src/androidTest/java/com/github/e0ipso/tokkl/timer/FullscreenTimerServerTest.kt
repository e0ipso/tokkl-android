package com.github.e0ipso.tokkl.timer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.setContent
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.time.parseDuration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Normal real-host acceptance at usual and largest font sizes on API31/36. */
abstract class FullscreenTimerAcceptanceTest(private val fontScale: Float) {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(SystemFontScale(fontScale)).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app
    private val context get() = fixture.context
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun prepare() { fixture.prepare(); fixture.start("Pause fixture"); fixture.ageRunning(); fixture.drain() }
    @After fun cleanup() { fixture.cleanup() }

    @Test
    fun enterBackAndExitKeepBothViewPositionsAndIdentity() {
        val day = app.accountSettings.state.value!!.time()!!.today()
        val midnight = app.accountSettings.state.value!!.time()!!.instantAt(day, 0)
        for (index in 1..24) fixture.account.createEntry("History $index", midnight.minusSeconds(index * 3600L), midnight.minusSeconds(index * 3600L - 600))
        runBlocking { app.accountSettings.refresh().join() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            val token = shownTarget()
            val queueBefore = runBlocking { app.changes.unsentCount.first() }
            for (view in listOf(TimerView.LIST, TimerView.CALENDAR)) {
                chooseView(view)
                if (view == TimerView.CALENDAR) {
                    scenario.onActivity { ViewModelProvider(it)[CalendarViewModel::class.java].model.select(day.minusWeeks(2)) }
                }
                compose.onRoot().performTouchInput { swipeUp() }
                compose.waitForIdle()
                val positions = scrollPositions()
                // A semantic click invokes the offscreen labelled action without scrolling the top.
                compose.onNodeWithText(context.getString(R.string.timer_open_fullscreen)).performSemanticsAction(SemanticsActions.OnClick)
                awaitFocused()
                assertActions(running = true)
                compose.onNode(hasText(context.getString(R.string.nav_more)) and hasClickAction()).assertDoesNotExist()
                compose.onNode(isSelectable() and hasText(context.getString(view.label))).assertDoesNotExist()
                compose.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
                assertEquals(token, shownTarget())
                scenario.recreate()
                awaitFocused()
                assertEquals(token, shownTarget())
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.awaitTimerPage()
                compose.onNode(isSelectable() and hasText(context.getString(view.label))).assertIsSelected()
                assertEquals(positions, scrollPositions())
                compose.onNodeWithText(context.getString(R.string.timer_open_fullscreen)).performSemanticsAction(SemanticsActions.OnClick)
                awaitFocused()
                focusedButton(R.string.timer_exit_fullscreen).performScrollTo().performClick()
                compose.awaitTimerPage()
                assertEquals(positions, scrollPositions())
                assertEquals(token, shownTarget())
                assertEquals(queueBefore, runBlocking { app.changes.unsentCount.first() })
            }
        }
    }

    @Test
    fun offlineActionsAndSharedEditorsKeepCumulativeTimeAndFinalStopExits() {
        val time = app.accountSettings.state.value!!.time()!!
        val yesterdayNoon = time.instantAt(time.today().minusDays(1), 12 * 60)
        fixture.ageRunning(java.time.Duration.between(yesterdayNoon, Instant.now()).seconds)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            openFocused()
            AirplaneMode.set(true, fixture.account.client)
            val original = (fixture.controls() as TimerControls.Running).entry
            focusedButton(R.string.timer_focused_edit).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.entry_form_edit_running)).assertExists()
            compose.onNode(hasText(context.getString(R.string.entry_end)) and hasSetTextAction()).assertDoesNotExist()
            compose.onNode(hasText(context.getString(R.string.entry_duration)) and hasSetTextAction()).assertDoesNotExist()
            // Shared start editor; the field keeps seconds and a real active start, never a display base.
            compose.onNode(hasText(context.getString(R.string.entry_start)) and hasSetTextAction()).performTextClearance()
            val settings = app.accountSettings.state.value!!
            val earlier = settings.formatTime(original.start.minusSeconds(600), settings.time()!!.zone)
            compose.onNode(hasText(context.getString(R.string.entry_start)) and hasSetTextAction()).performTextInput(earlier)
            compose.onNodeWithText(context.getString(R.string.entry_save)).performScrollTo().performClick()
            awaitFocused()
            assertEquals(original.id, (fixture.controls() as TimerControls.Running).entry.id)
            assertTrue((fixture.controls() as TimerControls.Running).entry.start < original.start)
            val first = elapsedText()
            compose.waitUntil("focused clock ticks locally", 5_000) { elapsedText() != first }
            focusedButton(R.string.timer_pause).performScrollTo().performClick()
            awaitPaused()
            assertActions(running = false)
            val frozen = (fixture.controls() as TimerControls.Paused).seconds
            assertEquals(frozen, parseDuration(elapsedText()))
            focusedButton(R.string.timer_focused_edit).performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("fullscreen_timer")))
                .performTextClearance()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("fullscreen_timer")))
                .performTextInput("Edited paused fields")
            focusedButton(R.string.timer_fields_done).performScrollTo().performClick()
            assertEquals(frozen, parseDuration(elapsedText()))
            scenario.recreate()
            awaitFocused()
            assertEquals(frozen, parseDuration(elapsedText()))
            focusedButton(R.string.timer_resume).performScrollTo().performClick()
            val resumed = awaitRunning()
            assertActions(running = true)
            assertNotEquals(original.id, resumed.entry.id)
            assertEquals("Edited paused fields", resumed.entry.description)
            assertTrue(parseDuration(elapsedText())!! >= frozen)
            assertEquals("Pause fixture", runBlocking { app.cache.timeEntries.first() }.first { it.id == original.id }.description)
            focusedButton(R.string.timer_stop).performScrollTo().performClick()
            awaitIdle()
            compose.awaitTimerPage()
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            AirplaneMode.set(false, fixture.account.client)
            fixture.drain()
        }
    }

    @Test
    fun actualRotationAndLargeTextKeepFocusedRunningAndPausedActionsReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            openFocused()
            scenario.onActivity { assertEquals(fontScale, it.resources.configuration.fontScale, 0f) }
            for (paused in listOf(false, true)) {
                if (paused) {
                    focusedButton(R.string.timer_pause).performScrollTo().performClick()
                    awaitPaused()
                }
                val token = shownTarget()
                for ((requested, orientation) in listOf(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
                )) {
                    val before = resumedActivity()
                    scenario.onActivity { it.requestedOrientation = requested }
                    compose.waitUntil("real activity rotation retains focus", 15_000) {
                        resumedActivity()?.let { it !== before && it.resources.configuration.orientation == orientation } == true
                    }
                    awaitFocused()
                    assertActions(running = !paused)
                    compose.onNodeWithTag("fullscreen_elapsed").performScrollTo().assertIsDisplayed()
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    compose.onNodeWithTag("fullscreen_elapsed").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertTrue(layouts.isNotEmpty())
                    assertTrue("enlarged clock text is not clipped", layouts.none { it.didOverflowHeight })
                    assertEquals(token, shownTarget())
                    focusedButton(R.string.timer_stop).performScrollTo().assertIsDisplayed()
                    focusedButton(R.string.timer_focused_edit).performScrollTo().assertIsDisplayed()
                    focusedButton(R.string.timer_exit_fullscreen).performScrollTo().assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun replacementRefusesDelayedDisplayedActionsAndOldEditorThenExternalStopExits() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            openFocused()
            lateinit var model: TimerModel
            scenario.onActivity { model = ViewModelProvider(it)[TimerViewModel::class.java].model }
            val old = fixture.controls() as TimerControls.Running
            focusedButton(R.string.timer_focused_edit).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.entry_form_edit_running)).assertExists()
            val replacementId = fixture.account.createEntry("Remote replacement", old.entry.start)
            runBlocking { app.accountSettings.refresh().join() }
            compose.waitUntil("replacement reaches shared model", 15_000) {
                (model.state.value as? TimerState.Running)?.entryId == replacementId
            }
            compose.runOnIdle { model.stopShown(old.target) }
            assertNull(runBlocking { model.runningForEditShown(old.target) })
            compose.onNodeWithText(context.getString(R.string.entry_save)).performScrollTo().performClick()
            // The save waits its turn in the queue, and the form ignores Back while it saves, so
            // Back goes only once the old editor has refused it.
            compose.waitUntil("the old editor refuses the save", 10_000) {
                compose.onAllNodesWithText(context.getString(R.string.entry_form_missing)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(replacementId, (fixture.controls() as TimerControls.Running).entry.id)
            assertEquals("Remote replacement", (fixture.controls() as TimerControls.Running).entry.description)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitFocused()
            compose.onNodeWithText("Remote replacement").assertExists()
            assertActions(running = true)
            val client = fixture.account.client
            client.send("PATCH", client.server.api("workspaces", fixture.account.workspaceId.toString(), "time_entries", replacementId.toString(), "stop"),
                buildJsonObject { put("stop", Instant.now().toString()) }, ServerTimeEntry.serializer()).orFail("external Stop")
            runBlocking { app.accountSettings.refresh().join() }
            awaitIdle()
            compose.awaitTimerPage()
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        }
    }

    @Test
    fun unsentRunningEditAndFocusedRecreationKeepTheLocalIdentity() {
        fixture.stop()
        fixture.drain()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            AirplaneMode.set(true, fixture.account.client)
            val started = fixture.start("Unsent focused entry")
            fixture.ageRunning()
            compose.waitUntil("local entry shown", 5_000) {
                compose.onAllNodesWithText("Unsent focused entry", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            openFocused()
            assertTrue(started.entry.id < 0)
            compose.onNodeWithText(context.getString(R.string.history_unsent)).assertExists()
            focusedButton(R.string.timer_focused_edit).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.entry_form_edit_running)).assertExists()
            scenario.recreate()
            compose.onNodeWithText(context.getString(R.string.entry_form_edit_running)).assertExists()
            compose.onNodeWithText(context.getString(R.string.entry_save)).performScrollTo().performClick()
            awaitFocused()
            assertEquals(started.entry.id, awaitRunning().entry.id)
            scenario.recreate()
            awaitFocused()
            assertEquals(started.entry.id, awaitRunning().entry.id)
            focusedButton(R.string.timer_exit_fullscreen).performScrollTo().performClick()
            compose.awaitTimerPage()
            assertEquals(started.entry.id, awaitRunning().entry.id)
        }
    }

    @Test
    fun focusedLocalTicksAndFrozenPauseAddNoRequests() {
        RequestCounter(TokklTestServer.url, " HTTP/1.1").use { requests ->
            ServerTrustStore(context).confirmCleartext(requests.url)
            Session(context).save(requests.url, fixture.account.apiToken)
            fixture.refresh()
            val clock = MutableClock(Instant.now())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[TimerViewModel::class.java].model
                    activity.setContent { TokklTheme { FullscreenTimerScreen(model, app.cache, {}, {}, clock) } }
                }
                awaitFocused()
                val beforeText = elapsedText()
                val beforeRequests = requests.count
                clock.at = clock.at.plusSeconds(2)
                compose.waitUntil("local focused tick", 5_000) { elapsedText() != beforeText }
                assertEquals(beforeRequests, requests.count)
                // Each start of the activity reads the server once (docs/PRD.md O2). Count what
                // one such refresh sends, so the trip below may add that and nothing for the clock.
                fixture.refresh()
                val refreshRequests = requests.count - beforeRequests
                val tripRequests = requests.count + refreshRequests
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                clock.at = clock.at.plusSeconds(10)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                compose.waitUntil("foreground catches up locally", 5_000) { parseDuration(elapsedText())!! >= parseDuration(beforeText)!! + 10 }
                compose.waitUntil("the foreground refresh is sent", 5_000) { requests.count >= tripRequests }
                assertEquals("the clock's catch-up adds no requests to the foreground refresh", tripRequests, requests.count)
                focusedButton(R.string.timer_pause).performScrollTo().performClick()
                val paused = awaitPaused()
                compose.waitUntil("paused display matches durable total", 5_000) { parseDuration(elapsedText()) == paused.seconds }
                fixture.drain()
                val frozenRequests = requests.count
                clock.at = clock.at.plusSeconds(3_600)
                compose.mainClock.advanceTimeBy(2_000)
                assertEquals(paused.seconds, parseDuration(elapsedText()))
                assertEquals("frozen display adds no requests", frozenRequests, requests.count)
                assertEquals(paused.target, shownTarget())
                assertTrue(requests.failures.isEmpty())
            }
            Session(context).save(TokklTestServer.url, fixture.account.apiToken)
        }
    }

    @Test
    fun externalStopAlsoClosesTheFocusedRunningEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            openFocused()
            focusedButton(R.string.timer_focused_edit).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.entry_form_edit_running)).assertExists()
            val row = (fixture.controls() as TimerControls.Running).entry
            val client = fixture.account.client
            client.send("PATCH", client.server.api("workspaces", fixture.account.workspaceId.toString(), "time_entries", row.id.toString(), "stop"),
                buildJsonObject { put("stop", Instant.now().toString()) }, ServerTimeEntry.serializer()).orFail("external Stop from editor")
            fixture.refresh()
            awaitIdle()
            compose.awaitTimerPage()
            compose.onNodeWithText(context.getString(R.string.entry_form_edit_running)).assertDoesNotExist()
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        }
    }

    private class MutableClock(@Volatile var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = at
    }

    private fun shownTarget(): TimerTarget = when (val state = fixture.controls()) {
        is TimerControls.Running -> state.target
        is TimerControls.Paused -> state.target
        TimerControls.Idle -> error("timer is idle")
    }
    private fun awaitRunning(): TimerControls.Running {
        compose.waitUntil("running durable state", 5_000) { fixture.controls() is TimerControls.Running }
        return fixture.controls() as TimerControls.Running
    }
    private fun awaitPaused(): TimerControls.Paused {
        compose.waitUntil("paused durable state", 5_000) { fixture.controls() is TimerControls.Paused }
        return fixture.controls() as TimerControls.Paused
    }
    private fun awaitIdle() {
        compose.waitUntil("idle durable state", 5_000) { fixture.controls() == TimerControls.Idle }
    }

    private fun chooseView(view: TimerView) {
        compose.onNode(isSelectable() and hasText(context.getString(view.label))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(isSelectable() and hasText(context.getString(view.label))).assertIsSelected()
    }
    private fun openFocused() {
        compose.onNodeWithText(context.getString(R.string.timer_open_fullscreen)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused()
    }
    private fun awaitFocused() {
        compose.waitUntil("fullscreen route", 5_000) { compose.onAllNodesWithTag("fullscreen_timer").fetchSemanticsNodes().size == 1 }
    }
    private fun focusedButton(label: Int) = compose.onNode(
        (if (label == R.string.timer_focused_edit) hasContentDescription(context.getString(label)) else hasText(context.getString(label))) and
            hasClickAction() and hasAnyAncestor(hasTestTag("fullscreen_timer")),
    )
    private fun assertActions(running: Boolean) {
        compose.waitUntil("shared focused controls settle", 5_000) {
            compose.onAllNodes(hasText(context.getString(if (running) R.string.timer_pause else R.string.timer_resume)) and hasClickAction()).fetchSemanticsNodes().size == 1
        }
        focusedButton(if (running) R.string.timer_pause else R.string.timer_resume).assertExists()
        compose.onNode(hasText(context.getString(if (running) R.string.timer_resume else R.string.timer_pause)) and hasClickAction()).assertDoesNotExist()
        focusedButton(R.string.timer_stop).assertExists()
        focusedButton(R.string.timer_focused_edit).assertExists()
        focusedButton(R.string.timer_exit_fullscreen).assertExists()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("fullscreen_timer"))).assertCountEquals(4)
    }
    private fun elapsedText(): String = compose.onNodeWithTag("fullscreen_elapsed").fetchSemanticsNode().config[SemanticsProperties.Text]
        .joinToString("") { it.text }
    private fun scrollPositions(): List<Float> = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() }
    private fun resumedActivity(): MainActivity? {
        var result: MainActivity? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return result
    }
}

class FullscreenTimerServerTest : FullscreenTimerAcceptanceTest(1f)
class FullscreenTimerLargeTextTest : FullscreenTimerAcceptanceTest(SystemFontScale.LARGEST)
