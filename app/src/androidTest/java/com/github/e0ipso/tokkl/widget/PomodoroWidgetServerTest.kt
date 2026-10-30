package com.github.e0ipso.tokkl.widget

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.pomodoro.HeldPhases
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.signin.SignInCredentials
import com.github.e0ipso.tokkl.signin.SignInResult
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timerstate.*
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Real pinned-server actions through the installed provider's native PendingIntents. */
class PomodoroWidgetServerTest {
    @get:Rule val session = SessionRule()
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var host: NativeWidget
    private val startLabel get() = context.getString(R.string.pomodoro_start)
    private val endLabel get() = context.getString(R.string.pomodoro_end)
    private val nextLabel get() = context.getString(R.string.pomodoro_start_short_rest)

    @Before fun prepare() {
        fixture.prepare()
        host = NativeWidget(PomodoroWidgetReceiver::class.java)
        host.awaitDescription(startLabel)
    }

    @After fun cleanup() {
        try { if (::host.isInitialized) host.close() } finally { fixture.cleanup() }
    }

    @Test fun remoteStartArrivesWithCorrectedCountdownAndNativeStartUsesDefaultsAndLastFields() {
        val api = fixture.account.otherDevice
        val workspace = fixture.account.workspaceId
        val project = api.send("POST", api.server.api("workspaces", "$workspace", "projects"), buildJsonObject {
            put("name", "Widget focus"); put("billable", true)
        }, JsonObject.serializer()).orFail("create project").getValue("id").jsonPrimitive.long
        val task = api.send("POST", api.server.api("workspaces", "$workspace", "projects", "$project", "tasks"), buildJsonObject {
            put("name", "Acceptance")
        }, JsonObject.serializer()).orFail("create task").getValue("id").jsonPrimitive.long
        api.sendNoContent("POST", api.server.api("me", "preferences"), buildJsonObject {
            put("pomodoro_focus_interval_in_minutes", 40); put("pomodoro_break_interval_in_minutes", 8)
            put("pomodoro_long_break_interval_in_minutes", 20); put("pomodoro_long_break_every", 3)
        }).orFail("save defaults")
        fixture.refresh()
        val remote = remoteStart(project, task)
        host.awaitText("Remote widget focus", 1_000)
        host.awaitText(context.getString(R.string.pomodoro_number, 1, 4))
        host.awaitDescription(context.getString(R.string.pomodoro_remaining))
        val active = active()
        val end = remote.pomodoro!!.phase.plannedEnd.minus(app.timerState.state.value!!.clockOffset)
        assertEquals(end, active.end)
        val expected = SystemClock.elapsedRealtime() + Duration.between(Instant.now(), end).toMillis()
        assertEquals("native countdown corrects the server boundary", expected.toDouble(), host.clockBaseValues().single().toDouble(), 250.0)
        assertEquals(0, host.provider.updatePeriodMillis)
        val remembered = checkNotNull(PomodoroWidgetFieldsStore(context).load(active.account))
        assertEquals(project, remembered.projectId)
        assertEquals(task, remembered.taskId)
        assertEquals(listOf("focus"), remembered.tags)
        assertTrue(remembered.billable)
        api.send("POST", api.server.app("pomodoro", "end"), buildJsonObject { put("revision", remote.revision) }, TimerState.serializer()).orFail("remote end")
        host.awaitDescription(startLabel, 1_000)
        host.awaitText(context.getString(R.string.pomodoro_widget_last, "Remote widget focus · Widget focus · Acceptance"))
        host.click(startLabel)
        host.awaitDescription(nextLabel, 1_000)
        val restarted = api.timerState()
        assertEquals(listOf(40, 8, 20, 3), restarted.pomodoro!!.run { listOf(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery) })
        val entry = restarted.runningEntry!!
        assertEquals("Remote widget focus", entry.description)
        assertEquals(project, entry.projectId); assertEquals(task, entry.taskId)
        assertEquals(listOf("focus"), entry.tags); assertEquals(true, entry.billable)
        host.click(nextLabel)
        host.awaitText(context.getString(R.string.pomodoro_short_rest), 1_000)
        assertEquals(PhaseKind.SHORT_REST, api.timerState().pomodoro!!.phase.kind)
        host.click(endLabel)
        host.awaitDescription(startLabel, 1_000)
        assertNull(api.timerState().pomodoro)
    }

