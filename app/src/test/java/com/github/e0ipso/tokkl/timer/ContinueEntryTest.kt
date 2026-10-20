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
