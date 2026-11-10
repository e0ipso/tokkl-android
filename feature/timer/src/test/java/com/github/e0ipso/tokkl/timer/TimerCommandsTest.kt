package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The timer's taps without a view model: what goes to the queue and the server, and what
 * [TimerCommands.taps] holds meanwhile. Unconfined runs each tap until it waits for the queue or
 * the server.
 */
class TimerCommandsTest {

    private val controls = MutableStateFlow<TimerControls>(TimerControls.Idle)
    private val settings = MutableStateFlow<AccountSettings?>(
        AccountSettings("UTC", DayOfWeek.MONDAY, null, defaultWorkspaceId = WorkspaceId(7)),
    )
    private val changes = QueueingChanges()
    private val server = AnsweringServer()
    private val notices = TimerNoticeBoard(NoDiscards)

    /** What escaped the taps' effects, which on the phone would reach the thread and end the app. */
    private val escaped = CopyOnWriteArrayList<Throwable>()

    private val scope = CoroutineScope(
        Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, thrown -> escaped += thrown },
    )

    private val timer = TimerCommands(
        controls = controls,
        projects = MutableStateFlow(emptyList()),
        tasks = MutableStateFlow(emptyList()),
        settings = settings,
        timerApi = MutableStateFlow(true),
        changes = changes,
        pauseChain = server,
        sender = { changes.calls += "send queue" },
        notices = notices,
        sections = TapSections(TapTrace.None),
        clock = Clock.fixed(TAP, ZoneOffset.UTC),
        scope = scope,
    )

    init {
        // The screen collects the state while it shows.
        scope.launch { timer.state.collect {} }
        timer.edits.editDescription("Write")
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun aDoubleStartQueuesOneEntryWithTheDraft() {
        timer.start()
        timer.start()

        assertEquals(listOf("start 7 Write"), changes.calls)
        assertEquals(EntryFields(), timer.taps.value.draft)
        val pending = timer.taps.value.pending as PendingTap.Starting
        assertEquals(
            Triple(TAP, EntryFields(description = "Write"), WorkspaceId(7)),
            Triple(pending.at, pending.fields, pending.workspaceId),
        )
    }

    @Test
    fun aStartTheQueueRefusesBringsTheDraftBack() {
        changes.refuseStart = true

        timer.start()

        assertEquals(EntryFields(description = "Write"), timer.taps.value.draft)
        assertNull(timer.taps.value.pending)
    }

    /** A start the phone's storage refuses gives the draft back, as the entry form gives its form back. */
    @Test
    fun aStartTheStorageRefusesBringsTheDraftBackAndThrowsNothing() {
        changes.storageRefusal = IOException("disk full")

        timer.start()

        assertEquals(EntryFields(description = "Write"), timer.taps.value.draft)
        assertNull(timer.taps.value.pending)
        assertEquals(emptyList<Throwable>(), escaped.toList())
    }

    @Test
    fun aStopTheStorageRefusesLeavesTheEntryRunningAndThrowsNothing() {
        controls.value = running()
        changes.storageRefusal = IOException("disk full")

        timer.stop()

        assertNull(timer.taps.value.pending)
        assertEquals(EntryId(42), (timer.now() as TimerState.Running).entryId)
        assertEquals(emptyList<Throwable>(), escaped.toList())
    }

    /** A pick the storage refuses stays on the timer as typed, not sent, for the next send to take. */
    @Test
    fun aFieldEditTheStorageRefusesStaysAsTypedAndThrowsNothing() {
        controls.value = running()
        changes.storageRefusal = IOException("disk full")

        timer.edits.setBillable(true)

        val edit = timer.taps.value.edit
        assertEquals(EntryFields(description = "Write", billable = true), edit?.fields)
        assertEquals(false, edit?.sent)
        assertEquals(emptyList<Throwable>(), escaped.toList())
    }

    @Test
    fun stopOfTheRunningEntryGoesToTheQueue() {
        controls.value = running()

        timer.stop()

        assertEquals(listOf("stop 42"), changes.calls)
        assertEquals(TAP, (timer.taps.value.pending as PendingTap.Stopping).start)
    }

    @Test
    fun aRefusedPauseBecomesANoticeAndFreesTheTimer() = runBlocking {
        controls.value = running()
        server.answer = TimerActionResult.Refused

        timer.pause()

        assertEquals(listOf("pause"), server.calls)
        assertNull(timer.taps.value.pending)
        val notice = notices.notices(MutableStateFlow(null)).first() as TimerNotice.ActionFailed
        assertEquals(TimerActionResult.Refused, notice.result)
    }

    @Test
    fun aTapThatEndsAfterANewerOneLeavesTheNewerOnePending() {
        val stopping = PendingTap.Stopping(TAP)
        val acting = PendingTap.Acting(TimerTarget.Running(EntryId(42), ACCOUNT))

        val taps = TimerTaps().reduce(TapEvent.StopTapped(stopping)).reduce(TapEvent.ActionTapped(acting))

        assertEquals(acting, taps.reduce(TapEvent.TapEnded(stopping)).pending)
        assertNull(taps.reduce(TapEvent.TapEnded(acting)).pending)
    }

    /** Two Start taps at the same millisecond with the same fields are still two taps. */
    @Test
    fun anEndedStartLeavesAnIdenticalNewerStartPending() {
        fun tap() = PendingTap.Starting(TAP, EntryFields(description = "Write"), WorkspaceId(7))
        val older = tap()
        val newer = tap()

        val taps = TimerTaps().reduce(TapEvent.StartTapped(older, typed = false))
            .reduce(TapEvent.StartTapped(newer, typed = false))
            .reduce(TapEvent.StartEnded(older, queued = false, draft = EntryFields(description = "Write")))

        assertSame(newer, taps.pending)
        assertEquals("the newer start keeps the draft cleared", EntryFields(), taps.draft)
    }

    @Test
    fun aStartThatQueuedNothingKeepsADraftTypedMeanwhile() {
        val starting = PendingTap.Starting(TAP, EntryFields(description = "Write"), WorkspaceId(7))
        val typed = TimerTaps(draft = EntryFields(description = "Write"))
            .reduce(TapEvent.StartTapped(starting, typed = false))
            .reduce(TapEvent.Drafted(EntryFields(description = "Read")))

        val cleared = EntryFields(description = "Write")
        val ended = typed.reduce(TapEvent.StartEnded(starting, queued = false, draft = cleared))

        assertEquals(TimerTaps(draft = EntryFields(description = "Read")), ended)
    }

    private fun running() = TimerControls.Running(
        entry = TimeEntry(
            EntryId(
                42,
            ),
            WorkspaceId(7), null, null, "Write", EntryTiming.Running(TAP), false, emptyList(), emptyList(),
        ),
        unsent = false,
        accumulatedSeconds = 0,
        target = TimerTarget.Running(EntryId(42), ACCOUNT),
    )

    /**
     * Records each start and stop; a start is queued unless refused, and nothing reaches the cache.
     * Every write throws [storageRefusal] when it is set, as the phone's storage refuses one.
     */
    private class QueueingChanges : RunningEntryChanges {
        val calls = mutableListOf<String>()
        var refuseStart = false
        var storageRefusal: Exception? = null

        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId {
            storageRefusal?.let { throw it }
            if (refuseStart) throw IllegalStateException("the database refused the start")
            calls += "start $workspaceId ${fields.description}"
            CompletableDeferred<Unit>().await()
            error("unreachable")
        }

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
            storageRefusal?.let { throw it }
            calls += "stop $entryId"
            CompletableDeferred<Unit>().await()
            error("unreachable")
        }

        override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
            throw AssertionError("unexpected runningEntry")

        override suspend fun updateRunning(
            identity: RunningEntryIdentity,
            fields: EntryFields,
            start: Instant,
        ): Boolean = throw storageRefusal ?: AssertionError("unexpected updateRunning")

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }

    private class AnsweringServer : PauseActions {
        val calls = mutableListOf<String>()
        var answer: TimerActionResult = TimerActionResult.Done

        override suspend fun pause() = answer.also { calls += "pause" }

        override suspend fun resume(revision: Long) = answer.also { calls += "resume $revision" }

        override suspend fun discard(revision: Long) = answer.also { calls += "discard $revision" }
    }

    private object NoDiscards : DiscardNotices {
        override val discardNotice = MutableStateFlow<DiscardedPomodoro?>(null)

        override suspend fun takeDiscardNotice(): DiscardedPomodoro? = null
    }

    private companion object {
        val TAP: Instant = Instant.parse("2026-10-19T08:00:00Z")
    }
}

/** The name the timer controls of these tests carry for the signed-in account. */
private const val ACCOUNT = "account"