    @Test fun offlineStartNextAndEndShowToastsAndLeaveStateAndQueueUntouched() {
        AirplaneMode.set(true, fixture.account.client)
        try {
            val before = app.timerState.state.value
            TimerActionToasts.await(TimerActionToasts.offline) { host.click(startLabel) }
            assertEquals(before, app.timerState.state.value)
            host.awaitDescription(startLabel)
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        } finally { AirplaneMode.set(false, fixture.account.client) }
        remoteStart()
        host.awaitDescription(nextLabel)
        val before = app.timerState.state.value
        AirplaneMode.set(true, fixture.account.client)
        try {
            for (label in listOf(nextLabel, endLabel)) {
                TimerActionToasts.await(TimerActionToasts.offline) { host.click(label) }
                assertEquals("failed $label preserves the held state", before, app.timerState.state.value)
                assertEquals(0, runBlocking { app.changes.unsentCount.first() })
            }
        } finally { AirplaneMode.set(false, fixture.account.client) }
        assertEquals(before!!.state.revision, fixture.account.otherDevice.timerState().revision)
    }

    @Test fun capturedNextAndEndRevisionsAreRefusedAfterRemoteEditsAndAdoptConflictState() {
        remoteStart()
        host.awaitDescription(nextLabel)
        // Hold back SSE so the launcher still shows the old revision when another device edits.
        // Keeping a View alone cannot freeze the extras of a reused native PendingIntent.
        app.timerSync.stop()
        host.awaitUpdatesSettled()
        val shown = active()
        val oldNext = host.captureTap(nextLabel)
        var current = fixture.account.otherDevice.timerState()
        editDescription(current, "Changed elsewhere")
        current = fixture.account.otherDevice.timerState()
        assertTrue("the server has a newer revision", current.revision > shown.revision)
        assertEquals("the edit keeps the phase", shown.session.phase.start, current.pomodoro!!.phase.start)
        assertEquals("the widget still holds its drawn revision", shown.revision, active().revision)
        TimerActionToasts.await(TimerActionResult.Refused.message) { oldNext() }
        host.awaitText("Changed elsewhere")
        assertEquals(current.revision, fixture.account.otherDevice.timerState().revision)
        assertEquals(current.revision, active().revision)
        assertEquals(PhaseKind.WORK, active().session.phase.kind)
        host.awaitUpdatesSettled()
        val oldEnd = host.captureTap(endLabel)
        val endRevision = current.revision
        editDescription(current, "Changed again")
        current = fixture.account.otherDevice.timerState()
        assertTrue("the server has a newer revision", current.revision > endRevision)
        assertEquals("the edit keeps the phase", shown.session.phase.start, current.pomodoro!!.phase.start)
        assertEquals("End keeps its drawn revision", endRevision, active().revision)
        TimerActionToasts.await(TimerActionResult.Refused.message) { oldEnd() }
        host.awaitText("Changed again")
        assertEquals(current.revision, fixture.account.otherDevice.timerState().revision)
        assertEquals(current.revision, active().revision)
        assertNotNull(current.pomodoro)
    }

    @Test fun signedOutAndReplacementAccountRejectOldNativeSessionTapsAndForgetFields() {
        remoteStart()
        host.awaitDescription(endLabel)
        val oldTap = host.captureTap(endLabel)
        val owner = active().account
        val before = fixture.account.otherDevice.timerState().revision
        val replacement = TestAccounts.create("Europe/Madrid")
        assertTrue(runBlocking { app.signOut.run() })
        host.awaitDescription(context.getString(R.string.widget_sign_in))
        assertNull(PomodoroWidgetFieldsStore(context).load(owner))
        try {
            // Production sign-out removes the HTTP confirmation as well as account settings.
            // Admit the replacement through the real sign-in request with its own /me response.
            ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
            assertEquals(SignInResult.SignedIn, SignIn(SessionWriter(app::storeSignIn)).attempt(
                TokklTestServer.url, SignInCredentials.ApiToken(replacement.apiToken),
            ))
            fixture.refresh()
            assertEquals(replacement.id, app.accountSettings.state.value?.userId)
            assertEquals(replacement.workspaceId, app.accountSettings.state.value?.defaultWorkspaceId)
            host.awaitDescription(startLabel)
            oldTap()
            host.awaitUpdatesSettled()
            assertEquals(before, fixture.account.otherDevice.timerState().revision)
            assertNull(replacement.otherDevice.timerState().pomodoro)
            assertEquals("", (runBlocking { app.pomodoroWidgetState.first() } as PomodoroWidgetState.Idle).entry.fields.description)
        } finally { TestAccounts.delete(replacement) }
    }

