package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TimerPause
import com.github.e0ipso.tokkl.api.TimerPomodoro
import com.github.e0ipso.tokkl.api.TimerPomodoroPhase
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.model.PhaseTransition
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.platform.MainDispatcherRule
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.continueEntry
import com.github.e0ipso.tokkl.timerstate.held
import com.github.e0ipso.tokkl.timerstate.serverTimerControls
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import com.github.e0ipso.tokkl.api.DiscardedPomodoro as ServerDiscardedPomodoro
import com.github.e0ipso.tokkl.api.TimerState as ServerTimerState

/**
 * Pause, Resume and Stop while paused on the timer's model (docs/PRD.md T1), against the cache's
 * running entry and the server's timer state, which the test holds together as the app's shared
 * controls read them, a server that answers the pause chain's actions, and a change queue for
 * everything else. The pause chain's actions wait for the server, and only Start, Stop of a
 * running entry and edits of it go to the queue.
 */
class TimerPauseTest {

    @get:Rule
    val main = MainDispatcherRule()
    private val scope = main.scope
    private val now = AtomicReference(TAP)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    /** What the cache and the timer state holder hold, written together as the holder writes them. */
    private val phone = MutableStateFlow(Phone(running = null, held = held(revision = 1)))
    private val timerApi = MutableStateFlow(true)
    private val changes = FakeChanges()
    private val server = FakeServer()
    private val calls = CopyOnWriteArrayList<String>()

    /** The queue's and the server's calls in the order they came, by kind. */
    private val events = CopyOnWriteArrayList<String>()
    private lateinit var model: TimerViewModel

