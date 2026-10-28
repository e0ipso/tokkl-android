package com.github.e0ipso.tokkl.timerstate

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.BackgroundTrip
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TimerActionToasts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.awaitControls
import com.github.e0ipso.tokkl.awaitPomodoroOffered
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.notification.action
import com.github.e0ipso.tokkl.notification.stopAction
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.pomodoro.PomodoroAction
import com.github.e0ipso.tokkl.pomodoro.PomodoroLengths
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseKey
import com.github.e0ipso.tokkl.pomodoro.PomodoroProblem
import com.github.e0ipso.tokkl.pomodoro.PomodoroRingingActionReceiver
import com.github.e0ipso.tokkl.pomodoro.PomodoroScreenModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionActionReceiver
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.pomodoro.pomodoroAlarmIntent
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.widget.NativeWidget
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Without a connection, each of the six timer actions that need the server, Pause, Resume and
 * Discard of the pause chain and Start, Next phase and End of a Pomodoro session, fails with the
 * offline message on the surfaces the user taps it on, and leaves the phone as it was: the same
 * held timer state and nothing queued. The surfaces here are the app's Timer and Pomodoro pages,
 * the running notification, the widget, the session notification, the ringing notification and
 * the full-screen alarm; the tile and the reminder are covered by RunningTileServerTest and
 * ReminderServerTest. Ordinary Start and Stop, a resumed clone's Stop among them, still change
 * the phone at once and go through the queue to the real server.
 *
 * The phone runs in airplane mode, so no request reaches the server. Another device of the same
 * user sets up and reads the server's timer, as the web app would.
 */
class TimerActionsOfflineServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = fixture.app
    private val device = UiDevice.getInstance(instrumentation)
    private val other get() = fixture.account.otherDevice
    private val offline get() = context.getString(TimerActionResult.Offline.message)

    @Before
    fun prepare() {
        fixture.prepare()
        RunningNotifications.allow()
    }

    @After
    fun cleanup() {
        PomodoroSessionActionReceiver.completed = null
        PomodoroRingingActionReceiver.completed = null
        try {
            TimerSurfaces.closeAlarms()
            AirplaneMode.set(false, fixture.account.client)
            val left = other.timerState()
            if (left.pomodoro != null) postTimer("pomodoro", "end", revision = left.revision)
            if (left.pause?.paused == true) postTimer("timer", "discard", revision = left.revision)
            // Read it at once rather than wait for the stream to reconnect after airplane mode.
            runBlocking { app.timerActions.read() }
            runBlocking { withTimeout(15_000) { app.pomodoroRinging.state.first { !it.serviceRunning } } }
        } finally {
            fixture.cleanup()
        }
    }

    /**
     * Pause offline on the Timer page and the running notification, then Resume and Discard of a
     * chain the other device paused, on the Timer page, the running notification and the widget.
     */
    @Test
    fun pauseResumeAndDiscardFailOfflineOnThePageTheNotificationAndTheWidget() {
        val entry = otherStarts("Offline chain")
        fixture.refresh()
        var pausedOffline: Snapshot? = null
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            app.awaitControls<TimerControls.Running>("the other device's entry") { it.entry.id == entry && it.pausable }
            AirplaneMode.set(true, fixture.account.client)
            val running = snapshot()

            tapOnTheTimerPage(R.string.timer_pause_spoken)
            awaitNotice()
            assertUnchanged("Pause on the Timer page", running)
            toast("Pause on the notification") { TimerSurfaces.runningNotification(ongoing = true).action(R.string.notification_pause).actionIntent.send() }
            assertUnchanged("Pause on the notification", running)
            assertEquals(entry, (shown() as TimerControls.Running).entry.id)

            // The other device pauses; the phone learns of it once it is back online.
            AirplaneMode.set(false, fixture.account.client)
            val pausedThere = postTimer("timer", "pause")
            app.awaitControls<TimerControls.Paused>("the other device's Pause") { it.target.revision == pausedThere.revision }
            AirplaneMode.set(true, fixture.account.client)
            val paused = snapshot()
            pausedOffline = paused

            tapOnTheTimerPage(R.string.timer_resume_spoken)
            awaitNotice()
            assertUnchanged("Resume on the Timer page", paused)
            tapOnTheTimerPage(R.string.timer_stop_spoken)
            awaitNotice()
            assertUnchanged("Stop while paused on the Timer page", paused)
            toast("Resume on the notification") { TimerSurfaces.runningNotification(ongoing = false).action(R.string.notification_resume).actionIntent.send() }
            assertUnchanged("Resume on the notification", paused)
            toast("Stop while paused on the notification") { TimerSurfaces.runningNotification(ongoing = false).stopAction().actionIntent.send() }
            assertUnchanged("Stop while paused on the notification", paused)
        }

        val paused = checkNotNull(pausedOffline)
        NativeWidget().use { widget ->
            widget.awaitDescription(context.getString(R.string.widget_resume_description))
            toast("Resume on the widget") { widget.click(context.getString(R.string.widget_resume_description)) }
            assertUnchanged("Resume on the widget", paused)
            toast("Stop while paused on the widget") { widget.click(context.getString(R.string.widget_stop_description)) }
            assertUnchanged("Stop while paused on the widget", paused)
            widget.awaitDescription(context.getString(R.string.widget_resume_description))
        }

        AirplaneMode.set(false, fixture.account.client)
        val server = other.timerState()
        assertEquals("no action reached the server", paused.state?.revision, server.revision)
        assertTrue("the chain is still paused there", server.pause?.paused == true)
    }

    /**
     * Stop of a running clone is the ordinary Stop: offline it changes the phone at once and
     * queues a v9 stop, and once sent the server ends the chain. Plain Start and Stop offline do
     * the same.
     */
    @Test
    fun aClonesStopAndPlainStartAndStopChangeThePhoneAtOnceAndGoThroughTheQueue() {
        val first = otherStarts("Chain to stop")
        fixture.refresh()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            app.awaitControls<TimerControls.Running>("the other device's entry") { it.entry.id == first && it.pausable }
            tapOnTheTimerPage(R.string.timer_pause_spoken)
            val paused = app.awaitControls<TimerControls.Paused>("the phone's Pause")
            val resumed = postTimer("timer", "resume", revision = other.timerState().revision)
            val clone = checkNotNull(resumed.pause?.cloneEntryId)
            app.awaitControls<TimerControls.Running>("the clone") { it.entry.id == clone && it.accumulatedSeconds == paused.seconds }
            assertEquals(0, unsent())

            AirplaneMode.set(true, fixture.account.client)
            tapOnTheTimerPage(R.string.timer_stop_spoken)
            app.awaitControls<TimerControls.Idle>("the clone's Stop, at once", timeoutMs = AT_ONCE_MS)
            assertEquals("the Stop waits in the queue", 1, unsent())
            assertEquals("the server has not heard of it", clone, held()?.pause?.cloneEntryId)
            assertNoNotice()

            // Only the queue sends: the tap sent nothing, as the phone was offline. Its v9 stop
            // stops the clone, and the server ends the chain with it.
            AirplaneMode.set(false, fixture.account.client)
            fixture.drain()
            val server = other.timerState()
            assertNull("the server ended the chain", server.pause)
            assertNull("the clone no longer runs there", server.runningEntry)
            assertEquals(TimerControls.Idle, shown())

            AirplaneMode.set(true, fixture.account.client)
            tapOnTheTimerPage(R.string.timer_start_spoken)
            val started = app.awaitControls<TimerControls.Running>("Start, at once", timeoutMs = AT_ONCE_MS)
            assertEquals(1, unsent())
            assertFalse("a local entry is no clone", started.entry.id == clone)
            tapOnTheTimerPage(R.string.timer_stop_spoken)
            app.awaitControls<TimerControls.Idle>("Stop, at once", timeoutMs = AT_ONCE_MS)
            assertEquals(2, unsent())
            assertNoNotice()
            AirplaneMode.set(false, fixture.account.client)
            fixture.drain()
            assertNull("the offline Stop reached the server", other.timerState().runningEntry)
        }
    }

    /**
     * Start session offline on the Pomodoro page, then Next phase and End of a running session on
     * the page and the session notification, and at the phase's end on the ringing notification
     * and the full-screen alarm, which keep ringing.
     */
    @Test
    fun sessionStartNextAndEndFailOfflineOnThePageTheNotificationsAndTheAlarm() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            compose.awaitPomodoroOffered(15_000)
            compose.openBarPage(R.string.pomodoro_title)
            var model: PomodoroScreenModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[PomodoroViewModel::class.java].model }
            val page = checkNotNull(model)
            compose.waitUntil("the page has loaded", 15_000) { !page.state.value.loading }
            compose.runOnIdle {
                page.editLengths(PomodoroLengths(work = "1", shortRest = "1", longRest = "1", every = "4"))
                page.editFields(EntryFields("Offline session"), entryId = null)
            }

            AirplaneMode.set(true, fixture.account.client)
            val idle = snapshot()
            tapOnThePage(R.string.pomodoro_start)
            awaitProblem(page, PomodoroAction.START)
            assertUnchanged("Start session on the page", idle)
            assertNull(page.state.value.pomodoro)

            AirplaneMode.set(false, fixture.account.client)
            tapOnThePage(R.string.pomodoro_start)
            compose.waitUntil("the server started the session", 15_000) { page.state.value.pomodoro != null && !page.state.value.busy }
            val session = checkNotNull(other.timerState().pomodoro)
            compose.waitUntil("the session notification", 10_000) { TimerSurfaces.sessionNotification() != null }

            AirplaneMode.set(true, fixture.account.client)
            val work = snapshot()
            tapOnThePage(R.string.pomodoro_start_short_rest)
            awaitProblem(page, PomodoroAction.NEXT)
            assertUnchanged("Next phase on the page", work)
            tapOnThePage(R.string.pomodoro_end)
            awaitProblem(page, PomodoroAction.END)
            assertUnchanged("End session on the page", work)

            val shown = checkNotNull(TimerSurfaces.sessionNotification()).notification
            sessionAction("Next phase on the session notification") { shown.actions[0].actionIntent.send() }
            assertUnchanged("Next phase on the session notification", work)
            sessionAction("End session on the session notification") { shown.actions[1].actionIntent.send() }
            assertUnchanged("End session on the session notification", work)
            BackgroundTrip.leave()

            // The phase ends while the phone is offline, and it rings from the state it holds.
            val phase = PomodoroPhaseKey(session.phase.start)
            val untilEnd = Duration.between(Instant.now(), session.phase.plannedEnd).toMillis().coerceAtLeast(0)
            val ringing = runBlocking {
                withTimeout(untilEnd + 30_000) { app.pomodoroRinging.state.first { it.serviceRunning && it.playing && it.ring?.phase == phase } }
            }
            compose.waitUntil("the ringing notification", 10_000) { TimerSurfaces.ringingNotification() != null }
            val ring = checkNotNull(TimerSurfaces.ringingNotification())
            ringAction("Next phase on the ringing notification") { ring.actions[0].actionIntent.send() }
            assertStillRings(ringing.audioSessionId)
            ringAction("End session on the ringing notification") { ring.actions[1].actionIntent.send() }
            assertStillRings(ringing.audioSessionId)
            assertUnchanged("the ringing notification", work)

            alarmAction(phase, R.string.pomodoro_alarm_next)
            assertStillRings(ringing.audioSessionId)
            alarmAction(phase, R.string.pomodoro_alarm_end)
            assertStillRings(ringing.audioSessionId)
            assertUnchanged("the full-screen alarm", work)

            AirplaneMode.set(false, fixture.account.client)
            val server = other.timerState()
            assertEquals("no action reached the server", work.state?.revision, server.revision)
            assertEquals(session, server.pomodoro)
        }
    }

    /** What the offline actions must leave as it is: the held state, its clock aside, and the queue. */
    private data class Snapshot(val state: TimerState?, val unsent: Int)

    private fun snapshot() = Snapshot(held()?.withoutClock(), unsent())

    private fun assertUnchanged(what: String, before: Snapshot) {
        assertEquals("$what: the phone holds the same timer", before.state, held()?.withoutClock())
        assertEquals("$what: nothing was queued", before.unsent, unsent())
    }

    private fun TimerState.withoutClock() = copy(serverTime = Instant.EPOCH)

    private fun held(): TimerState? = app.timerState.state.value?.state

    private fun shown(): TimerControls = runBlocking { app.timerControls.first() }

    private fun unsent(): Int = runBlocking { app.changes.unsentCount.first() }

    /** The other device starts [description] ten minutes ago. Returns its id. */
    private fun otherStarts(description: String): Long {
        val workspace = fixture.account.workspaceId
        return other.send("POST", other.server.api("workspaces", "$workspace", "time_entries"), buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "tokkl-android tests, another device")
            put("description", description)
            put("start", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS).toString())
            put("duration", -1)
        }, JsonObject.serializer()).orFail("POST a running entry").getValue("id").jsonPrimitive.long
    }

    private fun tapOnTheTimerPage(spoken: Int) {
        val button = hasContentDescription(context.getString(spoken)) and hasClickAction()
        compose.waitUntil("the Timer page offers ${context.getString(spoken)}", 5_000) { compose.onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(button).performClick()
    }

    private fun tapOnThePage(label: Int) {
        compose.onNode(hasText(context.getString(label)) and hasClickAction()).performScrollTo().performClick()
    }

    /** Waits until the Timer page's notice says the phone is offline, then until it has gone. */
    private fun awaitNotice() {
        compose.waitUntil("the offline notice shows", 10_000) { compose.onAllNodesWithText(offline).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil("the offline notice goes", 15_000) { compose.onAllNodesWithText(offline).fetchSemanticsNodes().isEmpty() }
    }

    private fun assertNoNotice() {
        compose.waitForIdle()
        assertTrue("an ordinary Stop says nothing", compose.onAllNodesWithText(offline).fetchSemanticsNodes().isEmpty())
    }

    /** Waits until the Pomodoro page says [action] could not reach the server, and shows it. */
    private fun awaitProblem(page: PomodoroScreenModel, action: PomodoroAction) {
        compose.waitUntil("$action fails offline", 15_000) { !page.state.value.busy && page.state.value.problem != null }
        assertEquals(PomodoroProblem(action, TimerActionResult.Offline), page.state.value.problem)
        compose.onNodeWithText(offline).performScrollTo().assertIsDisplayed()
    }

    private fun toast(what: String, action: () -> Unit) {
        try {
            TimerActionToasts.await(TimerActionResult.Offline.message, action)
        } catch (e: AssertionError) {
            throw AssertionError("$what: ${e.message}", e)
        }
    }

    private fun sessionAction(what: String, action: () -> Unit) {
        val done = CountDownLatch(1)
        PomodoroSessionActionReceiver.completed = done
        try {
            toast(what, action)
            assertTrue("$what finished", done.await(15, TimeUnit.SECONDS))
        } finally {
            PomodoroSessionActionReceiver.completed = null
        }
    }

    private fun ringAction(what: String, action: () -> Unit) {
        val done = CountDownLatch(1)
        PomodoroRingingActionReceiver.completed = done
        try {
            toast(what, action)
            assertTrue("$what finished", done.await(15, TimeUnit.SECONDS))
        } finally {
            PomodoroRingingActionReceiver.completed = null
        }
    }

    /**
     * Opens a new full-screen alarm for [phase], taps [label] on it and waits until it says the
     * phone is offline, then leaves it with Back. Each tap gets a screen of its own, so the message
     * it waits for cannot be the previous tap's.
     */
    private fun alarmAction(phase: PomodoroPhaseKey, label: Int) {
        device.wakeUp()
        context.startActivity(pomodoroAlarmIntent(context, phase))
        val alarm = TimerSurfaces.awaitAlarm()
        compose.waitUntil("the alarm shows the phase", 10_000) { alarm.state.value.ring?.phase == phase }
        assertNull("a new alarm screen shows no message yet", alarm.state.value.problem)
        compose.onNode(TimerSurfaces.onTheAlarm(hasText(context.getString(label)) and hasClickAction())).performClick()
        compose.waitUntil("${context.getString(label)} on the alarm fails offline", 15_000) {
            !alarm.state.value.busy && alarm.state.value.problem != null
        }
        assertEquals(TimerActionResult.Offline, alarm.state.value.problem)
        compose.onNode(TimerSurfaces.onTheAlarm(hasText(offline))).assertIsDisplayed()
        for (action in listOf(R.string.pomodoro_alarm_next, R.string.pomodoro_alarm_end)) {
            compose.onNode(TimerSurfaces.onTheAlarm(hasText(context.getString(action)) and hasClickAction())).assertIsEnabled()
        }
        device.pressBack()
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!TimerSurfaces.noOpenAlarm()) {
            assertTrue("Back left the alarm screen", SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(50)
        }
    }

    private fun assertStillRings(audioSession: Int?) {
        val state = app.pomodoroRinging.state.value
        assertTrue("it still rings", state.serviceRunning && state.playing && state.vibrationRequested)
        assertEquals("the same ring goes on", audioSession, state.audioSessionId)
    }

    /** A timer action from the other device, which must succeed. */
    private fun postTimer(vararg path: String, revision: Long? = null): TimerState =
        other.send("POST", other.server.app(*path), buildJsonObject { if (revision != null) put("revision", revision) }, TimerState.serializer())
            .orFail("POST /api/app/${path.joinToString("/")}")

    private fun com.github.e0ipso.tokkl.api.TogglApiClient.timerState(): TimerState =
        get(server.app("timer"), TimerState.serializer()).orFail("GET /api/app/timer")

    private companion object {
        /** A Start or Stop shows on the phone without waiting for any network. */
        const val AT_ONCE_MS = 1_000L

    }
}
