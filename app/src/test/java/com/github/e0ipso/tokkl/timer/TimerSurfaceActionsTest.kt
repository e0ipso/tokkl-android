package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one policy behind the native timer controls: what a tap on the running notification, the
 * widget, the tile or the reminder sends, to the queue or to the server, for the target it was
 * drawn with, and what it leaves alone.
 */
class TimerSurfaceActionsTest {

    private val calls = mutableListOf<String>()
    private var now: TimerControls = TimerControls.Idle
    private var answer: TimerActionResult = TimerActionResult.Done
    private val mappings = mutableMapOf<Long, Long>()

    /** While set, the server's Pause answers only once it completes. */
    private var held: CompletableDeferred<Unit>? = null

    private val actions = TimerSurfaceActions(
        controls = { now },
        account = { ACCOUNT },
        changes = Queue(),
        server = Server(),
        sendQueued = { calls += "send queue" },
    )

    @Test
    fun pauseOfTheShownEntryGoesToTheServerAndQueuesNothing() {
        now = running(id = 42)

        val tap = tap(TimerSurfaceAction.Pause, running(id = 42).target)

        assertTrue(tap is SurfaceTap.Request)
        assertTrue("nothing goes out before the surface sends it", calls.isEmpty())
        assertEquals(TimerActionResult.Done, runBlocking { (tap as SurfaceTap.Request).send() })
        assertEquals(listOf("pause"), calls)
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

        now = TimerControls.Paused(600, 1, EntryFields("Paused"), TimerTarget.server(revision = 8, account = ACCOUNT))
        assertNull("the timer was paused meanwhile", tap(TimerSurfaceAction.Pause, running(id = 42).target))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun pauseOfAPhoneMadeEntryFollowsItsServerId() {
        mappings[-5] = 42
        now = running(id = 42)

        val tap = tap(TimerSurfaceAction.Pause, running(id = -5).target) as SurfaceTap.Request
        runBlocking { tap.send() }

        assertEquals(listOf("pause"), calls)
    }

    @Test
    fun pauseOfAnUnsentEntrySendsTheQueueFirst() {
        now = running(id = -5, unsent = true)

        runBlocking { (tap(TimerSurfaceAction.Pause, running(id = -5).target) as SurfaceTap.Request).send() }

        assertEquals("the server runs the shown entry before it pauses it", listOf("send queue", "pause"), calls)
    }

    @Test
    fun resumeSendsTheRevisionTheControlWasDrawnAt() {
        now = paused(revision = 9)

        val tap = tap(TimerSurfaceAction.Resume, paused(revision = 7).target) as SurfaceTap.Request
        answer = TimerActionResult.Refused

        assertEquals("the server refuses a tap on a state it moved past", TimerActionResult.Refused, runBlocking { tap.send() })
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
    fun stopWhilePausedDiscardsAtTheDrawnRevision() {
        now = paused(revision = 9)

        val tap = tap(TimerSurfaceAction.Stop, paused(revision = 9).target) as SurfaceTap.Request
        runBlocking { tap.send() }

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
    fun aTapWhileAServerActionIsOnItsWaySendsNothingButStopStillQueues() = runBlocking {
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
        assertEquals(listOf("pause", "stop 42"), calls)
        assertTrue("the next tap goes out again", actions.tap(TimerSurfaceAction.Pause, target) is SurfaceTap.Request)
    }

    @Test
    fun aTapOnThePublishedNextStateWaitsForThePreviousRequestToReturn() = runBlocking {
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
            assertEquals("server calls stay serialized", listOf("pause"), calls)
            gate.complete(Unit)
            assertEquals(TimerActionResult.Done, sending.await())
            assertEquals(TimerActionResult.Done, resuming.await())
            assertEquals(listOf("pause", "resume 9"), calls)
        } finally {
            gate.complete(Unit)
        }
    }

    @Test
    fun aWaitingActionDoesNothingIfItsStateChangesAgain() = runBlocking {
        now = running(id = 42)
        val gate = CompletableDeferred<Unit>()
        held = gate
        val first = actions.tap(TimerSurfaceAction.Pause, running(id = 42).target) as SurfaceTap.Request
        val sending = async(start = CoroutineStart.UNDISPATCHED) { first.send() }
        try {
            now = paused(revision = 9)
            val next = actions.tap(TimerSurfaceAction.Resume, paused(revision = 9).target) as SurfaceTap.Request
            val resuming = async(start = CoroutineStart.UNDISPATCHED) { next.send() }
            assertNull("a duplicate of the waiting action", actions.tap(TimerSurfaceAction.Resume, paused(revision = 9).target))
            now = running(id = 43)
            gate.complete(Unit)
            sending.await()
            assertNull("the waiting control is now stale", resuming.await())
            assertEquals("only the original action reached the server", listOf("pause"), calls)
        } finally {
            gate.complete(Unit)
        }
    }

    private fun tap(action: TimerSurfaceAction, target: TimerTarget): SurfaceTap? = runBlocking { actions.tap(action, target) }

    private fun running(id: Long, pausable: Boolean = true, unsent: Boolean = false, accumulatedSeconds: Long = 0) = TimerControls.Running(
        entry = CachedTimeEntry(id, 1, null, null, "Work", Instant.parse("2026-10-28T09:00:00Z"), null, false, emptyList(), emptyList()),
        unsent = unsent,
        accumulatedSeconds = accumulatedSeconds,
        target = TimerTarget.server(entryId = id, account = ACCOUNT),
        pausable = pausable,
    )

    private fun paused(revision: Long) =
        TimerControls.Paused(600, 1, EntryFields("Paused"), TimerTarget.server(revision = revision, account = ACCOUNT))

    private inner class Server : PauseActions {
        override suspend fun pause(): TimerActionResult {
            calls += "pause"
            held?.await()
            return answer
        }

        override suspend fun resume(revision: Long): TimerActionResult = answer.also { calls += "resume $revision" }
        override suspend fun discard(revision: Long): TimerActionResult = answer.also { calls += "discard $revision" }
    }

    private inner class Queue : EntryChanges {
        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("nothing starts")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean {
            calls += "stop $entryId"
            return (now as? TimerControls.Running)?.entry?.id == entryId
        }

        override suspend fun currentId(entryId: Long): Long = mappings[entryId] ?: entryId

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("nothing is created")

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("nothing is edited")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("nothing is deleted")
    }

    private companion object {
        const val ACCOUNT = "this account"
    }
}
