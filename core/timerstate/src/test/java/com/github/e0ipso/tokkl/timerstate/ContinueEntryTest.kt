package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Continue-last, which the Quick Settings tile uses: the stopped entry that started last, as the
 * web app's "c" shortcut picks it, starts again with its fields in its workspace, through the
 * change queue's start.
 */
class ContinueEntryTest {

    private val calls = mutableListOf<String>()

    @Test
    fun continueLastStartsTheStoppedEntryThatStartedLast() = runTest {
        // Out of order, and the running entry started after every stopped one.
        val entries = listOf(
            entry(1, "2026-10-19T08:00:00Z"),
            entry(2, "2026-10-19T11:00:00Z"),
            entry(3, "2026-10-18T15:00:00Z"),
            running(4),
        )

        val id = run { RecordingChanges().continueLast(entries) }

        assertEquals(EntryId(LOCAL_ID), id)
        assertEquals(listOf("start 20 ${entry(2, "2026-10-19T11:00:00Z").fieldsOf()}"), calls)
    }

    @Test
    fun withoutAStoppedEntryNothingStarts() = runTest {
        assertNull(lastStopped(emptyList()))
        assertNull(run { RecordingChanges().continueLast(listOf(running(4))) })
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun tiedLocalAndServerStartsPreferTheNewestQueuedCreateInEveryOrder() {
        val entries = listOf(
            entry(42, "2026-10-19T11:00:00Z"),
            entry(-2, "2026-10-19T11:00:00Z"),
            entry(-4, "2026-10-19T11:00:00Z"),
        )

        for (order in permutations(entries)) {
            assertEquals("input IDs: ${order.map { it.id }}", entries.last(), lastStopped(order))
        }
    }

    @Test
    fun tiedServerStartsPreferTheLargerIdInEitherOrder() {
        val older = entry(41, "2026-10-19T11:00:00Z")
        val newer = entry(42, "2026-10-19T11:00:00Z")

        assertEquals(newer, lastStopped(listOf(older, newer)))
        assertEquals(newer, lastStopped(listOf(newer, older)))
    }

    @Test
    fun aLaterStartWinsOverCreationIdsAndRunningEntriesAreExcluded() {
        val later = entry(1, "2026-10-19T11:01:00Z")
        val entries = listOf(entry(-4, "2026-10-19T11:00:00Z"), entry(42, "2026-10-19T11:00:00Z"), later, running(-6))

        for (order in permutations(entries)) {
            assertEquals(later, lastStopped(order))
        }
    }

    @Test
    fun continueLastCopiesEveryFieldOfTheNewestTiedLocalEntry() = runTest {
        val server = entry(42, "2026-10-19T11:00:00Z")
        val older = server.copy(id = EntryId(-2), description = "Older local entry")
        val newer = server.copy(
            id = EntryId(-4),
            workspaceId = WorkspaceId(50),
            description = "Newest local entry",
            projectId = ProjectId(100),
            taskId = TaskId(200),
            tags = listOf("one", "two"),
            billable = true,
        )

        for (order in permutations(listOf(server, older, newer))) {
            calls.clear()
            assertEquals(EntryId(LOCAL_ID), run { RecordingChanges().continueLast(order) })
            assertEquals(listOf("start 50 ${newer.fieldsOf()}"), calls)
        }
    }

    private fun <T> permutations(items: List<T>): List<List<T>> = if (items.isEmpty()) {
        listOf(emptyList())
    } else {
        items.indices.flatMap { index ->
            permutations(items.filterIndexed { i, _ -> i != index }).map { listOf(items[index]) + it }
        }
    }

    private fun TimeEntry.fieldsOf() = EntryFields(description, projectId, taskId, tags, billable)

    private fun entry(id: Long, start: String) = TimeEntry(
        id = EntryId(id),
        workspaceId = WorkspaceId(id * 10),
        projectId = ProjectId(id + 100),
        taskId = TaskId(id + 200),
        description = "Entry $id",
        timing = EntryTiming.Stopped(Instant.parse(start), Instant.parse(start).plusSeconds(1800)),
        billable = id % 2 == 0L,
        tags = listOf("tag $id"),
        tagIds = emptyList(),
    )

    private fun running(id: Long) = entry(
        id,
        "2026-10-19T12:00:00Z",
    ).let { it.copy(timing = EntryTiming.Running(it.start)) }

    private inner class RecordingChanges : RunningEntryChanges {
        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId {
            calls += "start $workspaceId $fields"
            return EntryId(LOCAL_ID)
        }

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean = throw AssertionError(
            "nothing stops",
        )

        override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
            throw AssertionError("unexpected runningEntry")

        override suspend fun updateRunning(
            identity: RunningEntryIdentity,
            fields: EntryFields,
            start: Instant,
        ): Boolean = throw AssertionError("unexpected updateRunning")

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }

    private companion object {
        const val LOCAL_ID = -5L
    }
}