    @Before
    fun showTheTimer() {
        model = timerModel()
        // The screen collects the state while it shows.
        scope.launch { model.state.collect {} }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun timerModel(
        discardNotices: Flow<DiscardedPomodoro?> = MutableStateFlow(null),
        takeDiscardNotice: suspend () -> DiscardedPomodoro? = { null },
    ) = TimerViewModel(
        controls = combine(phone, timerApi) { shown, api -> serverTimerControls(shown.running, shown.held, api) },
        projects = MutableStateFlow(listOf(APP)),
        tasks = MutableStateFlow(listOf(DESIGN)),
        accountSettings = MutableStateFlow(SETTINGS),
        changes = changes,
        scope = scope,
        clock = clock,
        pauseChain = server,
        sender = { events += "send queued" },
        discards = discardNotices(discardNotices, takeDiscardNotice),
        trace = TapTrace.None,
        timerApi = flowOf(true),
        view = MutableStateFlow(TimerView.LIST),
    )

    @Test
    fun pauseWaitsForTheServerAndThenShowsItsFrozenSeconds() {
        phone.value = Phone(running(), held(revision = 2))
        awaitState("the running entry shows") { it is TimerState.Running && it.entryId?.value == RUNNING_ID }

        server.gate = CompletableDeferred()
        onMain { model.pause() }
        onMain {
            model.pause()
            model.stop()
        }
        assertEquals("one Pause, sent to the server", listOf("pause"), calls)
        assertTrue("nothing went to the queue", changes.calls.isEmpty())
        val inFlight = model.state.value as TimerState.Running
        assertEquals("the timer keeps running until the server answers", EntryId(RUNNING_ID), inFlight.entryId)

        server.gate.complete(Unit)
        val paused = awaitState("the server's pause shows") { it is TimerState.Paused } as TimerState.Paused
        assertEquals("the server's stored seconds", 90L, paused.seconds)
        assertEquals(FIELDS, paused.fields)
        assertEquals(WorkspaceId(WORKSPACE), paused.workspaceId)
        assertEquals(TimerTarget.server(revision = 3), paused.target)
        assertTrue("nothing was queued", changes.calls.isEmpty())
    }

    @Test
    fun pauseSendsADescriptionStillBeingTypedBeforeItself() {
        phone.value = Phone(running(), held(revision = 2))
        awaitState("the running entry shows") { it is TimerState.Running }

        onMain { model.editDescription("Typed before Pause") }
        awaitState(
            "the typed description shows",
        ) { it is TimerState.Running && it.fields.description == "Typed before Pause" }
        onMain { model.pause() }

        val typed = FIELDS.copy(description = "Typed before Pause")
        awaitState("the server's pause shows") { it is TimerState.Paused }
        assertEquals(listOf("updateRunning $RUNNING_ID $typed"), changes.calls)
        assertEquals(
            "the queue goes out before the server pauses",
            listOf("updateRunning", "send queued", "pause"),
            events,
        )
    }

    @Test
    fun anOfflinePauseSaysSoAndLeavesTheTimerRunning() {
        phone.value = Phone(running(), held(revision = 2))
        awaitState("the running entry shows") { it is TimerState.Running }
        server.answer = TimerActionResult.Offline

        onMain { model.pause() }

        val notice = awaitNotice { it is TimerNotice.ActionFailed } as TimerNotice.ActionFailed
        assertEquals(TimerActionResult.Offline, notice.result)
        val shown = awaitState(
            "taps count again",
        ) { it is TimerState.Running && model.isShown(it.target!!) } as TimerState.Running
        assertEquals(EntryId(RUNNING_ID), shown.entryId)
        assertTrue("nothing was queued", changes.calls.isEmpty())
        assertEquals(listOf("pause"), calls)

        assertEquals(notice, main.onMain { model.frame.claim(notice) })
        onMain { model.frame.noticeShown(notice) }
        awaitNotice { it == null }
    }

    @Test
    fun aRefusedPauseShowsTheServersStateAndSaysAnotherDeviceChangedIt() {
        phone.value = Phone(running(), held(revision = 2))
        awaitState("the running entry shows") { it is TimerState.Running }
        // Another device paused first, and the refusal brings its state.
        server.answer = TimerActionResult.Refused
        server.refusal = Phone(running = null, held(revision = 3, pause = pause(seconds = 120, cloneEntryId = null)))

        onMain { model.pause() }

        assertEquals(
            TimerActionResult.Refused,
            (awaitNotice { it is TimerNotice.ActionFailed } as TimerNotice.ActionFailed).result,
        )
        val paused = awaitState("the server's state shows") { it is TimerState.Paused } as TimerState.Paused
        assertEquals(120L, paused.seconds)
    }

    @Test
    fun pauseIsNotOfferedWithoutTheTimerApiOrDuringASession() {
        phone.value = Phone(running(), held(revision = 2))
        awaitState("Pause shows") { it is TimerState.Running && it.pausable }

        timerApi.value = false
        awaitState("the plain timer shows") { it is TimerState.Running && !it.pausable }
        onMain { model.pause() }

        timerApi.value = true
        awaitState("Pause shows again") { it is TimerState.Running && it.pausable }
        phone.value = Phone(running(), held(revision = 3, pomodoro = pomodoro()))
        awaitState("the pomodoro shows") { it is TimerState.Running && !it.pausable }
        onMain { model.pause() }

        main.settle(SETTLE_PAUSE_MS)
        assertTrue("no Pause went anywhere", calls.isEmpty() && changes.calls.isEmpty())
    }

    @Test
    fun resumeSendsTheShownRevisionAndTheCloneCountsOnTheServersClock() {
        phone.value = Phone(running = null, held(revision = 12, pause = pause(seconds = 600, cloneEntryId = null)))
        awaitState("the paused timer shows") { it is TimerState.Paused }

        server.gate = CompletableDeferred()
        onMain { model.resume() }
        assertEquals(listOf("resume 12"), calls)
        assertTrue("the paused view stays while Resume is on its way", model.state.value is TimerState.Paused)

        server.gate.complete(Unit)
        val resumed = awaitState(
            "the clone shows",
        ) { it is TimerState.Running && it.entryId?.value == CLONE_ID } as TimerState.Running
        assertEquals("the clock goes on from the chain's seconds", 600L, resumed.accumulatedSeconds)
        assertEquals("the clone counts on the server's clock", OFFSET, resumed.clockOffset)
        assertEquals("Resume starts the fields kept at Pause", FIELDS, resumed.fields)
        assertTrue(resumed.pausable)
        assertNotNull(
            "a resume asks about notifications as a start does",
            main.onMain { withTimeoutOrNull(TIMEOUT_MS) { model.frame.starts.first() } },
        )
        assertTrue("nothing was queued", changes.calls.isEmpty())
    }

    @Test
    fun anOfflineResumeOrStopLeavesThePausedTimerAsItWas() {
        val shown = Phone(running = null, held(revision = 12, pause = pause(seconds = 600, cloneEntryId = null)))
        phone.value = shown
        val before = awaitState("the paused timer shows") { it is TimerState.Paused } as TimerState.Paused
        server.answer = TimerActionResult.Offline

        onMain { model.resume() }
        awaitNotice { it is TimerNotice.ActionFailed }
        awaitState("taps count again") { it is TimerState.Paused && model.isShown(it.target!!) }
        onMain { model.stop() }
        awaitNotice { (it as? TimerNotice.ActionFailed)?.number == 2L }
        awaitState("taps count again") { it is TimerState.Paused && model.isShown(it.target!!) }

        assertEquals(listOf("resume 12", "discard 12"), calls)
        assertEquals(before, model.state.value)
        assertEquals(shown, phone.value)
        assertTrue("nothing was queued", changes.calls.isEmpty())
    }

    @Test
    fun stopWhilePausedDiscardsTheShownRevisionOnTheServer() {
        phone.value = Phone(running = null, held(revision = 12, pause = pause(seconds = 600, cloneEntryId = null)))
        awaitState("the paused timer shows") { it is TimerState.Paused }

        server.gate = CompletableDeferred()
        onMain { model.stop() }
        assertEquals(listOf("discard 12"), calls)
        assertTrue("the paused view stays while the discard is on its way", model.state.value is TimerState.Paused)

        server.gate.complete(Unit)
        awaitState("the server ended the chain") { it is TimerState.Idle && it.authoritative }
        assertTrue("nothing was queued", changes.calls.isEmpty())
    }

    @Test
    fun aPausedTimersFieldsAreReadOnly() {
        phone.value = Phone(running = null, held(revision = 12, pause = pause(seconds = 600, cloneEntryId = null)))
        awaitState("the paused timer shows") { it is TimerState.Paused }

        onMain {
            model.editDescription("Edited while paused")
            model.descriptionDone()
            model.pick(Picked(projectId = null))
            model.setBillable(true)
            model.fill(EntryFields("Filled while paused"))
        }

        main.settle(SETTLE_PAUSE_MS)
        assertEquals(FIELDS, (model.state.value as TimerState.Paused).fields)
        assertTrue("nothing went anywhere", calls.isEmpty() && changes.calls.isEmpty())
    }

    @Test
    fun continueWhilePausedQueuesANewEntryAndNotTheChain() {
        phone.value = Phone(running = null, held(revision = 12, pause = pause(seconds = 600, cloneEntryId = null)))
        awaitState("the paused timer shows") { it is TimerState.Paused }

        onMain { model.continueEntry(PAST) }

        assertEquals(listOf("start $WORKSPACE ${PAST.fields()}"), changes.calls)
        val started = awaitState(
            "the continued entry runs",
        ) { it is TimerState.Running && it.entryId?.value == STARTED_ID } as TimerState.Running
        assertEquals("a new timer leaves the chain's seconds behind", 0L, started.accumulatedSeconds)
        assertTrue("the server's chain is not resumed", calls.isEmpty())
    }

    @Test
    fun stopOfTheRunningCloneIsAQueuedStop() {
        phone.value = Phone(
            clone(),
            held(revision = 13, pause = pause(seconds = 600, cloneEntryId = EntryId(CLONE_ID))),
        )
        awaitState("the clone runs") { it is TimerState.Running && it.entryId?.value == CLONE_ID }

        onMain { model.stop() }

        assertEquals(listOf("stop $CLONE_ID null"), changes.calls)
        awaitState("the timer stops at once") { it is TimerState.Idle && it.authoritative }
        assertTrue("Stop of a running clone needs no server", calls.isEmpty())
    }

    @Test
    fun focusedCallbacksCannotPauseStopOrEditAReplacement() {
        phone.value = Phone(running(), held(revision = 2))
        val old = awaitState("old focused context") { it is TimerState.Running } as TimerState.Running
        phone.value = Phone(running(id = 99), held(revision = 3))
        awaitState("replacement context") { it is TimerState.Running && it.entryId?.value == 99L }

        onMain {
            model.pauseShown(old.target!!)
            model.stopShown(old.target!!)
        }

        assertNull(main.onMain { model.runningForEditShown(old.target!!) })
        assertTrue(calls.isEmpty() && changes.calls.isEmpty())
        assertEquals(TimerTarget.server(entryId = EntryId(99)), model.state.value.target)
    }

    @Test
    fun focusedPauseAndResumeKeepTheChainsSecondsAndFenceAnOlderRevision() {
        phone.value = Phone(running(), held(revision = 2))
        val running = awaitState("running context") { it is TimerState.Running } as TimerState.Running
        onMain { model.pauseShown(running.target!!) }
        val paused = awaitState(
            "settled paused context",
        ) { it is TimerState.Paused && model.isShown(it.target!!) } as TimerState.Paused
        assertEquals(90L, paused.seconds)

        onMain { model.resumeShown(running.target!!) }
        assertEquals("a stale running callback cannot resume its paused successor", listOf("pause"), calls)
        // A rename shown with the chain moves the server's revision.
        phone.value = Phone(running = null, held(revision = 4, pause = pause(seconds = 90, cloneEntryId = null)))
        val renamed = awaitState(
            "the newer revision shows",
        ) { it is TimerState.Paused && it.target != paused.target } as TimerState.Paused
        onMain { model.resumeShown(paused.target!!) }
        assertEquals("an older revision's callback sends nothing", listOf("pause"), calls)

        onMain { model.resumeShown(renamed.target!!) }
        val resumed = awaitState(
            "settled resumed context",
        ) { it is TimerState.Running && model.isShown(it.target!!) } as TimerState.Running
        assertEquals(listOf("pause", "resume 4"), calls)
        assertEquals(90L, resumed.accumulatedSeconds)
        assertEquals(EntryId(CLONE_ID), resumed.entryId)
        val identity = main.onMain { model.runningForEditShown(resumed.target!!) }
        assertEquals(RunningEntryIdentity(EntryId(CLONE_ID), WorkspaceId(WORKSPACE), CLONE_START), identity)
    }

    @Test
    fun focusedStopExitsOnlyAfterAuthoritativeIdleAndCannotRepeat() {
        phone.value = Phone(running(), held(revision = 2))
        val shown = awaitState("running context") { it is TimerState.Running } as TimerState.Running
        changes.gate = CompletableDeferred()
        onMain { model.stopShown(shown.target!!) }
        val pending = awaitState("the stop shows") { it is TimerState.Idle } as TimerState.Idle
        assertFalse("optimistic idle must keep the focused route", pending.authoritative)
        assertNull(pending.target)
        changes.gate.complete(Unit)
        awaitState("authoritative stop") { it is TimerState.Idle && it.authoritative }
        onMain { model.stopShown(shown.target!!) }
        assertEquals(listOf("stop $RUNNING_ID null"), changes.calls)
    }

    @Test
    fun aFocusedStopWhilePausedIsNotAuthoritativeUntilTheServerEndsTheChain() {
        phone.value = Phone(running = null, held(revision = 12, pause = pause(seconds = 600, cloneEntryId = null)))
        val paused = awaitState("paused context") { it is TimerState.Paused } as TimerState.Paused
        server.gate = CompletableDeferred()

        onMain { model.stopShown(paused.target!!) }

        assertFalse("taps wait for the server's answer", model.isShown(paused.target!!))
        assertNull(main.onMain { model.runningForEdit() })
        onMain { model.stopShown(paused.target!!) }
        assertEquals(listOf("discard 12"), calls)
        server.gate.complete(Unit)
        awaitState("authoritative idle") { it is TimerState.Idle && it.authoritative }
    }

    @Test
    fun theRunningEditorDoesNotOpenWhilePausedOrWhileAPauseIsOnItsWay() {
        phone.value = Phone(running(), held(revision = 2))
        awaitState("the running entry shows") { it is TimerState.Running }
        server.gate = CompletableDeferred()
        onMain { model.pause() }

        assertNull("Pause is on its way", main.onMain { model.runningForEdit() })
        server.gate.complete(Unit)
        awaitState("the server's pause shows") { it is TimerState.Paused }
        assertNull("paused", main.onMain { model.runningForEdit() })
    }

    @Test
    fun aTooShortPomodoroIsNoticedOnceEvenWhenItsStateArrivesAgain() {
        val holder = TimerStateSource(
            initial = null,
            store = {},
            cache = NoRunningEntries,
            clock = clock,
            runningEnded = {},
        )
        val notices = timerModel(
            discardNotices = holder.discardNotice,
            takeDiscardNotice = { holder.takeDiscardNotice() },
        )
        scope.launch { notices.frame.notice.collect {} }
        val discarded = ServerDiscardedPomodoro(entryId = 70, start = TAP.minusSeconds(40), stop = TAP)
        val state = serverState(revision = 20).copy(discardedPomodoro = discarded)

        assertTrue(holder.accept(holder.begin(), state))
        val notice = awaitNotice(notices) { it is TimerNotice.PomodoroDiscarded }
        assertEquals(TimerNotice.PomodoroDiscarded(70), notice)
        assertEquals(notice, main.onMain { notices.frame.claim(notice!!) })
        assertNull("the holder recorded it as shown", holder.discardNotice.value)
        awaitNotice(notices) { it == notice }

        onMain { notices.frame.noticeShown(notice!!) }
        awaitNotice(notices) { it == null }
        assertTrue("the same state arrives again", holder.accept(holder.begin(), state))
        main.settle(SETTLE_PAUSE_MS)
        assertNull("its notice does not come back", notices.frame.notice.value)
        assertNull("nor can a screen claim it", main.onMain { notices.frame.claim(notice!!) })
    }

    private fun awaitState(what: String, condition: (TimerState) -> Boolean): TimerState {
        var last: TimerState? = null
        return main.onMain {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.onEach { last = it }.first(condition) }
        } ?: throw AssertionError(
            "timed out waiting until $what; the last state was $last, after ${changes.calls} and $calls",
        )
    }

