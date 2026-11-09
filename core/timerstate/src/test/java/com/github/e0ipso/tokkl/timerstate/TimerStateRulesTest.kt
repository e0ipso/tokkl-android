package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The timer state's acceptance and cache reconciliation rules, apart from the source that orders them. */
class TimerStateRulesTest {

    @Test
    fun aLowerRevisionNeverReplacesTheHeldStateAndTheSameOneDoes() {
        assertTrue("the first state", acceptsRevision(null, 1))
        assertFalse(acceptsRevision(held(revision = 5), 4))
        assertTrue("refreshes the arrival time", acceptsRevision(held(revision = 5), 5))
        assertTrue(acceptsRevision(held(revision = 5), 6))
    }

    @Test
    fun aTakenDiscardNoticeDoesNotShowAgain() {
        val discarded = DiscardedPomodoro(EntryId(9), T0, T0.plusSeconds(30))
        val state = held(revision = 1).copy(discardedPomodoro = discarded)

        assertEquals(discarded, state.unshownDiscard(acknowledged = null))
        assertNull(state.unshownDiscard(acknowledged = 9))
    }

    /** A read sees the cache as it was; the writes of states accepted since count as server rows. */
    @Test
    fun theWritesAcceptedSinceAReadApplyToWhatItRead() {
        val before = RunningCheckStart(listOf(entry(1, "Read")), pendingIds = setOf(EntryId(1)))
        val history = RecentWrites(limit = 2)
        history.add(RunningCheckWrites(upsert = entry(2, "Older")))
        val seen = history.count
        history.add(RunningCheckWrites(delete = listOf(EntryId(1)), upsert = entry(3, "Newer")))

        val reconciled = before.withWrites(history.since(seen))

        assertEquals(listOf(entry(3, "Newer")), reconciled.running)
        assertEquals("the queue's ids stay as read", setOf(EntryId(1)), reconciled.pendingIds)
        assertEquals("nothing since the latest", emptyList<RunningCheckWrites>(), history.since(history.count))
    }

    @Test
    fun theHistoryKeepsOnlyTheLatestWrites() {
        val history = RecentWrites(limit = 2)
        repeat(3) { history.add(RunningCheckWrites(upsert = entry(it.toLong(), "Write $it"))) }

        assertEquals(3, history.count)
        assertEquals(listOf(1L, 2L), history.since(0).map { it.upsert!!.id.value })
    }

    private fun held(revision: Long) = HeldTimerState(revision, T0, null, null, null, null, T0)

    private fun entry(id: Long, description: String) = TimeEntry(
        EntryId(id),
        WorkspaceId(7),
        null,
        null,
        description,
        EntryTiming.Running(T0),
        false,
        emptyList(),
        emptyList(),
    )

    private companion object {
        val T0: Instant = Instant.parse("2026-11-09T10:00:00Z")
    }
}
