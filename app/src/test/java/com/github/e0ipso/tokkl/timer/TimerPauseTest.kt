package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.cache.seconds
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Pause, Resume and Stop on the timer's model (docs/PRD.md T1), against the controls the test
 * sets, as the queue's timerControls would show them, and a change queue that changes them. Each
 * tap must change what the timer shows before the queue has written anything.
 */
class TimerPauseTest {

    private val main: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainDispatcher: ExecutorCoroutineDispatcher = main.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val now = AtomicReference(TAP)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val controls = MutableStateFlow<TimerControls>(TimerControls.Idle)
    private val changes = FakeChanges()
    private lateinit var model: TimerModel

    @Before
    fun showTheTimer() {
        model = TimerModel(
            controls = controls,
            projects = MutableStateFlow(listOf(APP)),
            tasks = MutableStateFlow(listOf(DESIGN)),
            accountSettings = MutableStateFlow(SETTINGS),
            changes = changes,
            scope = scope,
            clock = clock,
        )
        // The screen collects the state while it shows.
        scope.launch { model.state.collect {} }
    }

    @After
    fun tearDown() {
        scope.cancel()
        main.shutdown()
    }

    @Test
    fun pauseShowsThePausedTotalAtOnceAndResumeGoesOnFromIt() {
        controls.value = running(accumulatedSeconds = 600)
        awaitState("the running entry shows") { it is TimerState.Running }

        changes.gate = CompletableDeferred()
        onMain { model.pause() }
        val paused = awaitState("the timer pauses before the queue has written it") { it is TimerState.Paused } as TimerState.Paused
        assertEquals("the chain's stored time and the segment's", 690L, paused.seconds)
        assertEquals(FIELDS, paused.fields)
        assertEquals(WORKSPACE, paused.workspaceId)
        assertEquals(listOf("pause ${RUNNING.copy(chainId = CHAIN, revision = 3)} null"), changes.calls)

        changes.gate.complete(Unit)
        awaitControls("the queue paused the chain") { it is TimerControls.Paused }

        now.set(TAP.plusSeconds(60))
        changes.gate = CompletableDeferred()
        tapUntilTaken { model.resume() }
        val resuming = awaitState("the timer resumes before the queue has written it") { it is TimerState.Running } as TimerState.Running
        assertNull(resuming.entryId)
        assertEquals(TAP.plusSeconds(60), resuming.start)
        assertEquals("the clock goes on from the paused total", 690L, resuming.accumulatedSeconds)

        changes.gate.complete(Unit)
        val resumed = awaitState("the clone shows") { it is TimerState.Running && it.entryId == CLONE_ID } as TimerState.Running
        assertEquals(690L, resumed.accumulatedSeconds)
        assertEquals("Resume starts the kept fields", FIELDS, resumed.fields)
    }

    @Test
    fun pauseKeepsADescriptionStillBeingTyped() {
        controls.value = running()
        awaitState("the running entry shows") { it is TimerState.Running }

        onMain { model.editDescription("Typed before Pause") }
        awaitState("the typed description shows") { it is TimerState.Running && it.fields.description == "Typed before Pause" }
        onMain { model.pause() }

        val typed = FIELDS.copy(description = "Typed before Pause")
        assertEquals(listOf("pause $RUNNING $typed"), changes.calls)
        awaitControls("the chain keeps the typed description") { (it as? TimerControls.Paused)?.fields == typed }
        val paused = awaitState("the paused timer shows it") { it is TimerState.Paused && it.fields == typed } as TimerState.Paused
        assertEquals(90L, paused.seconds)
    }

    @Test
    fun aPauseTheQueueRefusesShowsTheRunningTimerAgain() {
        controls.value = running()
        awaitState("the running entry shows") { it is TimerState.Running }
        changes.refusePause = true

        onMain { model.pause() }
        Thread.sleep(SETTLE_PAUSE_MS)

        assertEquals(listOf("pause $RUNNING null"), changes.calls)
        val shown = model.state.value as TimerState.Running
        assertEquals(RUNNING_ID, shown.entryId)
    }

    @Test
    fun stopWhilePausedEndsTheChainAtOnce() {
        controls.value = PAUSED
        awaitState("the paused timer shows") { it is TimerState.Paused }

        changes.gate = CompletableDeferred()
        onMain { model.stop() }
        awaitIdle()
        assertEquals("a paused chain stops by its target", listOf("stop $PAUSED_TARGET null"), changes.calls)

        changes.gate.complete(Unit)
        awaitControls("the queue ended the chain") { it == TimerControls.Idle }
        awaitIdle()
    }

