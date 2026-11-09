package com.github.e0ipso.tokkl.timernotice

import android.database.sqlite.SQLiteException
import androidx.work.ListenableWorker
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.timerstate.FixedTimerState
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * The one result policy of the timer controls outside the app's screens, with the storage and
 * the server held by the test: nothing they refuse passes as done.
 */
class SurfaceCommandTest {

    private val told = mutableListOf<String>()

    private val failures = object : TimerFailureNotice {
        override suspend fun show(result: TimerActionResult) {
            told += "failed $result"
        }

        override suspend fun showNotStored() {
            told += "not stored"
        }
    }

    /** What reading the timer controls does: give [controls] or throw what [storage] holds. */
    private var storage: Exception? = null
    private var controls: TimerControls = TimerControls.Idle
    private var answer: TimerActionResult = TimerActionResult.Done

    private val actions = TimerSurfaceActions(
        state = FixedTimerState(controls = { storage?.let { throw it } ?: controls }, account = ACCOUNT),
        changes = Changes(),
        server = Server(),
        sender = {},
    )

    @Test
    fun aStorageFailureIsReportedAndTheWorkFails() = runTest {
        storage = SQLiteException("disk I/O error")

        val outcome = actions.runFromSurface(TimerSurfaceAction.Resume, TimerTarget.Paused(3, ACCOUNT), failures)

        assertTrue(outcome is SurfaceCommandResult.NotStored)
        assertEquals(listOf("not stored"), told)
        assertTrue(outcome.toWorkResult() is ListenableWorker.Result.Failure)
    }

    @Test
    fun aDiskFailureOfACommandIsReportedToo() = runTest {
        val outcome = runSurfaceCommand(failures) { throw IOException("disk full") }

        assertTrue(outcome is SurfaceCommandResult.NotStored)
        assertEquals(listOf("not stored"), told)
    }

    @Test
    fun aRefusedActionIsReportedAndTheWorkFails() = runTest {
        controls = paused(revision = 3)
        answer = TimerActionResult.Failed(ApiError.AuthFailed)

        val outcome = actions.runFromSurface(TimerSurfaceAction.Resume, TimerTarget.Paused(3, ACCOUNT), failures)

        assertEquals(SurfaceCommandResult.Failed(answer), outcome)
        assertEquals(listOf("failed $answer"), told)
        assertTrue(outcome.toWorkResult() is ListenableWorker.Result.Failure)
    }

    @Test
    fun aSentActionAndAStaleTapCompleteQuietly() = runTest {
        controls = paused(revision = 3)

        val sent = actions.runFromSurface(TimerSurfaceAction.Resume, TimerTarget.Paused(3, ACCOUNT), failures)
        val stale = actions.runFromSurface(TimerSurfaceAction.Resume, TimerTarget.Paused(3, "another account"), failures)

        assertEquals(SurfaceCommandResult.Completed, sent)
        assertEquals(SurfaceCommandResult.Completed, stale)
        assertEquals(emptyList<String>(), told)
        assertTrue(sent.toWorkResult() is ListenableWorker.Result.Success)
    }

    private fun paused(revision: Long) = TimerControls.Paused(
        seconds = 600,
        workspaceId = WorkspaceId(7),
        fields = EntryFields("Paused work"),
        target = TimerTarget.Paused(revision, ACCOUNT),
    )

    private inner class Server : PauseActions {
        override suspend fun pause(): TimerActionResult = answer

        override suspend fun resume(revision: Long): TimerActionResult = answer

        override suspend fun discard(revision: Long): TimerActionResult = answer
    }

    private class Changes : RunningEntryChanges {
        override suspend fun currentId(entryId: EntryId): EntryId = entryId

        override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId =
            throw AssertionError("nothing starts")

        override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean = throw AssertionError("nothing stops")

        override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
            throw AssertionError("unexpected runningEntry")

        override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean =
            throw AssertionError("unexpected updateRunning")
    }

    private companion object {
        const val ACCOUNT = "this account"
    }
}
