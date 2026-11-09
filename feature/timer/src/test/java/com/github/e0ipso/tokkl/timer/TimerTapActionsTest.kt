package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.api.DiscardedPomodoro
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * The timer's taps without a view model: what goes to the queue and the server, and what [local]
 * holds meanwhile. Unconfined runs each tap until it waits for the queue or the server.
 */
class TimerTapActionsTest {

    private val local = MutableStateFlow(TimerTaps(draft = EntryFields(description = "Write")))
    private val cached = MutableStateFlow<CachedControls>(CachedControls.Read(TimerControls.Idle, null, null))
    private val settings = MutableStateFlow<AccountSettings?>(
        AccountSettings("UTC", DayOfWeek.MONDAY, null, defaultWorkspaceId = WorkspaceId(7)),
    )
    private val changes = QueueingChanges()
    private val server = AnsweringServer()
    private val notices = TimerNoticeBoard(NoDiscards)

    /** Swallows the start the queue refuses, as a test cannot let it reach the thread. */
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

    private val snapshot = TimerSnapshot(cached, local, settings, MutableStateFlow(true))
    private val taps = TimerTapActions(
        local = local,
        cached = cached,
        snapshot = snapshot,
        settings = settings,
        changes = changes,
        pauseChain = server,
        sender = { changes.calls += "send queue" },
        fieldEdits = RunningFieldEdits(local, cached, changes, scope, snapshot, MutableStateFlow(Catalog())),
        notices = notices,
        sections = TapSections(TapTrace.None),
        clock = Clock.fixed(TAP, ZoneOffset.UTC),
        scope = scope,
    )

    @Test
    fun aDoubleStartQueuesOneEntryWithTheDraft() {
        taps.start()
        taps.start()

        assertEquals(listOf("start 7 Write"), changes.calls)
        assertEquals(EntryFields(), local.value.draft)
        assertEquals(PendingTap.Starting(TAP, EntryFields(description = "Write"), WorkspaceId(7)), local.value.pending)
    }

    @Test
    fun aStartTheQueueRefusesBringsTheDraftBack() {
        changes.refuseStart = true

        taps.start()

        assertEquals(EntryFields(description = "Write"), local.value.draft)
        assertNull(local.value.pending)
    }

    @Test
    fun stopOfTheRunningEntryGoesToTheQueue() {
        cached.value = CachedControls.Read(running(), null, null)

        taps.stop()

        assertEquals(listOf("stop 42"), changes.calls)
        assertEquals(PendingTap.Stopping(TAP), local.value.pending)
    }

    @Test
    fun aRefusedPauseBecomesANoticeAndFreesTheTimer() = runBlocking {
        cached.value = CachedControls.Read(running(), null, null)
        server.answer = TimerActionResult.Refused

        taps.pause()

        assertEquals(listOf("pause"), server.calls)
        assertNull(local.value.pending)
        val notice = notices.notices(MutableStateFlow(null)).first() as TimerNotice.ActionFailed
        assertEquals(TimerActionResult.Refused, notice.result)
    }

    private fun running() = TimerControls.Running(
        entry = TimeEntry(EntryId(42), WorkspaceId(7), null, null, "Write", TAP, null, false, emptyList(), emptyList()),
        unsent = false,
        accumulatedSeconds = 0,
        target = TimerTarget(entryId = EntryId(42)),
    )

    /** Records each start and stop; a start is queued unless refused, and nothing reaches the cache. */
    private class QueueingChanges : RunningEntryChanges {
        val calls = mutableListOf<String>()
        var refuseStart = false

        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId {
            if (refuseStart) throw IllegalStateException("the database refused the start")
            calls += "start $workspaceId ${fields.description}"
            CompletableDeferred<Unit>().await()
            error("unreachable")
        }

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
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
        ): Boolean = throw AssertionError("unexpected updateRunning")

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