    @Test
    fun anEditWhilePausedChangesWhatResumeStarts() {
        controls.value = PAUSED
        awaitState("the paused timer shows") { it is TimerState.Paused }

        onMain { model.editDescription("Edited while paused") }
        awaitState("the typed description shows") { it is TimerState.Paused && it.fields.description == "Edited while paused" }
        assertEquals("typing keeps the edit on the phone", emptyList<String>(), changes.calls)

        onMain { model.descriptionDone() }
        val edited = FIELDS.copy(description = "Edited while paused")
        awaitControls("the chain keeps the edit") { (it as? TimerControls.Paused)?.fields == edited }
        assertEquals(listOf("editPaused $PAUSED_TARGET $edited"), changes.calls)

        tapUntilTaken { model.resume() }
        val resumed = awaitState("the clone shows") { it is TimerState.Running && it.entryId == CLONE_ID } as TimerState.Running
        assertEquals(edited, resumed.fields)
        assertEquals(PAUSED.seconds, resumed.accumulatedSeconds)
    }

    @Test
    fun aPickWhilePausedGoesToTheChainAtOnce() {
        controls.value = PAUSED
        awaitState("the paused timer shows") { it is TimerState.Paused }

        onMain { model.setBillable(true) }

        val billable = FIELDS.copy(billable = true)
        assertEquals(listOf("editPaused $PAUSED_TARGET $billable"), changes.calls)
        awaitControls("the chain keeps the pick") { (it as? TimerControls.Paused)?.fields == billable }
    }

    @Test
    fun continueWhilePausedStartsTheEntryAndNotTheChain() {
        controls.value = PAUSED
        awaitState("the paused timer shows") { it is TimerState.Paused }

        onMain { model.continueEntry(PAST) }

        assertEquals(listOf("start $WORKSPACE ${PAST.fields()}"), changes.calls)
        val started = awaitState("the continued entry runs") { it is TimerState.Running && it.entryId == STARTED_ID } as TimerState.Running
        assertEquals("a new timer leaves the chain's time behind", 0L, started.accumulatedSeconds)
    }

    /** Taps until the model takes the tap, which it ignores while the last tap is pending. */
    private fun tapUntilTaken(tap: () -> Unit) {
        val before = changes.calls.size
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (true) {
            onMain(tap)
            if (changes.calls.size > before) return
            if (System.nanoTime() > deadline) throw AssertionError("the tap was never taken, after ${changes.calls}")
            Thread.sleep(POLL_MS)
        }
    }

    @Test
    fun openingTheEditorDuringResumeWaitsForTheNewSegment() {
        controls.value = PAUSED
        awaitState("the paused timer shows") { it is TimerState.Paused }
        changes.gate = CompletableDeferred()
        val opening = runBlocking(mainDispatcher) {
            model.resume()
            scope.async(start = CoroutineStart.UNDISPATCHED) { model.runningForEdit() }
        }
        assertFalse(opening.isCompleted)
        changes.gate.complete(Unit)
        val identity = runBlocking { withTimeout(TIMEOUT_MS) { opening.await() } }
        assertNotNull(identity)
        assertEquals(RunningEntryIdentity(CLONE_ID, WORKSPACE, TAP), identity)
        val shown = awaitState("the resumed segment shows") { it is TimerState.Running && it.entryId == CLONE_ID } as TimerState.Running
        assertEquals(PAUSED.seconds, shown.accumulatedSeconds)
        assertEquals(listOf("resume $PAUSED_TARGET null"), changes.calls)
    }

    @Test
    fun aRefusedResumeOpensNoRunningEditor() {
        controls.value = PAUSED
        awaitState("the paused timer shows") { it is TimerState.Paused }
        changes.gate = CompletableDeferred()
        val opening = runBlocking(mainDispatcher) {
            model.resume()
            scope.async(start = CoroutineStart.UNDISPATCHED) { model.runningForEdit() }
        }
        controls.value = TimerControls.Idle
        changes.gate.complete(Unit)
        assertNull(runBlocking { withTimeout(TIMEOUT_MS) { opening.await() } })
    }

    @Test
    fun focusedCallbacksCannotPauseStopOrEditAReplacement() {
        controls.value = running(accumulatedSeconds = 600)
        val old = awaitState("old focused context") { it is TimerState.Running } as TimerState.Running
        val next = running().let { it.copy(entry = it.entry.copy(id = 99), target = RUNNING.copy(entryId = 99)) }
        controls.value = next
        awaitState("replacement context") { it is TimerState.Running && it.entryId == 99L }
        onMain {
            model.pauseShown(old.target!!)
            model.stopShown(old.target!!)
        }
        assertNull(runBlocking(mainDispatcher) { model.runningForEditShown(old.target!!) })
        assertEquals(emptyList<String>(), changes.calls)
        assertEquals(next.target, model.state.value.target)
    }