    private fun awaitNotice(from: TimerViewModel = model, condition: (TimerNotice?) -> Boolean): TimerNotice? {
        var last: TimerNotice? = null
        var matched = false
        val found = main.onMain {
            withTimeoutOrNull(TIMEOUT_MS) {
                from.frame.notice.onEach { last = it }.first(condition).also { matched = true }
            }
        }
        if (!matched) throw AssertionError("timed out waiting for a notice; the last was $last, after $calls")
        return found
    }

    /** The tap handlers run on the main thread, which [main] stands in for. */
    private fun onMain(block: () -> Unit) {
        main.runCurrent()
        block()
        main.runCurrent()
    }

    private fun running(id: Long = RUNNING_ID) = EntryWithSendState(
        TimeEntry(
            EntryId(id), WorkspaceId(WORKSPACE), FIELDS.projectId, FIELDS.taskId, FIELDS.description,
            EntryTiming.Running(
                TAP.minusSeconds(
                    90,
                ),
            ),
            FIELDS.billable, FIELDS.tags, emptyList(),
        ),
        unsent = false,
    )

    private fun clone() = EntryWithSendState(
        TimeEntry(
            EntryId(CLONE_ID),
            WorkspaceId(WORKSPACE),
            FIELDS.projectId,
            FIELDS.taskId,
            FIELDS.description,
            EntryTiming.Running(CLONE_START),
            FIELDS.billable,
            FIELDS.tags,
            emptyList(),
        ),
        unsent = false,
    )

