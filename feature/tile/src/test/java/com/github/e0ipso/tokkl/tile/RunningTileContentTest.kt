package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.timerstate.FixedTimerState
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.ServerTimerStates
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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

        assertEquals(
            RunningTileContent.Running(
                running,
                TimerTarget.server(entryId = EntryId(4), account = ServerTimerStates.ACCOUNT),
            ),
            content,
        )
        assertEquals("queued, nothing goes to the server", SurfaceTap.Queued, tap(content))
        assertEquals(listOf("stop 4"), calls)
    }

    @Test
    fun aResumedCloneStopsThroughTheQueue() {
        val clone = running(55)
        now = ServerTimerStates.controls(
            clone,
            ServerTimerStates.held(9, start, ServerTimerStates.resumed(600, entryId = EntryId(55))),
        )

        val content = runningTileContent(now, listOf(clone))

        assertEquals(
            RunningTileContent.Running(
                clone,
                TimerTarget.server(entryId = EntryId(55), account = ServerTimerStates.ACCOUNT),
            ),
            content,
        )
        tap(content)
        assertEquals("a v9 Stop, which ends the chain on the server", listOf("stop 55"), calls)
    }

    @Test
    fun withNoneRunningTheTileContinuesTheStoppedEntryThatStartedLast() {
        val last = stopped(2, "2026-10-19T11:00:00Z")
        val content = runningTileContent(
            TimerControls.Idle,
            listOf(stopped(1, "2026-10-19T08:00:00Z"), last, stopped(3, "2026-10-18T15:00:00Z")),
        )

        assertEquals(RunningTileContent.Continue(last), content)
        tap(content)
        assertEquals(
            listOf("start 20 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"),
            calls,
        )
    }

    @Test
    fun tiedSnappedStartsContinueTheNewestLocalEntryBeforeServerEntries() {
        val server = stopped(42, "2026-10-19T11:00:00Z")
        val earlier = server.copy(id = EntryId(-2), description = "Earlier local entry")
        val last = server.copy(
            id = EntryId(-4),
            workspaceId = WorkspaceId(50),
            description = "Last local entry",
            projectId = ProjectId(100),
            taskId = TaskId(200),
            tags = listOf("one", "two"),
            billable = false,
        )
        // Room's id DESC puts server entries and older negative local IDs first.
        val content = runningTileContent(TimerControls.Idle, listOf(server, earlier, last))

        assertEquals(RunningTileContent.Continue(last), content)
        assertEquals(content, runningTileContent(TimerControls.Idle, listOf(last, earlier, server)))
        tap(content)
        assertEquals(
            listOf("start 50 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"),
            calls,
        )
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

        assertEquals(
            listOf("start 10 ${EntryFields(last.description, last.projectId, last.taskId, last.tags, last.billable)}"),
            calls,
        )
    }

    @Test
    fun aPausedTimerMakesTheTileResumeTheChainOnTheServerAtTheShownRevision() = runTest {
        now = ServerTimerStates.controls(
            null,
            ServerTimerStates.held(3, start, ServerTimerStates.paused(600, EntryFields("Paused work"))),
        )
        val content = runningTileContent(now, listOf(stopped(1, "2026-10-19T08:00:00Z")))

        assertEquals(
            RunningTileContent.Paused(
                "Paused work",
                TimerTarget.server(revision = 3, account = ServerTimerStates.ACCOUNT),
            ),
            content,
        )
        val request = tap(content) as SurfaceTap.Request
        assertTrue("nothing queued", calls.isEmpty())
        assertEquals(TimerActionResult.Offline, run { request.send() })
        assertEquals(listOf("resume 3"), calls)
    }

    @Test
    fun aPausedTileTappedAfterTheChainResumedElsewhereSendsNothing() {
        now = ServerTimerStates.controls(
            null,
            ServerTimerStates.held(3, start, ServerTimerStates.paused(600, EntryFields("Paused work"))),
        )
        val shown = runningTileContent(now, emptyList())
        now = ServerTimerStates.controls(
            running(55),
            ServerTimerStates.held(4, start, ServerTimerStates.resumed(600, entryId = EntryId(55))),
        )

        assertNull(tap(shown))
        assertTrue(calls.isEmpty())
    }

    private fun tap(content: RunningTileContent): SurfaceTap? = runBlocking {
        val changes = RecordingChanges()
        tapTile(
            content,
            TimerSurfaceActions(FixedTimerState(controls = { now }), changes, RecordingServer(), sender = {}),
            changes,
        )
    }

    private fun stopped(id: Long, start: String) = TimeEntry(
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

    private fun running(id: Long) = stopped(
        id,
        "2026-10-19T12:00:00Z",
    ).let { it.copy(timing = EntryTiming.Running(it.start)) }

    /** The server answers no action: the phone is offline. */
    private inner class RecordingServer : PauseActions {
        override suspend fun pause(): TimerActionResult = TimerActionResult.Offline.also { calls += "pause" }
        override suspend fun resume(revision: Long): TimerActionResult = TimerActionResult.Offline.also {
            calls +=
                "resume $revision"
        }
        override suspend fun discard(revision: Long): TimerActionResult = TimerActionResult.Offline.also {
            calls +=
                "discard $revision"
        }
    }

    private inner class RecordingChanges : RunningEntryChanges {
        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId {
            calls += "start $workspaceId $fields"
            return EntryId(-1L)
        }

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
            calls += "stop $entryId"
            return true
        }

        override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
            throw AssertionError("unexpected runningEntry")

        override suspend fun updateRunning(
            identity: RunningEntryIdentity,
            fields: EntryFields,
            start: Instant,
        ): Boolean = throw AssertionError("unexpected updateRunning")

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }
}