    @Test
    fun focusedPauseAndResumeKeepCumulativeTimeAndFenceOldRevision() {
        controls.value = running(accumulatedSeconds = 600)
        val running = awaitState("running context") { it is TimerState.Running } as TimerState.Running
        onMain { model.pauseShown(running.target!!) }
        val paused = awaitState("settled paused context") { it is TimerState.Paused && it.target?.let(model::isShown) == true } as TimerState.Paused
        assertEquals(690L, paused.seconds)
        val calls = changes.calls.toList()
        onMain { model.resumeShown(running.target!!) }
        assertEquals("stale running callback cannot resume a paused successor", calls, changes.calls)
        onMain { model.resumeShown(paused.target!!) }
        val resumed = awaitState("settled resumed context") { it is TimerState.Running && it.target?.let(model::isShown) == true } as TimerState.Running
        assertEquals(690L, resumed.accumulatedSeconds)
        assertEquals(CLONE_ID, resumed.entryId)
        val identity = runBlocking(mainDispatcher) { model.runningForEditShown(resumed.target!!) }
        assertEquals(RunningEntryIdentity(CLONE_ID, WORKSPACE, TAP), identity)
    }

    @Test
    fun focusedStopExitsOnlyAfterAuthoritativeIdleAndCannotRepeat() {
        controls.value = running()
        val shown = awaitState("running context") { it is TimerState.Running } as TimerState.Running
        changes.gate = CompletableDeferred()
        onMain { model.stopShown(shown.target!!) }
        val pending = awaitIdle()
        assertFalse("optimistic idle must retain focused route", pending.authoritative)
        assertNull(pending.target)
        changes.gate.complete(Unit)
        awaitState("authoritative stop") { it is TimerState.Idle && it.authoritative }
        val calls = changes.calls.toList()
        onMain { model.stopShown(shown.target!!) }
        assertEquals(calls, changes.calls)
        assertEquals(listOf("stop ${shown.target} null"), calls)
    }

    @Test
    fun focusedPausedEditorTargetCannotActAfterAChainReplacement() {
        controls.value = PAUSED
        val old = awaitState("paused context") { it is TimerState.Paused } as TimerState.Paused
        controls.value = PAUSED.copy(target = PAUSED_TARGET.copy(chainId = CHAIN + 1))
        awaitState("replacement paused context") { it is TimerState.Paused && it.target?.chainId == CHAIN + 1 }
        onMain { model.resumeShown(old.target!!); model.stopShown(old.target!!) }
        assertFalse(model.isShown(old.target!!))
        assertEquals(emptyList<String>(), changes.calls)
    }

    private fun awaitIdle(): TimerState.Idle = awaitState("the idle timer shows") { it is TimerState.Idle } as TimerState.Idle