    @Test fun liveDeadlineRedrawsOvertimeWithinASecondWithoutAnAlarmOrRing() {
        val real = remoteStart()
        host.awaitDescription(context.getString(R.string.pomodoro_remaining))
        // Use the real server phase with a changed server clock offset, rather than waiting 25 minutes.
        // A far higher revision prevents the real SSE state replacing this controlled boundary.
        val held = HeldPhases.hold(real.copy(revision = real.revision + 1_000_000, serverTime = real.pomodoro!!.phase.plannedEnd.minusSeconds(5)))
        val boundary = active().end
        host.awaitDescription(context.getString(R.string.pomodoro_remaining))
        runBlocking { app.pomodoroAlarm.follow() }
        app.pomodoroAlarms.cancel()
        val before = host.updates.get()
        val untilBoundary = Duration.between(Instant.now(), boundary).toMillis().coerceAtLeast(0)
        host.awaitDescription(context.getString(R.string.pomodoro_overtime), untilBoundary + 1_000)
        assertFalse("redraw is not early", Instant.now().isBefore(boundary))
        assertTrue(host.updates.get() > before)
        assertFalse(app.pomodoroRinging.state.value.serviceRunning)
        assertEquals(held, app.timerState.state.value)
        host.awaitUpdatesSettled()
        host.assertClockTicksWithoutUpdates()
    }

    @Test fun defaultsRedrawPomodoroWithoutChangingTimerProviderKey() {
        NativeWidget().use { timer ->
            timer.awaitDescription(context.getString(R.string.widget_start_description))
            host.awaitUpdatesSettled(); timer.awaitUpdatesSettled()
            val timerKey = WidgetStore(context).load()
            val oldPomodoroKey = WidgetStore(context, WidgetStore.POMODORO_KEY).load()
            fixture.account.otherDevice.sendNoContent("POST", TokklTestServer.url.api("me", "preferences"), buildJsonObject {
                put("pomodoro_focus_interval_in_minutes", 42)
            }).orFail("change defaults")
            fixture.refresh()
            host.await("Pomodoro independently persists its changed key") {
                WidgetStore(context, WidgetStore.POMODORO_KEY).load() != oldPomodoroKey
            }
            assertEquals(timerKey, WidgetStore(context).load())
            assertEquals(42, (runBlocking { app.pomodoroWidgetState.first() } as PomodoroWidgetState.Idle).defaults.workMinutes)
            assertEquals(0, timer.provider.updatePeriodMillis)
        }
    }

    private fun active() = runBlocking { app.pomodoroWidgetState.first { it is PomodoroWidgetState.Active } } as PomodoroWidgetState.Active
    private fun remoteStart(project: Long? = null, task: Long? = null): TimerState {
        val api = fixture.account.otherDevice
        return api.send("POST", api.server.app("pomodoro"), buildJsonObject {
            put("workspace_id", fixture.account.workspaceId); put("description", "Remote widget focus")
            put("project_id", project); put("task_id", task); put("billable", true)
            put("tags", JsonArray(listOf(JsonPrimitive("focus"))))
            put("work_minutes", 25); put("short_rest_minutes", 5); put("long_rest_minutes", 15); put("long_rest_every", 4)
        }, TimerState.serializer()).orFail("remote session start")
    }
    private fun editDescription(current: TimerState, description: String) {
        val api = fixture.account.otherDevice
        api.send("PUT", api.server.api("workspaces", "${fixture.account.workspaceId}", "time_entries", "${current.runningEntry!!.id}"), buildJsonObject {
            put("description", description)
        }, JsonObject.serializer()).orFail("remote field edit")
    }
}
