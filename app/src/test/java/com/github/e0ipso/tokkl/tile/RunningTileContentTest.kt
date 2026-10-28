package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.ServerTimerStates
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Quick Settings tile shows for the cache and the server's timer state, and what a tap
 * on it queues or sends.
 */
class RunningTileContentTest {

    private val start = Instant.parse("2026-10-19T12:00:00Z")
    private val calls = mutableListOf<String>()

    /** What the phone shows when the tap lands. */
    private var now: TimerControls = TimerControls.Idle

    @Test
    fun aRunningEntryMakesTheTileStopIt() {
        val running = running(4)
        now = ServerTimerStates.controls(running)
        val content = runningTileContent(now, listOf(stopped(1, "2026-10-19T08:00:00Z"), running))

        assertEquals(RunningTileContent.Running(running, TimerTarget.server(entryId = 4, account = ServerTimerStates.ACCOUNT)), content)
        assertEquals("queued, nothing goes to the server", SurfaceTap.Queued, tap(content))
        assertEquals(listOf("stop 4"), calls)
    }

    @Test
    fun aResumedCloneStopsThroughTheQueue() {
        val clone = running(55)
        now = ServerTimerStates.controls(clone, ServerTimerStates.held(9, start, ServerTimerStates.resumed(600, entryId = 55)))

        val content = runningTileContent(now, listOf(clone))

        assertEquals(RunningTileContent.Running(clone, TimerTarget.server(entryId = 55, account = ServerTimerStates.ACCOUNT)), content)
        tap(content)
        assertEquals("a v9 Stop, which ends the chain on the server", listOf("stop 55"), calls)
    }

    @Test
    fun withNoneRunningTheTileContinuesTheStoppedEntryThatStartedLast() {
        val last = stopped(2, "2026-10-19T11:00:00Z")
        val content = runningTileContent(TimerControls.Idle, listOf(stopped(1, "2026-10-19T08:00:00Z"), last, stopped(3, "2026-10-18T15:00:00Z")))

        assertEquals(RunningTileContent.Continue(last), content)
        tap(content)
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
        val content = runningTileContent(TimerControls.Idle, listOf(server, earlier, last))

        assertEquals(RunningTileContent.Continue(last), content)
        assertEquals(content, runningTileContent(TimerControls.Idle, listOf(last, earlier, server)))
        tap(content)
        assertEquals(listOf("start 50 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"), calls)
    }

    @Test
    fun tiedServerStartsContinueTheNewestServerId() {
        val earlier = stopped(41, "2026-10-19T11:00:00Z")
        val last = stopped(42, "2026-10-19T11:00:00Z")

        assertEquals(RunningTileContent.Continue(last), runningTileContent(TimerControls.Idle, listOf(earlier, last)))
    }

    @Test
    fun withNothingToContinueTheTileDoesNothing() {
        val content = runningTileContent(TimerControls.Idle, emptyList())

        assertEquals(RunningTileContent.Nothing, content)
        tap(content)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aTapActsOnWhatTheTileShowsNotOnWhatTheCacheHoldsByNow() {
        // The tile showed the entry to continue; the cache has since got a running entry from
        // elsewhere. The tap still continues what the tile showed.
        val last = stopped(1, "2026-10-19T08:00:00Z")
        val shown = runningTileContent(TimerControls.Idle, listOf(last))
        now = ServerTimerStates.controls(running(4))

        tap(shown)

        assertEquals(listOf("start 10 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"), calls)
    }

    @Test
    fun aPausedTimerMakesTheTileResumeTheChainOnTheServerAtTheShownRevision() {
        now = ServerTimerStates.controls(null, ServerTimerStates.held(3, start, ServerTimerStates.paused(600, EntryFields("Paused work"))))
        val content = runningTileContent(now, listOf(stopped(1, "2026-10-19T08:00:00Z")))

        assertEquals(RunningTileContent.Paused("Paused work", TimerTarget.server(revision = 3, account = ServerTimerStates.ACCOUNT)), content)
        val request = tap(content) as SurfaceTap.Request
        assertTrue("nothing queued", calls.isEmpty())
        assertEquals(TimerActionResult.Offline, runBlocking { request.send() })
        assertEquals(listOf("resume 3"), calls)
    }

    @Test
    fun aPausedTileTappedAfterTheChainResumedElsewhereSendsNothing() {
        now = ServerTimerStates.controls(null, ServerTimerStates.held(3, start, ServerTimerStates.paused(600, EntryFields("Paused work"))))
        val shown = runningTileContent(now, emptyList())
        now = ServerTimerStates.controls(running(55), ServerTimerStates.held(4, start, ServerTimerStates.resumed(600, entryId = 55)))

        assertNull(tap(shown))
        assertTrue(calls.isEmpty())
    }

    private fun tap(content: RunningTileContent): SurfaceTap? = runBlocking {
        val changes = RecordingChanges()
        tapTile(content, TimerSurfaceActions({ now }, { ServerTimerStates.ACCOUNT }, changes, RecordingServer(), sendQueued = {}), changes)
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

    /** The server answers no action: the phone is offline. */
    private inner class RecordingServer : PauseActions {
        override suspend fun pause(): TimerActionResult = TimerActionResult.Offline.also { calls += "pause" }
        override suspend fun resume(revision: Long): TimerActionResult = TimerActionResult.Offline.also { calls += "resume $revision" }
        override suspend fun discard(revision: Long): TimerActionResult = TimerActionResult.Offline.also { calls += "discard $revision" }
    }

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
