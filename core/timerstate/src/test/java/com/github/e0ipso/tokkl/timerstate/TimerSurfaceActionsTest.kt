package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The one policy behind the native timer controls: what a tap on the running notification, the
 * widget, the tile or the reminder sends, to the queue or to the server, for the target it was
 * drawn with, and what it leaves alone.
 */
class TimerSurfaceActionsTest {

    private val calls = mutableListOf<String>()
    private var now: TimerControls = TimerControls.Idle
    private var answer: TimerActionResult = TimerActionResult.Done
    private val mappings = mutableMapOf<EntryId, EntryId>()

    /** The account's held state, which the phone took from the server's last answer or event. */
    private var heldState: HeldTimerState? = held(revision = 7, running = 42)

    /** What the server's timer read does, as [TimerActions.read] gives its answer to the holder. */
    private var onRead: () -> TimerActionResult = { error("this tap reads nothing") }

    /** What sending the queue does besides, such as the server's id for a phone-made entry. */
    private var onSend: () -> Unit = {}

    /** While set, the server's Pause answers only once it completes. */
    private var held: CompletableDeferred<Unit>? = null

    private val actions = TimerSurfaceActions(
        state = FixedTimerState(controls = { now }, account = ACCOUNT, held = { heldState }),
        changes = Queue(),
        server = Server(),
        sender = {
            calls += "send queue"
            onSend()
        },
    )

    @Test
    fun pauseOfTheShownEntryGoesToTheServerAtTheRevisionHeldAtTheTap() = runTest {
        now = running(id = 42)
        heldState = held(revision = 7, running = 42)

        val tap = tap(TimerSurfaceAction.Pause, running(id = 42).target)
        // A state the stream delivers before the request goes out, as a rename on another device makes.
        heldState = held(revision = 8, running = 42)

        assertTrue(tap is SurfaceTap.Request)
        assertTrue("nothing goes out before the surface sends it", calls.isEmpty())
        assertEquals(TimerActionResult.Done, run { (tap as SurfaceTap.Request).send() })
        assertEquals("no read and no queue", listOf("pause 8"), calls)
    }

    @Test
    fun pauseWithNoHeldStateReadsTheTimerFirstAndSendsItsRevision() = runTest {
        // A phone that has read no timer state yet, as before the stream's first event.
        now = running(id = 42)
        heldState = null
        onRead = { TimerActionResult.Offline }

        val offline = tap(TimerSurfaceAction.Pause, running(id = 42).target) as SurfaceTap.Request
        assertEquals("a failed read is what the tap comes to", TimerActionResult.Offline, run { offline.send() })
        assertEquals("nothing is sent without a revision", listOf("read"), calls)

        calls.clear()
        onRead = {
            heldState = held(revision = 12, running = 42)
            TimerActionResult.Done
        }
        val online = tap(TimerSurfaceAction.Pause, running(id = 42).target) as SurfaceTap.Request
        assertEquals(TimerActionResult.Done, run { online.send() })
        assertEquals(listOf("read", "pause 12"), calls)
    }

