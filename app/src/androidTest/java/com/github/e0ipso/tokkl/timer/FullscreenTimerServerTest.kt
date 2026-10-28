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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Normal real-host acceptance at usual and largest font sizes on API31/36. The focused timer reads
 * the app's shared timer controls, so Pause, Resume and Stop while paused go to the server's
 * pause chain, and Start, Stop of a running entry and edits of it to the queue.
 */
abstract class FullscreenTimerAcceptanceTest(private val fontScale: Float) {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(SystemFontScale(fontScale)).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app
    private val context get() = fixture.context
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // The running entry starts on the server, so no queued setup change, and no refresh its send
    // starts without waiting, is still on its way when a test begins.
    @Before fun prepare() { fixture.prepare(); fixture.startOnServer("Pause fixture") }
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
            val original = (fixture.shown() as TimerControls.Running).entry
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
            assertEquals(original.id, (fixture.shown() as TimerControls.Running).entry.id)
            assertTrue((fixture.shown() as TimerControls.Running).entry.start < original.start)
            val first = elapsedText()
            compose.waitUntil("focused clock ticks locally", 5_000) { elapsedText() != first }
            // Offline, Pause needs the server: it says so, queues nothing and the entry runs on.
            val queued = runBlocking { app.changes.unsentCount.first() }
            assertTrue("the start edit waits in the queue", queued > 0)
            focusedButton(R.string.timer_pause).performScrollTo().performClick()
            awaitNotice(R.string.timer_action_offline)
            assertEquals(original.id, awaitRunning().entry.id)
            assertActions(running = true)
            assertEquals(queued, runBlocking { app.changes.unsentCount.first() })
            AirplaneMode.set(false, fixture.account.client)
            fixture.drain()
            // Online, the server pauses it and the paused fields are read-only.
            focusedButton(R.string.timer_pause).performScrollTo().performClick()
            val paused = awaitPaused()
            assertActions(running = false)
            compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("fullscreen_timer"))).assertCountEquals(0)
            val frozen = paused.seconds
            assertEquals(frozen, app.timerState.state.value!!.state.pause!!.seconds)
            assertEquals(frozen, parseDuration(elapsedText()))
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
            scenario.recreate()
            awaitFocused()
            assertEquals(frozen, parseDuration(elapsedText()))
            assertEquals(paused.target, shownTarget())
            // Offline, Resume and Stop while paused need the server too, and the pause stays as it was.
            AirplaneMode.set(true, fixture.account.client)
            focusedButton(R.string.timer_resume).performScrollTo().performClick()
            awaitNotice(R.string.timer_action_offline)
            assertEquals(paused, awaitPaused())
            focusedButton(R.string.timer_stop).performScrollTo().performClick()
            awaitNotice(R.string.timer_action_offline)
            assertEquals(paused, awaitPaused())
            assertActions(running = false)
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
            AirplaneMode.set(false, fixture.account.client)
            focusedButton(R.string.timer_resume).performScrollTo().performClick()
            val resumed = awaitRunning()
            assertActions(running = true)
            assertNotEquals(original.id, resumed.entry.id)
            assertEquals("Resume starts the fields kept at Pause", "Pause fixture", resumed.entry.description)
            assertEquals(frozen, resumed.accumulatedSeconds)
            assertTrue(parseDuration(elapsedText())!! >= frozen)
            assertEquals("Pause fixture", runBlocking { app.cache.timeEntries.first() }.first { it.id == original.id }.description)
            // A clone's Stop is an ordinary queued Stop, offline too, and the focused timer leaves.
            AirplaneMode.set(true, fixture.account.client)
            focusedButton(R.string.timer_stop).performScrollTo().performClick()
            awaitIdle()
            compose.awaitTimerPage()
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            assertTrue(runBlocking { app.changes.unsentCount.first() } > 0)
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
                    if (paused) {
                        focusedButton(R.string.timer_resume).performScrollTo().assertIsDisplayed()
                        focusedButton(R.string.timer_focused_edit).assertDoesNotExist()
                    } else {
                        focusedButton(R.string.timer_pause).performScrollTo().assertIsDisplayed()
                        focusedButton(R.string.timer_focused_edit).performScrollTo().assertIsDisplayed()
                    }
                    focusedButton(R.string.timer_exit_fullscreen).performScrollTo().assertIsDisplayed()
                }
            }
            // Stop while paused ends the chain on the server, and the focused timer leaves.
            focusedButton(R.string.timer_stop).performScrollTo().performClick()
            awaitIdle()
            compose.awaitTimerPage()
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            assertNull(app.timerState.state.value!!.state.pause)
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        }
    }

    @Test
    fun replacementRefusesDelayedDisplayedActionsAndOldEditorThenExternalStopExits() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            openFocused()
            lateinit var model: TimerModel
            scenario.onActivity { model = ViewModelProvider(it)[TimerViewModel::class.java].model }
            val old = fixture.shown() as TimerControls.Running
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
            assertEquals(replacementId, (fixture.shown() as TimerControls.Running).entry.id)
            assertEquals("Remote replacement", (fixture.shown() as TimerControls.Running).entry.description)
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
        holdTimerEventsOnTheServerItself()
        RequestCounter(TokklTestServer.url, " HTTP/1.1").use { requests ->
            ServerTrustStore(context).confirmCleartext(requests.url)
            Session(context).save(requests.url, fixture.account.apiToken)
            fixture.refresh()
            val clock = MutableClock(Instant.now())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[TimerViewModel::class.java].model
                    activity.setContent { TokklTheme { FullscreenTimerScreen(model, {}, {}, clock) } }
                }
                awaitFocused()
                // The activity's start refreshes once. Joining the refresh in flight, or running
                // one when it has ended, leaves none to come, so the counts below start settled.
                fixture.refresh()
                val running = fixture.shown() as TimerControls.Running
                val beforeText = elapsedText()
                val beforeRequests = requests.count
                clock.at = clock.at.plusSeconds(2)
                compose.waitUntil("local focused tick", 5_000) { elapsedText() != beforeText }
                assertEquals("a local tick adds no requests: ${requests.lines.drop(beforeRequests)}", beforeRequests, requests.count)
                // Each start of the activity reads the server once (docs/PRD.md O2). Count what
                // one such refresh sends, so the trip below may add that and nothing for the clock.
                fixture.refresh()
                val refreshRequests = requests.count - beforeRequests
                assertTrue("a refresh reads the server", refreshRequests > 0)
                val tripRequests = requests.count + refreshRequests
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                clock.at = clock.at.plusSeconds(10)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                compose.waitUntil("foreground catches up locally", 5_000) { parseDuration(elapsedText())!! >= parseDuration(beforeText)!! + 10 }
                compose.waitUntil("the foreground refresh is sent", 5_000) { requests.count >= tripRequests }
                assertEquals("the clock's catch-up adds no requests to the foreground refresh", tripRequests, requests.count)
                // Pause goes to the server and nowhere else. Its answer, the authoritative pause,
                // is what the phone holds and shows before anything is counted.
                val pauseRequests = requests.count
                val linesBeforePause = requests.lines.size
                focusedButton(R.string.timer_pause).performScrollTo().performClick()
                val paused = awaitPaused()
                val held = app.timerState.state.value!!.state
                val pause = held.pause!!
                assertTrue(pause.paused)
                assertEquals(pause.seconds, paused.seconds)
                assertEquals(TimerTarget.server(revision = held.revision), paused.target)
                assertEquals("Pause queued no Stop", 0, runBlocking { app.changes.unsentCount.first() })
                compose.waitUntil("paused display matches the server's total", 5_000) { parseDuration(elapsedText()) == paused.seconds }
                // The paused state took the running entry out of the cache, and the one refresh
                // that brings it back stopped writes the cache after its last request.
                compose.waitUntil("the refresh after Pause brings the entry back stopped", 10_000) {
                    fixture.rows().any { it.id == running.entry.id && it.stop != null }
                }
                val sent = requests.lines.drop(linesBeforePause)
                assertEquals("one Pause and one refresh: $sent", pauseRequests + 1 + refreshRequests, requests.count)
                assertEquals("one direct Pause: $sent", 1, sent.count { it.startsWith("POST /api/app/timer/pause ") })
                assertTrue("no queued Stop went out: $sent", sent.none { it.contains("/stop ") })
                val frozenRequests = requests.count
                clock.at = clock.at.plusSeconds(3_600)
                compose.mainClock.advanceTimeBy(2_000)
                assertEquals(paused.seconds, parseDuration(elapsedText()))
                assertEquals("frozen display adds no requests: ${requests.lines.drop(linesBeforePause)}", frozenRequests, requests.count)
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
            val row = (fixture.shown() as TimerControls.Running).entry
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

    /**
     * Opens the timer event stream afresh to the test server itself and waits for its first
     * state, while the stored server is still that server. The stream then stays open for the
     * test, and a relay stored later sees neither its request nor its events and keep-alives.
     * Nothing else accepts a state between the stop and the stream's first event.
     */
    private fun holdTimerEventsOnTheServerItself() {
        app.timerSync.stop()
        val stopped = Instant.now()
        app.timerSync.run(app.timerState)
        val first = runBlocking {
            withTimeoutOrNull(15_000) { app.timerState.state.first { it?.receivedAt?.isAfter(stopped) == true } }
        }
        assertNotNull("the timer event stream sent its first state", first)
    }

    private fun shownTarget(): TimerTarget = when (val state = fixture.shown()) {
        is TimerControls.Running -> state.target
        is TimerControls.Paused -> state.target
        TimerControls.Idle -> error("timer is idle")
    }
    private fun awaitRunning(): TimerControls.Running {
        compose.waitUntil("running durable state", 5_000) { fixture.shown() is TimerControls.Running }
        return fixture.shown() as TimerControls.Running
    }
    private fun awaitPaused(): TimerControls.Paused {
        compose.waitUntil("paused durable state", 5_000) { fixture.shown() is TimerControls.Paused }
        return fixture.shown() as TimerControls.Paused
    }
    private fun awaitIdle() {
        compose.waitUntil("idle durable state", 5_000) { fixture.shown() == TimerControls.Idle }
    }

    /** Waits until the timer's notice [message] shows, then until it has gone, so the next one is new. */
    private fun awaitNotice(message: Int) {
        val text = context.getString(message)
        compose.waitUntil("the notice \"$text\" shows", 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil("the notice \"$text\" goes", 15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
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

    /**
     * The focused timer's actions: Pause, Stop, Edit and Exit while running, and Resume, Stop and
     * Exit while paused, when the paused fields are read-only and Edit is hidden.
     */
    private fun assertActions(running: Boolean) {
        compose.waitUntil("shared focused controls settle", 5_000) {
            compose.onAllNodes(hasText(context.getString(if (running) R.string.timer_pause else R.string.timer_resume)) and hasClickAction()).fetchSemanticsNodes().size == 1
        }
        focusedButton(if (running) R.string.timer_pause else R.string.timer_resume).assertExists()
        compose.onNode(hasText(context.getString(if (running) R.string.timer_resume else R.string.timer_pause)) and hasClickAction()).assertDoesNotExist()
        focusedButton(R.string.timer_stop).assertExists()
        if (running) focusedButton(R.string.timer_focused_edit).assertExists() else focusedButton(R.string.timer_focused_edit).assertDoesNotExist()
        focusedButton(R.string.timer_exit_fullscreen).assertExists()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("fullscreen_timer"))).assertCountEquals(if (running) 4 else 3)
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