    private fun awaitState(what: String, condition: (TimerState) -> Boolean): TimerState {
        var last: TimerState? = null
        return runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.onEach { last = it }.first(condition) }
        } ?: throw AssertionError("timed out waiting until $what; the last state was $last, after ${changes.calls}")
    }

    /** Waits until the controls the queue shows pass [condition]. */
    private fun awaitControls(what: String, condition: (TimerControls) -> Boolean) {
        runBlocking { withTimeoutOrNull(TIMEOUT_MS) { controls.first(condition) } }
            ?: throw AssertionError("timed out waiting until $what; the controls are ${controls.value}, after ${changes.calls}")
    }

    /** The tap handlers run on the main thread, which [scope] stands in for. */
    private fun onMain(block: () -> Unit) = runBlocking(mainDispatcher) { block() }

    private fun running(accumulatedSeconds: Long = 0) = TimerControls.Running(
        entry = CachedTimeEntry(
            RUNNING_ID, WORKSPACE, FIELDS.projectId, FIELDS.taskId, FIELDS.description,
            TAP.minusSeconds(90), null, FIELDS.billable, FIELDS.tags, emptyList(),
        ),
        unsent = false,
        accumulatedSeconds = accumulatedSeconds,
        target = if (accumulatedSeconds == 0L) RUNNING else RUNNING.copy(chainId = CHAIN, revision = 3),
    )

    private fun CachedTimeEntry.fields() = EntryFields(description, projectId, taskId, tags, billable)

    /**
     * The change queue: each call reads the tap time first, then waits for [gate], then changes
     * the controls, as long as they still show the chain or entry the target names.
     */
    private inner class FakeChanges : EntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        @Volatile
        var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

        /** Pause refuses, as the queue does for a timer the phone no longer shows. */
        @Volatile
        var refusePause = false

        private var revision = PAUSED_TARGET.revision

        override suspend fun pause(target: TimerTarget, fields: EntryFields?): Boolean {
            calls += "pause $target $fields"
            gate.await()
            val shown = controls.value as? TimerControls.Running ?: return false
            if (refusePause || shown.target != target) return false
            controls.value = TimerControls.Paused(
                seconds = shown.seconds(clock.instant()),
                workspaceId = shown.entry.workspaceId,
                fields = fields ?: shown.entry.fields(),
                target = TimerTarget(target.session, CHAIN, ++revision),
            )
            return true
        }

        override suspend fun resume(target: TimerTarget, fields: EntryFields?): Long? {
            calls += "resume $target $fields"
            gate.await()
            val shown = controls.value as? TimerControls.Paused ?: return null
            if (shown.target != target) return null
            val kept = fields ?: shown.fields
            controls.value = TimerControls.Running(
                entry = CachedTimeEntry(
                    CLONE_ID, shown.workspaceId, kept.projectId, kept.taskId, kept.description,
                    clock.instant(), null, kept.billable, kept.tags, emptyList(),
                ),
                unsent = true,
                accumulatedSeconds = shown.seconds,
                target = TimerTarget(target.session, CHAIN, ++revision, CLONE_ID),
            )
            return CLONE_ID
        }

        override suspend fun stop(target: TimerTarget, fields: EntryFields?): Boolean {
            calls += "stop $target $fields"
            gate.await()
            val shown = when (val current = controls.value) {
                is TimerControls.Running -> current.target
                is TimerControls.Paused -> current.target
                TimerControls.Idle -> null
            }
            if (shown != target) return false
            controls.value = TimerControls.Idle
            return true
        }

        override suspend fun editPaused(target: TimerTarget, fields: EntryFields): Boolean {
            calls += "editPaused $target $fields"
            gate.await()
            val shown = controls.value as? TimerControls.Paused ?: return false
            if (shown.target != target) return false
            controls.value = shown.copy(fields = fields)
            return true
        }

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
            calls += "start $workspaceId $fields"
            gate.await()
            controls.value = TimerControls.Running(
                entry = CachedTimeEntry(
                    STARTED_ID, workspaceId, fields.projectId, fields.taskId, fields.description,
                    clock.instant(), null, fields.billable, fields.tags, emptyList(),
                ),
                unsent = true,
                accumulatedSeconds = 0,
                target = TimerTarget(SESSION, entryId = STARTED_ID),
            )
            return STARTED_ID
        }

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean =
            throw AssertionError("these timers stop by their target")

        override suspend fun runningEntry(identity: RunningEntryIdentity): CachedTimeEntry? =
            (controls.value as? TimerControls.Running)?.entry?.takeIf {
                it.id == identity.entryId && it.workspaceId == identity.workspaceId && it.start == identity.start
            }

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("these timers edit no running entry")

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("the timer creates no stopped entry")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("the timer deletes nothing")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** Long enough for the model to show a cache change. */
        const val SETTLE_PAUSE_MS = 300L
        const val POLL_MS = 10L
        const val WORKSPACE = 10L
        const val SESSION = 77L
        const val CHAIN = 88L
        const val RUNNING_ID = 42L
        const val CLONE_ID = -2L
        const val STARTED_ID = -3L
        val TAP: Instant = Instant.parse("2026-10-19T10:00:00.250Z")
        val SETTINGS = AccountSettings(
            timeZone = "Europe/Madrid",
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
            defaultWorkspaceId = WORKSPACE,
        )
        val APP = CachedProject(1, WORKSPACE, clientId = null, name = "Tokkl for Android", color = "#0b83d9", active = true, billable = false)
        val DESIGN = CachedTask(5, WORKSPACE, projectId = APP.id, name = "Design", active = true)
        val FIELDS = EntryFields("Pause the timer", projectId = APP.id, taskId = DESIGN.id, tags = listOf("android"))
        val RUNNING = TimerTarget(SESSION, entryId = RUNNING_ID)
        val PAUSED_TARGET = TimerTarget(SESSION, CHAIN, revision = 5)
        val PAUSED = TimerControls.Paused(seconds = 1_500, workspaceId = WORKSPACE, fields = FIELDS, target = PAUSED_TARGET)

        /** A stopped entry in the default workspace, which Continue starts again. */
        val PAST = CachedTimeEntry(
            id = 7,
            workspaceId = WORKSPACE,
            projectId = APP.id,
            taskId = null,
            description = "Review the mockups",
            start = Instant.parse("2026-10-18T08:00:00Z"),
            stop = Instant.parse("2026-10-18T09:30:00Z"),
            billable = true,
            tags = listOf("review"),
            tagIds = emptyList(),
        )
    }
}