    private fun TimeEntry.fields() = EntryFields(description, projectId, taskId, tags, billable)

    /** A state the phone received at [TAP], with the server's clock [offset] ahead. */
    private fun held(
        revision: Long,
        pause: TimerPause? = null,
        pomodoro: TimerPomodoro? = null,
        offset: Duration = OFFSET,
    ): HeldTimerState = serverState(revision, pause, pomodoro, offset).held(TAP)

    /** The server's state that [held] is. */
    private fun serverState(
        revision: Long,
        pause: TimerPause? = null,
        pomodoro: TimerPomodoro? = null,
        offset: Duration = OFFSET,
    ): ServerTimerState {
        val clone = pause?.cloneEntryId?.let {
            ServerTimeEntry(
                id = it,
                workspaceId = WORKSPACE,
                description = FIELDS.description,
                start = CLONE_START.toString(),
            )
        }
        return ServerTimerState(revision, TAP.plus(offset), clone, pause, pomodoro, discardedPomodoro = null)
    }

    private fun pause(seconds: Long, cloneEntryId: EntryId?) = TimerPause(
        paused = cloneEntryId == null,
        seconds = seconds,
        workspaceId = WORKSPACE,
        description = FIELDS.description,
        projectId = FIELDS.projectId?.value,
        taskId = FIELDS.taskId?.value,
        tagIds = listOf(3),
        tags = FIELDS.tags,
        billable = FIELDS.billable,
        cloneEntryId = cloneEntryId?.value,
    )

