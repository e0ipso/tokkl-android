package com.github.e0ipso.tokkl.timerstate

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.BackgroundTrip
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TimerActionToasts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitControls
import com.github.e0ipso.tokkl.awaitPomodoroOffered
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.seconds
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.notification.action
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.pomodoro.PomodoroLengths
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseKey
import com.github.e0ipso.tokkl.pomodoro.PomodoroRing
import com.github.e0ipso.tokkl.pomodoro.PomodoroScreenModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroRingingActionReceiver
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotifier
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.pomodoro.pomodoroAlarmIntent
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.widget.NativeWidget
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The phone and another device of the same user share one timer on the real Tokkl 1.10.0 server
 * (plan 02, success criteria 1 to 4). The phone signs in with the account's API token. The other
 * device, which stands in for the web app or a desktop client, signs in with the account's email
 * and password and calls the API straight; its answers are what the assertions compare with.
 * Between them there is only the server and the phone's timer event stream: the phone runs in the
 * background, so no foreground refresh reads the state for it.
 *
 * Arrival and silencing are timed on the monotonic clock from the moment the other device's
 * request returned, and must take at most [AT_ONCE_MS]. The sessions are real one-minute phases,
 * so the phone rings at the server's planned end, not at a state the test made up.
 */
class TimerCrossDeviceServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val other get() = fixture.account.otherDevice
    private var signedInToTheMainServer = false

    @Before
    fun signInAndAllowNotifications() {
        RunningNotifications.allow()
    }

    @After
    fun cleanup() {
        PomodoroRingingActionReceiver.completed = null
        try {
            TimerSurfaces.closeAlarms()
            if (signedInToTheMainServer) {
                // End what the test left on the server, so the ring stops before the account goes.
                val left = other.timerState()
                if (left.pomodoro != null) postTimer("pomodoro", "end", revision = left.revision)
                if (left.pause?.paused == true) postTimer("timer", "discard", revision = left.revision)
                // Read it at once, also where the test held the stream back.
                runBlocking { app.timerActions.read() }
                runBlocking { withTimeout(15_000) { app.pomodoroRinging.state.first { !it.serviceRunning } } }
            }
        } finally {
            if (signedInToTheMainServer) fixture.cleanup() else SessionRule.signOut()
        }
    }

    /**
     * Criteria 1 and 2: a session the other device starts reaches the phone in the background
     * within 2 s with the server's phase, countdown and number; the phone rings at the server's
     * planned end; the other device's Next phase and End session each stop the ring and close the
     * alarm screen within 2 s.
     */
    @Test
    fun aSessionFromAnotherDeviceReachesTheBackgroundedPhoneAndItsNextAndEndSilenceTheRing() {
        prepareTheMainServer()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            awaitTimerSyncService()
            BackgroundTrip.leave()
            awaitTheStream()

            val started = postTimer("pomodoro", body = sessionBody("Shared session", every = 2))
            val sent = SystemClock.elapsedRealtime()
            val session = checkNotNull(started.pomodoro)
            within("the remote session reaches the backgrounded phone", sent,
                "held" to { held()?.let { it.revision >= started.revision && it.pomodoro?.phase?.start == session.phase.start } == true },
                "notification" to { TimerSurfaces.sessionNotification()?.title() == sessionTitle(R.string.pomodoro_session_work, 1, 2) },
            )
            val held = checkNotNull(app.timerState.state.value)
            assertEquals("the phone holds the server's session", session, held.state.pomodoro)
            assertEquals(PhaseKind.WORK, session.phase.kind)
            assertEquals(1, session.number)
            assertEquals(2, session.longRestEvery)
            assertCountsDownTo(checkNotNull(TimerSurfaces.sessionNotification()).notification, session, held, R.string.pomodoro_short_rest)

            // The server's one-minute work phase ends, and the phone rings from its planned end.
            val work = awaitRingAtItsEnd(session)
            openAlarm(work)

            val next = postTimer("pomodoro", "next", revision = other.timerState().revision)
            val confirmed = SystemClock.elapsedRealtime()
            val rest = checkNotNull(next.pomodoro)
            assertEquals(PhaseKind.SHORT_REST, rest.phase.kind)
            within("the other device's Next phase silences the phone", confirmed,
                "held" to { held()?.pomodoro?.phase?.start == rest.phase.start },
                "ring stopped" to { ringStopped() },
                "alarm closed" to { TimerSurfaces.noOpenAlarm() },
            )
            awaitSessionNotification(sessionTitle(R.string.pomodoro_session_short_rest, 1, 2))

            // The short rest ends too, and End session from the other device ends the ring.
            awaitRingAtItsEnd(rest)
            openAlarm(PomodoroPhaseKey(rest.phase.start))
            val ended = postTimer("pomodoro", "end", revision = other.timerState().revision)
            val stopped = SystemClock.elapsedRealtime()
            assertNull(ended.pomodoro)
            within("the other device's End session silences the phone", stopped,
                "held" to { held()?.let { it.revision >= ended.revision && it.pomodoro == null } == true },
                "ring stopped" to { ringStopped() },
                "alarm closed" to { TimerSurfaces.noOpenAlarm() },
            )
            awaitNoSessionNotification()
            assertNull("the server has no session", other.timerState().pomodoro)
        }
    }

    /**
     * Criteria 2 and 3: a session started on the phone's Pomodoro page shows through the API with
     * the page's fields and lengths. At its end the phone rings. The other device confirms Next
     * phase while the phone's stream has not delivered it yet, and the ringing notification's Next
     * phase, drawn at the older revision, gets the server's refusal: it advances nothing, the
     * phone takes the server's state from the refusal, the ring stops and the phone says so.
     */
    @Test
    fun aSessionFromThePhoneShowsThroughTheApiAndAStaleNextFromTheRingIsRefused() {
        prepareTheMainServer()
        val workspace = fixture.account.workspaceId
        val project = other.send("POST", other.server.api("workspaces", "$workspace", "projects"),
            buildJsonObject { put("name", "Shared project") }, JsonObject.serializer()).orFail("POST a project")
            .getValue("id").jsonPrimitive.long
        other.send("POST", other.server.api("workspaces", "$workspace", "tags"),
            buildJsonObject { put("name", "shared") }, JsonObject.serializer()).orFail("POST a tag")
        fixture.refresh()
        val fields = EntryFields("Phone session", projectId = project, tags = listOf("shared"), billable = true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            compose.awaitPomodoroOffered(15_000)
            compose.openBarPage(R.string.pomodoro_title)
            var model: PomodoroScreenModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[PomodoroViewModel::class.java].model }
            val page = checkNotNull(model)
            compose.waitUntil("the page has loaded", 15_000) { !page.state.value.loading }
            compose.runOnIdle {
                page.editLengths(PomodoroLengths(work = "1", shortRest = "2", longRest = "3", every = "3"))
                page.editFields(fields, entryId = null)
            }
            compose.onNode(hasText(context.getString(R.string.pomodoro_start)) and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil("the server started the session", 15_000) { page.state.value.pomodoro != null && !page.state.value.busy }

            val server = other.timerState()
            val session = checkNotNull(server.pomodoro)
            assertEquals("the page's lengths", listOf(1, 2, 3, 3), listOf(session.workMinutes, session.shortRestMinutes, session.longRestMinutes, session.longRestEvery))
            val entry = checkNotNull(server.runningEntry)
            assertEquals("the open pomodoro is the running entry", entry.id, session.entryId)
            assertEquals(fields.description, entry.description)
            assertEquals(fields.projectId, entry.projectId)
            assertEquals(fields.tags, entry.tags)
            assertEquals(true, entry.billable)
            assertEquals(workspace, entry.workspaceId)
            assertEquals("the phone holds what the other device reads", server.revision, held()?.revision)

            BackgroundTrip.leave()
            val ring = awaitRingAtItsEnd(session)
            val drawn = checkNotNull(held())
            val next = checkNotNull(TimerSurfaces.ringingNotification()).actions.single { it.title.toString() == context.getString(R.string.pomodoro_session_next) }

            // The stream has not brought the other device's change yet, as on a slow network.
            app.timerSync.stop()
            val current = other.timerState().revision
            assertEquals("the ring shows the server's revision", current, drawn.revision)
            val moved = postTimer("pomodoro", "next", revision = current)
            val rest = checkNotNull(moved.pomodoro)
            assertEquals(PhaseKind.SHORT_REST, rest.phase.kind)
            assertEquals("the phone has not heard of it", drawn, held())
            assertTrue("the phone still rings the work phase", app.pomodoroRinging.state.value.playing)

            val done = CountDownLatch(1)
            PomodoroRingingActionReceiver.completed = done
            TimerActionToasts.await(TimerActionResult.Refused.message) { next.actionIntent.send() }
            assertTrue("the ringing Next phase finished", done.await(15, TimeUnit.SECONDS))

            val after = other.timerState()
            assertEquals("the refused Next advanced nothing", moved.revision, after.revision)
            assertEquals(rest, after.pomodoro)
            val adopted = checkNotNull(held())
            assertEquals("the phone took the server's state from the refusal", moved.revision, adopted.revision)
            assertEquals(rest, adopted.pomodoro)
            assertNotEquals(ring.phase, PomodoroPhaseKey(rest.phase.start))
            runBlocking { withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning && !it.playing } } }
            awaitSessionNotification(sessionTitle(R.string.pomodoro_session_short_rest, 1, 3))
            app.followTimerSync()
        }
    }

    /**
     * Criterion 3: Pause and Resume work in both directions on one chain, with the same paused
     * total on the phone and the server, and Discard on the other device clears the paused chain
     * on the phone. The phone acts through its running notification, with the app in the
     * background.
     */
    @Test
    fun pauseResumeAndDiscardFromEitherDeviceShareOneChainAndOneTotal() {
        prepareTheMainServer()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            awaitTimerSyncService()
            BackgroundTrip.leave()
            val first = awaitTheStream(startedSecondsAgo = 600)

            // The phone pauses; the other device reads the same frozen total.
            TimerSurfaces.runningNotification(ongoing = true).action(R.string.notification_pause).actionIntent.send()
            val paused = app.awaitControls<TimerControls.Paused>("the phone's Pause")
            val chain = checkNotNull(other.timerState().pause)
            assertTrue(chain.paused)
            assertEquals("the server's frozen total", chain.seconds, paused.seconds)
            assertEquals(chain, held()?.pause)

            // The other device resumes; the phone counts on from the same total.
            val resumed = postTimer("timer", "resume", revision = other.timerState().revision)
            val sent = SystemClock.elapsedRealtime()
            val clone = checkNotNull(resumed.pause?.cloneEntryId)
            within("the other device's Resume reaches the phone", sent,
                "held" to { held()?.pause?.cloneEntryId == clone },
            )
            val running = app.awaitControls<TimerControls.Running>("the clone runs on the phone") { it.entry.id == clone }
            assertNotEquals("Resume starts a new entry", first, clone)
            assertEquals(chain.seconds, running.accumulatedSeconds)
            assertChainTotalsAgree()

            // The other device pauses; the phone shows the server's new total and resumes it.
            val pausedThere = postTimer("timer", "pause")
            val secondTotal = checkNotNull(pausedThere.pause).seconds
            assertTrue("the clone's time was added", secondTotal >= chain.seconds)
            val again = app.awaitControls<TimerControls.Paused>("the other device's Pause") { it.seconds == secondTotal }
            TimerSurfaces.runningNotification(ongoing = false).action(R.string.notification_resume).actionIntent.send()
            val phoneClone = app.awaitControls<TimerControls.Running>("the phone's Resume") { it.accumulatedSeconds == again.seconds }
            val there = checkNotNull(other.timerState().pause)
            assertFalse(there.paused)
            assertEquals("the other device sees the phone's clone", phoneClone.entry.id, there.cloneEntryId)
            assertEquals(secondTotal, there.seconds)
            assertChainTotalsAgree()

            // The phone pauses, and Discard on the other device ends the chain on the phone.
            TimerSurfaces.runningNotification(ongoing = true).action(R.string.notification_pause).actionIntent.send()
            app.awaitControls<TimerControls.Paused>("the phone's second Pause")
            val discarded = postTimer("timer", "discard", revision = other.timerState().revision)
            assertNull(discarded.pause)
            app.awaitControls<TimerControls.Idle>("the other device's Discard")
            assertNull(held()?.pause)
            RunningNotifications.awaitGone("the notification after Discard elsewhere")
            assertEquals("nothing went through the queue", 0, runBlocking { app.changes.unsentCount.first() })
        }
    }

    /**
     * Criterion 4: on Tokkl 1.8.1, which has no timer API, the bar and the rail offer no Pomodoro,
     * and the Timer page, the full-screen timer, the running notification and the widget offer no
     * Pause, while Start and Stop still reach that server. The timer event stream does not run.
     */
    @Test
    @SignedOut
    fun anOlderTokklWithoutTheTimerApiHidesPomodoroAndPauseWhileStartAndStopWork() {
        val legacy = TogglApiClient(TokklTestServer.legacyUrl, ApiCredentials.ApiToken(TokklTestServer.legacyApiToken), httpClient = OkHttpClient())
        ServerTrustStore(context).confirmCleartext(TokklTestServer.legacyUrl)
        Session(context).save(TokklTestServer.legacyUrl, TokklTestServer.legacyApiToken)
        runBlocking { app.accountSettings.refresh().join() }
        app.storeSignIn(TokklTestServer.legacyUrl, TokklTestServer.legacyApiToken, checkNotNull(app.accountSettings.state.value))
        runBlocking { app.accountSettings.refresh().join() }
        val flags = app.capabilities.state.value
        assertFalse("1.8.1 has no timer API", flags.timerState)
        assertTrue("it is still a Tokkl", flags.favorites && flags.trash)
        assertNull("no timer state is held", app.timerState.state.value)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage()
            assertNull("the timer event stream does not run", app.timerSyncService)
            assertNoPomodoroItem("bar")
            val start = hasContentDescription(context.getString(R.string.timer_start_spoken)) and hasClickAction()
            compose.onNode(start).performClick()
            val running = app.awaitControls<TimerControls.Running>("the phone's Start", timeoutMs = 2_000)
            assertFalse("Pause is not offered", running.pausable)
            compose.waitUntil("the Timer page shows Stop", 5_000) {
                compose.onAllNodes(hasContentDescription(context.getString(R.string.timer_stop_spoken))).fetchSemanticsNodes().isNotEmpty()
            }
            assertNoPause("the Timer page")
            compose.onNodeWithText(context.getString(R.string.timer_open_fullscreen)).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil("the full-screen timer", 5_000) { compose.onAllNodes(hasTestTag("fullscreen_timer")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodes(focused(R.string.timer_stop)).fetchSemanticsNodes().isNotEmpty())
            assertTrue("the full-screen timer offers no Pause", compose.onAllNodes(focused(R.string.timer_pause)).fetchSemanticsNodes().isEmpty())
            compose.onNode(focused(R.string.timer_exit_fullscreen)).performScrollTo().performClick()
            compose.awaitTimerPage()

            // A sideways phone draws the rail, which offers no Pomodoro either.
            val before = resumed<MainActivity>()
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil("the activity turns sideways", 15_000) {
                resumed<MainActivity>()?.let { it !== before && it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE } == true
            }
            compose.awaitTimerPage()
            val items = compose.onAllNodes(bottomBarItem(R.string.nav_timer)).fetchSemanticsNodes() +
                compose.onAllNodes(bottomBarItem(R.string.settings)).fetchSemanticsNodes()
            assertEquals("the rail's items share a centre", items[0].boundsInRoot.center.x, items[1].boundsInRoot.center.x, 2f)
            assertTrue("the rail's items run from the top", items[0].boundsInRoot.bottom <= items[1].boundsInRoot.top + 2f)
            assertNoPomodoroItem("rail")
            assertNoPause("the Timer page beside the rail")
            val sideways = resumed<MainActivity>()
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil("the activity turns upright", 15_000) {
                resumed<MainActivity>()?.let { it !== sideways && it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT } == true
            }
            compose.awaitTimerPage()

            val notification = TimerSurfaces.runningNotification(ongoing = true)
            assertEquals("the notification offers Stop alone", listOf(context.getString(R.string.notification_stop)),
                notification.actions.map { it.title.toString() })

            runBlocking { app.sender.sendAndWait() }
            // The entry the phone started under a local id now has the server's.
            val sent = app.awaitControls<TimerControls.Running>("the sent Start") { it.entry.id > 0 && !it.unsent }
            val current = legacy.get(legacy.server.api("me", "time_entries", "current"), JsonObject.serializer()).orFail("GET the running entry")
            assertEquals("Start reached 1.8.1", sent.entry.id, current.getValue("id").jsonPrimitive.long)

            compose.onNode(hasContentDescription(context.getString(R.string.timer_stop_spoken)) and hasClickAction()).performClick()
            app.awaitControls<TimerControls.Idle>("the phone's Stop", timeoutMs = 2_000)
            runBlocking { app.sender.sendAndWait() }
            // A Stop this soon after Start keeps no span on 1.8.1, which sends the entry to the trash.
            // Either way nothing runs there any more.
            val after = legacy.get(legacy.server.api("me", "time_entries", "current"), JsonElement.serializer()).orFail("GET the running entry")
            assertEquals("Stop reached 1.8.1", JsonNull, after)
        }

        // The widget draws the same controls: a running entry with Stop and no Pause.
        NativeWidget().use { widget ->
            runBlocking { app.changes.start(checkNotNull(app.accountSettings.state.value?.defaultWorkspaceId), EntryFields("On 1.8.1")) }
            widget.awaitDescription(context.getString(R.string.widget_stop_description))
            assertFalse("the widget offers no Pause", widget.hasDescription(context.getString(R.string.widget_pause_description)))
            widget.click(context.getString(R.string.widget_stop_description))
            widget.awaitDescription(context.getString(R.string.widget_start_description))
        }
        runBlocking { app.sender.sendAndWait() }
        assertEquals("nothing is left to send", 0, runBlocking { app.changes.unsentCount.first() })
    }

    // The main server's account, with the phone signed in to it.
    private fun prepareTheMainServer() {
        fixture.prepare()
        signedInToTheMainServer = true
    }

    /** The signed-in account's held timer state, or null. */
    private fun held(): TimerState? = app.timerState.state.value?.state

    /**
     * The other device starts an entry [startedSecondsAgo] ago through v9 and waits until the
     * phone holds it. With the app in the background only the timer event stream can bring it,
     * so this also waits out the stream's connection. Returns the entry's id.
     */
    private fun awaitTheStream(startedSecondsAgo: Long = 60): Long {
        val workspace = fixture.account.workspaceId
        val id = other.send("POST", other.server.api("workspaces", "$workspace", "time_entries"), buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "tokkl-android tests, another device")
            put("description", "Before the session")
            put("start", Instant.now().minusSeconds(startedSecondsAgo).truncatedTo(ChronoUnit.SECONDS).toString())
            put("duration", -1)
        }, JsonObject.serializer()).orFail("POST a running entry").getValue("id").jsonPrimitive.long
        val revision = other.timerState().revision
        val deadline = SystemClock.elapsedRealtime() + STREAM_CONNECT_MS
        while (held().let { it == null || it.revision < revision || it.runningEntry?.id != id }) {
            if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("the stream brought no state: held ${held()}")
            SystemClock.sleep(POLL_MS)
        }
        return id
    }

    private fun awaitTimerSyncService() {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (app.timerSyncService == null) {
            if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("the timer sync service did not start")
            SystemClock.sleep(POLL_MS)
        }
    }

    /**
     * Waits until every one of [conditions] has held, each timed from [from] on the monotonic
     * clock, and fails when one has not within [AT_ONCE_MS]. Logs each time for the record.
     */
    private fun within(what: String, from: Long, vararg conditions: Pair<String, () -> Boolean>) {
        val seen = linkedMapOf<String, Long>()
        while (seen.size < conditions.size) {
            val now = SystemClock.elapsedRealtime() - from
            for ((name, condition) in conditions) if (name !in seen && condition()) seen[name] = now
            if (seen.size == conditions.size) break
            if (now > AT_ONCE_MS) {
                val missing = conditions.map { it.first }.filter { it !in seen }
                throw AssertionError("$what: after $now ms, $missing did not hold, $seen did; held ${held()}")
            }
            SystemClock.sleep(TIGHT_POLL_MS)
        }
        Log.i(TAG, "$what within ${AT_ONCE_MS} ms: ${seen.entries.joinToString { "${it.key} ${it.value} ms" }}")
    }

    /**
     * Waits for the ring of [session]'s phase and checks that it began at the phase's planned end,
     * as the phone's clock corrected by the server's reads it: not before, and soon after.
     */
    private fun awaitRingAtItsEnd(session: TimerPomodoro): PomodoroRing {
        val phase = PomodoroPhaseKey(session.phase.start)
        val untilEnd = Duration.between(Instant.now(), session.phase.plannedEnd).toMillis().coerceAtLeast(0)
        val state = runBlocking {
            withTimeout(untilEnd + RING_LATE_MS + 20_000) {
                app.pomodoroRinging.state.first { it.serviceRunning && it.playing && it.ring?.phase == phase }
            }
        }
        val heard = System.currentTimeMillis()
        val ring = checkNotNull(state.ring)
        val late = heard - ring.end.toEpochMilli()
        Log.i(TAG, "the ${session.phase.kind} phase rang $late ms after its planned end")
        assertTrue("it rang ${-late} ms before the planned end", late >= -CLOCK_TOLERANCE_MS)
        assertTrue("it rang $late ms after the planned end", late <= RING_LATE_MS)
        assertEquals("the ring is the server's phase", session.phase.plannedEnd, ring.end.plus(checkNotNull(app.timerState.state.value).clockOffset))
        return ring
    }

    /** Opens the full-screen alarm of [ring]'s phase, as its notification's full-screen intent does. */
    private fun openAlarm(ring: PomodoroRing) = openAlarm(ring.phase)

    private fun openAlarm(phase: PomodoroPhaseKey) {
        device.wakeUp()
        context.startActivity(pomodoroAlarmIntent(context, phase))
        val alarm = TimerSurfaces.awaitAlarm()
        compose.waitUntil("the alarm shows the phase", 10_000) { alarm.state.value.ring?.phase == phase }
        compose.onNode(TimerSurfaces.onTheAlarm(hasText(context.getString(R.string.pomodoro_alarm_next)) and hasClickAction())).assertIsDisplayed()
    }

    private fun ringStopped(): Boolean = app.pomodoroRinging.state.value.let { !it.serviceRunning && !it.playing && !it.vibrationRequested }

    private inline fun <reified T : Activity> resumed(): T? {
        var found: T? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<T>().singleOrNull()
        }
        return found
    }

    private fun awaitSessionNotification(title: String): Notification {
        compose.waitUntil("the session notification shows $title", 10_000) { TimerSurfaces.sessionNotification()?.title() == title }
        return checkNotNull(TimerSurfaces.sessionNotification()).notification
    }

    private fun awaitNoSessionNotification() {
        compose.waitUntil("the session notification goes", 10_000) {
            notifications.activeNotifications.none { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
        }
    }

    private fun StatusBarNotification.title(): String? = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun sessionTitle(kind: Int, number: Int, every: Int): String =
        context.getString(R.string.pomodoro_session_title, context.getString(kind), number, every)

    /**
     * The session notification counts down to the server's planned end, on the phone's clock
     * corrected by the server's, and names the upcoming phase.
     */
    private fun assertCountsDownTo(notification: Notification, session: TimerPomodoro, held: HeldTimerState, upcoming: Int) {
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // The device need not share the server's clock. Check the actual notification against
        // the server's remaining interval at receipt, without using the production offset helper.
        val remaining = Duration.between(held.state.serverTime, session.phase.plannedEnd)
        assertEquals("it counts down for the server's remaining time", held.receivedAt.plus(remaining).toEpochMilli(), notification.`when`)
        assertEquals(context.getString(R.string.pomodoro_up_next, context.getString(upcoming)),
            notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
    }

    /**
     * The chain's total on the phone, as its clock shows it, against the server's at one sampling
     * instant: the other device reads the state, and the phone's clock at that read's middle is
     * compared with the server's stored seconds plus the clone's time at its `server_time`.
     */
    private fun assertChainTotalsAgree() {
        val before = Instant.now()
        val sample = other.timerState()
        val after = Instant.now()
        val pause = checkNotNull(sample.pause)
        val clone = checkNotNull(sample.runningEntry)
        assertEquals(pause.cloneEntryId, clone.id)
        val serverTotal = pause.seconds + Duration.between(OffsetDateTime.parse(clone.start).toInstant(), sample.serverTime).seconds
        val middle = before.plusMillis(Duration.between(before, after).toMillis() / 2)
        val shown = runBlocking { app.timerControls.first() } as TimerControls.Running
        val phoneTotal = shown.seconds(middle)
        val heldTotal = checkNotNull(app.timerState.state.value).chainTotal(middle)!!.seconds
        Log.i(TAG, "chain total: server $serverTotal s at ${sample.serverTime}, phone $phoneTotal s, held $heldTotal s")
        assertTrue("the phone shows $phoneTotal s, the server $serverTotal s", abs(phoneTotal - serverTotal) <= TOTAL_TOLERANCE_S)
        assertTrue("the held total is $heldTotal s, the server's $serverTotal s", abs(heldTotal - serverTotal) <= TOTAL_TOLERANCE_S)
    }

    /** A button of the full-screen timer labelled [label]. */
    private fun focused(label: Int) =
        hasText(context.getString(label)) and hasClickAction() and hasAnyAncestor(hasTestTag("fullscreen_timer"))

    private fun assertNoPomodoroItem(where: String) {
        compose.waitForIdle()
        assertTrue("the $where offers no Pomodoro", compose.onAllNodes(bottomBarItem(R.string.pomodoro_title)).fetchSemanticsNodes().isEmpty())
        assertTrue("the $where offers Timer", compose.onAllNodes(bottomBarItem(R.string.nav_timer)).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertNoPause(where: String) {
        compose.waitForIdle()
        for (label in listOf(R.string.timer_pause_spoken, R.string.timer_resume_spoken)) {
            assertTrue("$where offers no ${context.getString(label)}",
                compose.onAllNodes(hasContentDescription(context.getString(label))).fetchSemanticsNodes().isEmpty())
        }
    }

    private fun sessionBody(description: String, every: Int) = buildJsonObject {
        put("workspace_id", fixture.account.workspaceId)
        put("created_with", "tokkl-android tests, another device")
        put("description", description)
        put("project_id", null as Long?)
        put("task_id", null as Long?)
        put("tags", JsonArray(emptyList<JsonPrimitive>()))
        put("billable", false)
        put("work_minutes", 1)
        put("short_rest_minutes", 1)
        put("long_rest_minutes", 1)
        put("long_rest_every", every)
    }

    /** A timer action from the other device: its answer, a 2xx state. */
    private fun postTimer(vararg path: String, revision: Long? = null, body: JsonObject? = null): TimerState =
        other.send("POST", other.server.app(*path), body ?: buildJsonObject { if (revision != null) put("revision", revision) }, TimerState.serializer())
            .orFail("POST /api/app/${path.joinToString("/")}")

    private fun TogglApiClient.timerState(): TimerState = get(server.app("timer"), TimerState.serializer()).orFail("GET /api/app/timer")

    private companion object {
        const val TAG = "TimerCrossDevice"

        /** Plan 02: a remote change reaches the phone, and silences it, within 2 s. */
        const val AT_ONCE_MS = 2_000L

        /** How long the stream may take to connect after the app's first start. */
        const val STREAM_CONNECT_MS = 20_000L

        /** How late after its planned end a phase may begin to ring: alarm delivery and the player. */
        const val RING_LATE_MS = 10_000L

        /** Early alarm-delivery tolerance, measured on the corrected phone clock. */
        const val CLOCK_TOLERANCE_MS = 1_000L

        /** Whole seconds on two clocks sampled a request apart. */
        const val TOTAL_TOLERANCE_S = 2L

        const val POLL_MS = 50L
        const val TIGHT_POLL_MS = 10L

    }
}
