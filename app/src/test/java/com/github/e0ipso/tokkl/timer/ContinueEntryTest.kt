package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Continue-last, which the Quick Settings tile uses: the stopped entry that started last, as the
 * web app's "c" shortcut picks it, starts again with its fields in its workspace, through the
 * change queue's start.
 */
class ContinueEntryTest {

    private val calls = mutableListOf<String>()

    @Test
    fun continueLastStartsTheStoppedEntryThatStartedLast() {
        // Out of order, and the running entry started after every stopped one.
        val entries = listOf(entry(1, "2026-10-19T08:00:00Z"), entry(2, "2026-10-19T11:00:00Z"), entry(3, "2026-10-18T15:00:00Z"), running(4))

        val id = runBlocking { RecordingChanges().continueLast(entries) }

        assertEquals(LOCAL_ID, id)
        assertEquals(listOf("start 20 ${entry(2, "2026-10-19T11:00:00Z").fieldsOf()}"), calls)
    }

    @Test
    fun withoutAStoppedEntryNothingStarts() {
        assertNull(lastStopped(emptyList()))
        assertNull(runBlocking { RecordingChanges().continueLast(listOf(running(4))) })
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun tiedLocalAndServerStartsPreferTheNewestQueuedCreateInEveryOrder() {
        val entries = listOf(entry(42, "2026-10-19T11:00:00Z"), entry(-2, "2026-10-19T11:00:00Z"), entry(-4, "2026-10-19T11:00:00Z"))

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
    fun continueLastCopiesEveryFieldOfTheNewestTiedLocalEntry() {
        val server = entry(42, "2026-10-19T11:00:00Z")
        val older = server.copy(id = -2, description = "Older local entry")
        val newer = server.copy(
            id = -4,
            workspaceId = 50,
            description = "Newest local entry",
            projectId = 100,
            taskId = 200,
            tags = listOf("one", "two"),
            billable = true,
        )

        for (order in permutations(listOf(server, older, newer))) {
            calls.clear()
            assertEquals(LOCAL_ID, runBlocking { RecordingChanges().continueLast(order) })
            assertEquals(listOf("start 50 ${newer.fieldsOf()}"), calls)
        }
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.isEmpty()) listOf(emptyList()) else items.indices.flatMap { index ->
            permutations(items.filterIndexed { i, _ -> i != index }).map { listOf(items[index]) + it }
        }

    private fun CachedTimeEntry.fieldsOf() = EntryFields(description, projectId, taskId, tags, billable)

    private fun entry(id: Long, start: String) = CachedTimeEntry(
        id = id,
        workspaceId = id * 10,
        projectId = id + 100,
        taskId = id + 200,
        description = "Entry $id",
        start = Instant.parse(start),
        stop = Instant.parse(start).plusSeconds(1800),
        billable = id % 2 == 0L,
        tags = listOf("tag $id"),
        tagIds = emptyList(),
    )

    private fun running(id: Long) = entry(id, "2026-10-19T12:00:00Z").copy(stop = null)

    private inner class RecordingChanges : EntryChanges {
        override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
            calls += "start $workspaceId $fields"
            return LOCAL_ID
        }

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("nothing stops")

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("nothing is created")

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("nothing is edited")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("nothing is deleted")
    }

    private companion object {
        const val LOCAL_ID = -5L
    }
}