    private fun pomodoro() = TimerPomodoro(
        workMinutes = 25,
        shortRestMinutes = 5,
        longRestMinutes = 15,
        longRestEvery = 4,
        phase = TimerPomodoroPhase(
            PhaseKind.WORK,
            TAP.minusSeconds(90),
            completed = 0,
            plannedEnd = TAP.plusSeconds(1_410),
        ),
        upcomingPhase = PhaseKind.SHORT_REST,
        transition = PhaseTransition.WORK_TO_SHORT_REST,
        number = 1,
        entryId = RUNNING_ID,
        pomodoroStart = TAP.minusSeconds(90),
    )

    /** The cache and the timer state holder as the shared controls read them together. */
    private data class Phone(val running: EntryWithSendState?, val held: HeldTimerState?)

    /**
     * The server's pause chain actions. Each waits for [gate], then answers [answer]. A Done
     * pause stops the running entry into the chain, a Done resume starts the clone and a Done
     * discard ends the chain, each with the next revision, written to the cache and the holder
     * together before it returns, as the holder does. A refusal brings [refusal], when set.
     */
    private inner class FakeServer : PauseActions {
        @Volatile
        var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

        @Volatile
        var answer: TimerActionResult = TimerActionResult.Done

        @Volatile
        var refusal: Phone? = null

        override suspend fun pause(): TimerActionResult = act("pause") { shown ->
            val entry = shown.running?.entry ?: return@act shown
            val stored = Duration.between(entry.start, clock.instant()).seconds
            Phone(running = null, held(revision(shown) + 1, pause = pause(seconds = stored, cloneEntryId = null)))
        }

        override suspend fun resume(revision: Long): TimerActionResult = act("resume $revision") { shown ->
            val chain = shown.held?.pause ?: return@act shown
            Phone(
                clone(),
                held(revision(shown) + 1, pause = pause(seconds = chain.seconds, cloneEntryId = EntryId(CLONE_ID))),
            )
        }

        override suspend fun discard(revision: Long): TimerActionResult = act("discard $revision") { shown ->
            Phone(running = null, held(revision(shown) + 1))
        }

        private fun revision(shown: Phone) = shown.held?.revision ?: 0

        private suspend fun act(call: String, done: (Phone) -> Phone): TimerActionResult {
            calls += call
            events += call.substringBefore(' ')
            gate.await()
            when (answer) {
                TimerActionResult.Done -> phone.value = done(phone.value)
                TimerActionResult.Refused -> refusal?.let { phone.value = it }
                else -> Unit
            }
            return answer
        }
    }