    @Test
    fun pauseIsNotSentWhereTheControlsNoLongerOfferIt() {
        // During a Pomodoro session, or on a server without the timer API.
        now = running(id = 42, pausable = false)

        assertNull(tap(TimerSurfaceAction.Pause, running(id = 42).target))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun pauseOfAnEntryThatNoLongerRunsSendsNothing() {
        now = running(id = 43)
        assertNull("another entry runs", tap(TimerSurfaceAction.Pause, running(id = 42).target))

        now = TimerControls.Paused(
            600,
            WorkspaceId(1),
            EntryFields("Paused"),
            TimerTarget.Paused(8, ACCOUNT),
        )
        assertNull("the timer was paused meanwhile", tap(TimerSurfaceAction.Pause, running(id = 42).target))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun pauseOfAPhoneMadeEntryFollowsItsServerId() = runTest {
        mappings[EntryId(-5)] = EntryId(42)
        now = running(id = 42)

        val tap = tap(TimerSurfaceAction.Pause, running(id = -5).target) as SurfaceTap.Request
        run { tap.send() }

        assertEquals(listOf("pause 7"), calls)
    }

    @Test
    fun pauseOfAnUnsentEntrySendsTheQueueFirst() = runTest {
        // The phone started -5 while the server held revision 7 with 41 running.
        now = running(id = -5, unsent = true)
        heldState = held(revision = 7, running = 41)
        onSend = { mappings[EntryId(-5)] = EntryId(42) }
        onRead = {
            heldState = held(revision = 8, running = 42)
            TimerActionResult.Done
        }

        val result = run { (tap(TimerSurfaceAction.Pause, running(id = -5).target) as SurfaceTap.Request).send() }

        assertEquals(TimerActionResult.Done, result)
        assertEquals(
            "the server runs the shown entry before it pauses it, at the revision its start made",
            listOf("send queue", "read", "pause 8"),
            calls,
        )
    }

    @Test
    fun pauseOfAnUnsentEntryThatNoLongerRunsOnceSentSendsNothing() = runTest {
        // Another device started 43 before the phone's Start of -5 reached the server.
        now = running(id = -5, unsent = true)
        heldState = held(revision = 7, running = 41)
        onSend = { mappings[EntryId(-5)] = EntryId(42) }
        onRead = {
            heldState = held(revision = 9, running = 43)
            TimerActionResult.Done
        }

        val result = run { (tap(TimerSurfaceAction.Pause, running(id = -5).target) as SurfaceTap.Request).send() }

        assertEquals(TimerActionResult.Refused, result)
        assertEquals(listOf("send queue", "read"), calls)
    }

    @Test
    fun resumeSendsTheRevisionTheControlWasDrawnAt() = runTest {
        now = paused(revision = 9)

        val tap = tap(TimerSurfaceAction.Resume, paused(revision = 7).target) as SurfaceTap.Request
        answer = TimerActionResult.Refused

        assertEquals(
            "the server refuses a tap on a state it moved past",
            TimerActionResult.Refused,
            run { tap.send() },
        )
        assertEquals(listOf("resume 7"), calls)
    }

    @Test
    fun resumeOfATimerNoLongerPausedSendsNothing() {
        now = running(id = 42)

        assertNull(tap(TimerSurfaceAction.Resume, paused(revision = 7).target))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun stopOfARunningEntryIsQueuedAlsoForAResumedClone() {
        now = running(id = 55, accumulatedSeconds = 600)

        val tap = tap(TimerSurfaceAction.Stop, running(id = 55).target)

        assertEquals(SurfaceTap.Queued, tap)
        assertEquals("a v9 Stop through the queue, never Discard", listOf("stop 55"), calls)
    }

    @Test
    fun stopOfAnEntryTheQueueNoLongerRunsDoesNothing() {
        now = TimerControls.Idle

        assertNull(tap(TimerSurfaceAction.Stop, running(id = 77).target))
        assertEquals("the queue checked and found nothing to stop", listOf("stop 77"), calls)
    }

    @Test
    fun stopWhilePausedDiscardsAtTheDrawnRevision() = runTest {
        now = paused(revision = 9)

        val tap = tap(TimerSurfaceAction.Stop, paused(revision = 9).target) as SurfaceTap.Request
        run { tap.send() }

        assertEquals(listOf("discard 9"), calls)
    }

    @Test
    fun stopWhilePausedOfATimerNoLongerPausedSendsNothing() {
        now = TimerControls.Idle

        assertNull(tap(TimerSurfaceAction.Stop, paused(revision = 9).target))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun aControlDrawnForAnotherAccountDoesNothing() {
        now = running(id = 42)
        val other = running(id = 42).target.copy(account = "an earlier account")
        assertNull(tap(TimerSurfaceAction.Pause, other))
        assertNull(tap(TimerSurfaceAction.Stop, other))

        now = paused(revision = 9)
        val otherPaused = paused(revision = 9).target.copy(account = "an earlier account")
        assertNull(tap(TimerSurfaceAction.Resume, otherPaused))
        assertNull(tap(TimerSurfaceAction.Stop, otherPaused))
        assertTrue("nothing queued and nothing sent", calls.isEmpty())
    }

    @Test
    fun aTapWhileAServerActionIsOnItsWaySendsNothingButStopStillQueues() = runTest {
        now = running(id = 42)
        val target = running(id = 42).target
        val gate = CompletableDeferred<Unit>()
        held = gate
        val first = actions.tap(TimerSurfaceAction.Pause, target) as SurfaceTap.Request
        val second = actions.tap(TimerSurfaceAction.Pause, target) as SurfaceTap.Request
        val sending = async(start = CoroutineStart.UNDISPATCHED) { first.send() }

        assertNull("a second quick tap", actions.tap(TimerSurfaceAction.Pause, target))
        assertNull("a request made before the first went out", second.send())
        assertEquals("Stop never waits for the server", SurfaceTap.Queued, actions.tap(TimerSurfaceAction.Stop, target))

        gate.complete(Unit)
        assertEquals(TimerActionResult.Done, sending.await())
        assertEquals(listOf("pause 7", "stop 42"), calls)
        assertTrue("the next tap goes out again", actions.tap(TimerSurfaceAction.Pause, target) is SurfaceTap.Request)
    }

    @Test
    fun aTapOnThePublishedNextStateWaitsForThePreviousRequestToReturn() = runTest {
        now = running(id = 42)
        val gate = CompletableDeferred<Unit>()
        held = gate
        val first = actions.tap(TimerSurfaceAction.Pause, running(id = 42).target) as SurfaceTap.Request
        val sending = async(start = CoroutineStart.UNDISPATCHED) { first.send() }
        try {
            // TimerActions publishes the server response before returning to the caller.
            // A notification can already show Resume while the Pause call is unwinding.
            now = paused(revision = 9)
            val next = actions.tap(TimerSurfaceAction.Resume, paused(revision = 9).target)
            assertTrue("the newly drawn control accepts a tap", next is SurfaceTap.Request)
            val resuming = async(start = CoroutineStart.UNDISPATCHED) { (next as SurfaceTap.Request).send() }
            assertEquals("server calls stay serialized", listOf("pause 7"), calls)
            gate.complete(Unit)
            assertEquals(TimerActionResult.Done, sending.await())
            assertEquals(TimerActionResult.Done, resuming.await())
            assertEquals(listOf("pause 7", "resume 9"), calls)
        } finally {
            gate.complete(Unit)
        }
    }

    @Test
    fun aWaitingActionDoesNothingIfItsStateChangesAgain() = runTest {
        now = running(id = 42)
        val gate = CompletableDeferred<Unit>()
        held = gate
        val first = actions.tap(TimerSurfaceAction.Pause, running(id = 42).target) as SurfaceTap.Request
        val sending = async(start = CoroutineStart.UNDISPATCHED) { first.send() }
        try {
            now = paused(revision = 9)
            val next = actions.tap(TimerSurfaceAction.Resume, paused(revision = 9).target) as SurfaceTap.Request
            val resuming = async(start = CoroutineStart.UNDISPATCHED) { next.send() }
            assertNull(
                "a duplicate of the waiting action",
                actions.tap(TimerSurfaceAction.Resume, paused(revision = 9).target),
            )
            now = running(id = 43)
            gate.complete(Unit)
            sending.await()
            assertNull("the waiting control is now stale", resuming.await())
            assertEquals("only the original action reached the server", listOf("pause 7"), calls)
        } finally {
            gate.complete(Unit)
        }
    }

    private fun tap(action: TimerSurfaceAction, target: TimerTarget): SurfaceTap? = runBlocking {
        actions.tap(
            action,
            target,
        )
    }

    private fun running(id: Long, pausable: Boolean = true, unsent: Boolean = false, accumulatedSeconds: Long = 0) =
        TimerControls.Running(
            entry = entry(id),
            unsent = unsent,
            accumulatedSeconds = accumulatedSeconds,
            target = TimerTarget.Running(EntryId(id), ACCOUNT),
            pausable = pausable,
        )

    private fun entry(id: Long) = TimeEntry(
        EntryId(id), WorkspaceId(1), null, null, "Work",
        EntryTiming.Running(START),
        false, emptyList(), emptyList(),
    )

    /** A state the server sent at [revision] with [running] as its running entry, outside a session. */
    private fun held(revision: Long, running: Long?) = HeldTimerState(
        revision = revision,
        serverTime = START,
        runningEntry = running?.let(::entry),
        pause = null,
        pomodoro = null,
        discardedPomodoro = null,
        receivedAt = START,
    )

    private fun paused(revision: Long) = TimerControls.Paused(
        600,
        WorkspaceId(1),
        EntryFields("Paused"),
        TimerTarget.Paused(revision, ACCOUNT),
    )

    private inner class Server : PauseActions {
        override suspend fun read(): TimerActionResult {
            calls += "read"
            return onRead()
        }

        override suspend fun pause(revision: Long): TimerActionResult {
            calls += "pause $revision"
            held?.await()
            return answer
        }

        override suspend fun resume(revision: Long): TimerActionResult = answer.also { calls += "resume $revision" }
        override suspend fun discard(revision: Long): TimerActionResult = answer.also { calls += "discard $revision" }
    }

    private inner class Queue : RunningEntryChanges {
        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId = throw AssertionError(
            "nothing starts",
        )

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
            calls += "stop $entryId"
            return (now as? TimerControls.Running)?.entry?.id == entryId
        }

        override suspend fun currentId(entryId: EntryId): EntryId = mappings[entryId] ?: entryId

        override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
            throw AssertionError("unexpected runningEntry")

        override suspend fun updateRunning(
            identity: RunningEntryIdentity,
            fields: EntryFields,
            start: Instant,
        ): Boolean = throw AssertionError("unexpected updateRunning")
    }

    private companion object {
        const val ACCOUNT = "this account"
        val START: Instant = Instant.parse("2026-10-28T09:00:00Z")
    }
}
