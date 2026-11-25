package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.model.EntryId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The revision a Pomodoro Start sends: the one held at the tap, or the one the phone's own queued
 * changes produced, read after sending them, and none while a session shows. Pause's choice is
 * checked through the surfaces that use it, in [TimerSurfaceActionsTest].
 */
class TimerRevisionsTest {

    private val calls = mutableListOf<String>()
    private val held: MutableStateFlow<HeldTimerState?> = MutableStateFlow(idle(revision = 3))

    /** What the timer read does, as [TimerActions.read] gives its answer to the holder. */
    private var onRead: () -> TimerActionResult = { error("this start reads nothing") }

    private val revisions = TimerRevisions(
        held = held,
        read = {
            calls += "read"
            onRead()
        },
        sender = { calls += "send queue" },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startAfterQueuedChangesSendsThemAndTakesTheRevisionTheyMade() = runTest {
        onRead = {
            held.value = idle(revision = 4)
            TimerActionResult.Done
        }

        assertEquals(RevisionChoice.Send(4), revisions.forStart(queued = { true }))
        assertEquals(listOf("send queue", "read"), calls)

        // A queued change the server refused moves nothing, and the start waits no longer for it.
        calls.clear()
        onRead = { TimerActionResult.Done }
        val before = testScheduler.currentTime

        assertEquals(RevisionChoice.Send(4), revisions.forStart(queued = { true }))
        assertEquals(listOf("send queue", "read"), calls)
        assertEquals("the wait gives up after 3 s", 3_000L, testScheduler.currentTime - before)
    }

    @Test
    fun startWithNothingQueuedSendsTheHeldRevisionAndReadsOnlyWithoutOne() = runTest {
        assertEquals(RevisionChoice.Send(3), revisions.forStart(queued = { false }))
        assertTrue("no read and no queue", calls.isEmpty())

        // A phone that has read no timer state yet.
        held.value = null
        onRead = { TimerActionResult.Offline }
        assertEquals(RevisionChoice.Skip(TimerActionResult.Offline), revisions.forStart(queued = { false }))

        onRead = {
            held.value = idle(revision = 6)
            TimerActionResult.Done
        }
        assertEquals(RevisionChoice.Send(6), revisions.forStart(queued = { false }))
        assertEquals(listOf("read", "read"), calls)
    }

    @Test
    fun startWhileASessionShowsIsRefusedAndSendsNothing() = runTest {
        held.value = inSession(revision = 5)
        assertEquals(RevisionChoice.Skip(TimerActionResult.Refused), revisions.forStart(queued = { false }))
        assertTrue(calls.isEmpty())

        // Another device started a session before the phone's queued changes reached the server.
        held.value = idle(revision = 5)
        onRead = {
            held.value = inSession(revision = 7)
            TimerActionResult.Done
        }
        assertEquals(RevisionChoice.Skip(TimerActionResult.Refused), revisions.forStart(queued = { true }))
        assertEquals(listOf("send queue", "read"), calls)
    }

    private fun idle(revision: Long): HeldTimerState = ServerTimerStates.held(revision, AT)

    private fun inSession(revision: Long): HeldTimerState =
        ServerTimerStates.held(revision, AT, pomodoro = ServerTimerStates.session(EntryId(40), AT))

    private companion object {
        val AT: Instant = Instant.parse("2026-10-28T09:00:00Z")
    }
}
