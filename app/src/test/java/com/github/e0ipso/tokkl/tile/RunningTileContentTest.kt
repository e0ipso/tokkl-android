package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the Quick Settings tile shows for the cache, and what a tap on it queues. */
class RunningTileContentTest {

    private val calls = mutableListOf<String>()

    @Test
    fun aRunningEntryMakesTheTileStopIt() {
        val running = running(4)
        val content = runningTileContent(running, listOf(stopped(1, "2026-10-19T08:00:00Z"), running))

        assertEquals(RunningTileContent.Running(running), content)
        runBlocking { RecordingChanges().tapTile(content) }
        assertEquals(listOf("stop 4"), calls)
    }

    @Test
    fun withNoneRunningTheTileContinuesTheStoppedEntryThatStartedLast() {
        val last = stopped(2, "2026-10-19T11:00:00Z")
        val content = runningTileContent(null, listOf(stopped(1, "2026-10-19T08:00:00Z"), last, stopped(3, "2026-10-18T15:00:00Z")))

        assertEquals(RunningTileContent.Continue(last), content)
        runBlocking { RecordingChanges().tapTile(content) }
        assertEquals(listOf("start 20 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"), calls)
    }

    @Test
    fun tiedSnappedStartsContinueTheNewestLocalEntryBeforeServerEntries() {
        val server = stopped(42, "2026-10-19T11:00:00Z")
        val earlier = server.copy(id = -2, description = "Earlier local entry")
        val last = server.copy(
            id = -4,
            workspaceId = 50,
            description = "Last local entry",
            projectId = 100,
            taskId = 200,
            tags = listOf("one", "two"),
            billable = false,
        )
        // Room's id DESC puts server entries and older negative local IDs first.
        val content = runningTileContent(null, listOf(server, earlier, last))

        assertEquals(RunningTileContent.Continue(last), content)
        assertEquals(content, runningTileContent(null, listOf(last, earlier, server)))
        runBlocking { RecordingChanges().tapTile(content) }
        assertEquals(listOf("start 50 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"), calls)
    }

    @Test
    fun tiedServerStartsContinueTheNewestServerId() {
        val earlier = stopped(41, "2026-10-19T11:00:00Z")
        val last = stopped(42, "2026-10-19T11:00:00Z")

        assertEquals(RunningTileContent.Continue(last), runningTileContent(null, listOf(earlier, last)))
    }

    @Test
    fun withNothingToContinueTheTileDoesNothing() {
        val content = runningTileContent(null, emptyList())

        assertEquals(RunningTileContent.Nothing, content)
        runBlocking { RecordingChanges().tapTile(content) }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aTapActsOnWhatTheTileShowsNotOnWhatTheCacheHoldsByNow() {
        // The tile showed the entry to continue; the cache has since got a running entry from
        // elsewhere. The tap still continues what the tile showed.
        val last = stopped(1, "2026-10-19T08:00:00Z")
        val shown = runningTileContent(null, listOf(last))
        val cacheByNow = runningTileContent(running(4), listOf(last, running(4)))

        runBlocking { RecordingChanges().tapTile(shown) }

        assertEquals(RunningTileContent.Running(running(4)), cacheByNow)
        assertEquals(listOf("start 10 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"), calls)
    }

    private fun stopped(id: Long, start: String) = CachedTimeEntry(
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

    private fun running(id: Long) = stopped(id, "2026-10-19T12:00:00Z").copy(stop = null)

    private inner class RecordingChanges : EntryChanges {
        override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
            calls += "start $workspaceId $fields"
            return -1L
        }

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean {
            calls += "stop $entryId"
            return true
        }

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("nothing is created")

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("nothing is edited")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("nothing is deleted")
    }
}