    /**
     * The change queue: each call reads the tap time first, then waits for [gate], then changes
     * the cache's running entry, leaving the holder's state as it was.
     */
    private inner class FakeChanges : RunningEntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        @Volatile
        var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId {
            val at = clock.instant()
            calls += "start $workspaceId $fields"
            gate.await()
            val entry =
                TimeEntry(
                    EntryId(STARTED_ID),
                    workspaceId,
                    fields.projectId,
                    fields.taskId,
                    fields.description,
                    EntryTiming.Running(at),
                    fields.billable,
                    fields.tags,
                    emptyList(),
                )
            phone.value = phone.value.copy(running = EntryWithSendState(entry, unsent = true))
            return EntryId(STARTED_ID)
        }

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
            calls += "stop $entryId $fields"
            gate.await()
            if (phone.value.running?.entry?.id != entryId) return false
            phone.value = phone.value.copy(running = null)
            return true
        }

        override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
            phone.value.running?.entry?.takeIf {
                it.id == identity.entryId && it.workspaceId == identity.workspaceId && it.start == identity.start
            }

        override suspend fun updateRunning(
            identity: RunningEntryIdentity,
            fields: EntryFields,
            start: Instant,
        ): Boolean {
            calls += "updateRunning ${identity.entryId} $fields"
            events += "updateRunning"
            gate.await()
            val current = runningEntry(identity) ?: return false
            val edited = current.copy(
                description = fields.description,
                projectId = fields.projectId,
                taskId = fields.taskId,
                tags = fields.tags,
                billable = fields.billable,
            )
            phone.value = phone.value.copy(running = EntryWithSendState(edited, unsent = true))
            return true
        }

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }

    /** A cache with no running entry, for states that name none. */
    private object NoRunningEntries : CurrentEntryCache {
        override fun latestRunning(): EntryWithSendState? = null

        override fun runningCheckStart() = RunningCheckStart(emptyList())

        override fun checkedRunning(before: RunningCheckStart, server: TimeEntry?): RunningCheckWrites? = null
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** Long enough for the model to show a change, which it must not. */
        const val SETTLE_PAUSE_MS = 300L
        const val WORKSPACE = 10L
        const val RUNNING_ID = 42L
        const val CLONE_ID = 43L
        const val STARTED_ID = -3L
        val TAP: Instant = Instant.parse("2026-10-19T10:00:00.250Z")
        val CLONE_START: Instant = TAP.minusSeconds(5)

        /** The server's clock runs this far ahead of the phone's. */
        val OFFSET: Duration = Duration.ofSeconds(3)
        val SETTINGS = AccountSettings(
            timeZone = "Europe/Madrid",
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
            defaultWorkspaceId = WorkspaceId(WORKSPACE),
        )
        val APP = Project(
            ProjectId(1),
            WorkspaceId(WORKSPACE),
            clientId = null,
            name = "Tokkl for Android",
            color = "#0b83d9",
            active = true,
            billable = false,
        )
        val DESIGN = Task(TaskId(5), WorkspaceId(WORKSPACE), projectId = APP.id, name = "Design", active = true)
        val FIELDS = EntryFields("Pause the timer", projectId = APP.id, taskId = DESIGN.id, tags = listOf("android"))

        /** A stopped entry in the default workspace, which Continue starts again. */
        val PAST = TimeEntry(
            id = EntryId(7),
            workspaceId = WorkspaceId(WORKSPACE),
            projectId = APP.id,
            taskId = null,
            description = "Review the mockups",
            timing = EntryTiming.Stopped(Instant.parse("2026-10-18T08:00:00Z"), Instant.parse("2026-10-18T09:30:00Z")),
            billable = true,
            tags = listOf("review"),
            tagIds = emptyList(),
        )
    }
}
