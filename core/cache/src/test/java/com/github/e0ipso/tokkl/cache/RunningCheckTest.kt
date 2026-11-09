package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** What the running entry check writes to the cache for each answer (docs/PRD.md 6.3). */
class RunningCheckTest {

    @Test
    fun aTimerTheServerNoLongerRunsLeavesTheCache() {
        val running = entry(1)

        assertEquals(RunningCheckWrites(delete = listOf(EntryId(1))), writes(before = listOf(running), server = null))
    }

    @Test
    fun aTimerStartedElsewhereReplacesTheOneItStopped() {
        val running = entry(1)
        val elsewhere = entry(2, description = "Started elsewhere")

        assertEquals(
            RunningCheckWrites(delete = listOf(EntryId(1)), upsert = elsewhere),
            writes(before = listOf(running), server = elsewhere),
        )
    }

    @Test
    fun theServersValuesReplaceTheRunningEntrysRow() {
        val running = entry(1)
        val edited = entry(1, description = "Edited elsewhere")

        assertEquals(RunningCheckWrites(upsert = edited), writes(before = listOf(running), server = edited))
        assertEquals(
            "the same values write nothing",
            RunningCheckWrites(),
            writes(before = listOf(running), server = running),
        )
    }

    @Test
    fun anEntryWithUnsentChangesStaysAsItIs() {
        val running = entry(1, description = "Edited on the phone")

        assertEquals(RunningCheckWrites(), writes(before = listOf(running), server = null, unsent = setOf(1L)))
        assertEquals(
            RunningCheckWrites(),
            writes(before = listOf(running), server = entry(1, description = "Older"), unsent = setOf(1L)),
        )
    }

    @Test
    fun theServersEntryIsNotAddedWhileAnUnsentTimerRunsOnThePhone() {
        // The phone's start is on its way, and the server may already run it under its own id.
        val started = entry(-4)

        assertEquals(RunningCheckWrites(), writes(before = listOf(started), server = entry(9), unsent = setOf(-4L)))
    }

    @Test
    fun theServersEntryWithUnsentChangesIsNotWritten() {
        val running = entry(1)

        assertEquals(
            "the cached running entry still goes",
            RunningCheckWrites(delete = listOf(EntryId(1))),
            writes(
                before = listOf(running),
                server = entry(2),
                unsent = setOf(2L),
                cached = listOf(running, entry(2, stop = STOP)),
            ),
        )
    }

    @Test
    fun aRowWrittenAfterTheRequestStays() {
        val before = entry(1)
        val edited = entry(1, description = "Edited and sent meanwhile")

        assertEquals(
            "an edit that reached the cache after the request",
            RunningCheckWrites(),
            writes(before = listOf(before), now = listOf(edited), server = null),
        )
        assertEquals(
            "a start that reached the cache after the request",
            RunningCheckWrites(),
            writes(before = emptyList(), now = listOf(entry(3)), server = null),
        )
    }

    @Test
    fun aChangeQueuedWhenTheRequestBeganStaysAfterItWasSent() {
        // The edit was queued before the request and sent during it, so the answer may predate it.
        val edited = entry(1, description = "Edited on the phone")

        assertEquals(
            "a stale answer does not undo the edit",
            RunningCheckWrites(),
            writes(before = listOf(edited), server = entry(1, description = "Older"), pendingAtStart = setOf(1L)),
        )
        assertEquals(
            "nor drops the timer",
            RunningCheckWrites(),
            writes(before = listOf(edited), server = null, pendingAtStart = setOf(1L)),
        )
    }

    @Test
    fun aDeleteSentDuringTheRequestIsNotAddedBack() {
        val running = entry(1)

        assertEquals(
            RunningCheckWrites(),
            writes(
                before = emptyList(),
                now = emptyList(),
                server = running,
                cached = emptyList(),
                pendingAtStart = setOf(1L),
            ),
        )
    }

    @Test
    fun aStopOnThePhoneAfterTheRequestIsNotUndone() {
        val running = entry(1)
        val stopped = entry(1, stop = STOP)

        assertEquals(
            RunningCheckWrites(),
            writes(before = listOf(running), now = emptyList(), server = running, cached = listOf(stopped)),
        )
    }

    @Test
    fun anEntryDeletedOnThePhoneAfterTheRequestIsNotAddedBack() {
        val running = entry(1)

        assertEquals(
            RunningCheckWrites(),
            writes(before = listOf(running), now = emptyList(), server = running, cached = emptyList()),
        )
    }

    @Test
    fun aTimerTheCacheDidNotHoldIsAdded() {
        val elsewhere = entry(2)

        assertEquals(RunningCheckWrites(upsert = elsewhere), writes(before = emptyList(), server = elsewhere))
    }

    @Test
    fun anOlderAnswerDoesNotAddASecondTimerAfterANewStartReachedTheCache() {
        val before = entry(1)
        val started = entry(3)
        assertEquals(
            RunningCheckWrites(),
            writes(before = listOf(before), now = listOf(started), server = entry(2)),
        )
    }

    @Test
    fun aFailedReadOfTheRunningEntryReadsItAgainWithoutEndingTheCheck() = runTest {
        val calls = mutableListOf<String>()
        var reads = 0
        val running = flow {
            reads++
            if (reads == 1) throw IOException("cache unreadable")
            emit(true)
            awaitCancellation()
        }

        val following = launch { followRunning(running, { calls += "schedule" }, { calls += "cancel" }) }
        advanceTimeBy(1_001)

        assertEquals(listOf("schedule"), calls)
        following.cancel()
    }

    /** The writes for [server], with [now] the running entries and [cached] every row when the answer arrives. */
    private fun writes(
        before: List<TimeEntry>,
        server: TimeEntry?,
        now: List<TimeEntry> = before,
        unsent: Set<Long> = emptySet(),
        cached: List<TimeEntry> = now,
        pendingAtStart: Set<Long> = emptySet(),
    ): RunningCheckWrites = runningCheckWrites(
        before = before,
        now = now,
        server = server,
        unsent = { it.value in unsent },
        cached = { id -> cached.firstOrNull { it.id == id } },
        pendingAtStart = pendingAtStart.mapTo(HashSet(), ::EntryId),
    )

    private fun entry(id: Long, description: String = "Running", stop: Instant? = null) = TimeEntry(
        id = EntryId(id),
        workspaceId = WorkspaceId(7),
        projectId = null,
        taskId = null,
        description = description,
        start = START,
        stop = stop,
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private companion object {
        val START: Instant = Instant.parse("2026-10-20T08:00:00Z")
        val STOP: Instant = Instant.parse("2026-10-20T08:30:00Z")
    }
}
