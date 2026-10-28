package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.TimerPause
import com.github.e0ipso.tokkl.timerstate.TimerState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The real Room transaction and durable ID mapping, in a disposable database. No sender runs. */
class RunningEntryQueueTest {
    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CacheDatabase
    private lateinit var queue: ChangeQueue
    private val clock = Clock.fixed(START, ZoneOffset.UTC)

    @Before
    fun open() {
        context.deleteDatabase(FILE)
        reopen()
    }

    @After
    fun close() {
        database.close()
        assertTrue("disposable database removed", context.deleteDatabase(FILE))
        assertFalse(context.getDatabasePath(FILE).exists())
    }

    @Test
    fun runningUpdateQueuesExactUnsnappedStartAndNullStop() {
        runBlocking {
            val id = queue.start(WORKSPACE, EntryFields("Running"))
            val corrected = START.minusSeconds(793)
            assertTrue(queue.updateRunning(identity(id), EntryFields("Corrected", tags = listOf("offline")), corrected))
            val changes = database.changes().changesFor(id)
            assertEquals(listOf(ChangeKind.CREATE, ChangeKind.UPDATE), changes.map { it.kind })
            val update = changes.last()
            assertEquals(id, update.entryId)
            assertEquals(WORKSPACE, update.workspaceId)
            assertEquals(corrected, update.start)
            assertNull(update.stop)
            assertEquals("Corrected", database.changes().entry(id)!!.description)
            assertEquals(listOf(id), database.changes().runningEntries().map { it.id })
        }
    }

    @Test
    fun originalLocalIdentityResolvesAfterRemapAndDatabaseReopen() {
        runBlocking {
            val id = queue.start(WORKSPACE, EntryFields("Running"))
            val identity = identity(id)
            val create = database.changes().changesFor(id).single()
            val answer = create.entry().copy(id = 201, start = START.truncatedTo(ChronoUnit.SECONDS))
            RoomChangeStore(database).sent(create, serverId = 201, answer = answer)
            database.close()
            reopen()
            assertEquals(201L, queue.runningEntry(identity)!!.id)
            assertTrue(queue.updateRunning(identity, EntryFields("Offline correction"), START.minusSeconds(793)))
            assertNull(database.changes().entry(id))
            val update = database.changes().changesFor(201).single()
            assertEquals(ChangeKind.UPDATE, update.kind)
            assertEquals(WORKSPACE, update.workspaceId)
            assertNull(update.stop)
            assertEquals(listOf(201L), database.changes().runningEntries().map { it.id })
        }
    }

    @Test
    fun stoppedEntryRejectsStaleSaveWithoutAnotherQueuedChange() {
        runBlocking {
            val id = queue.start(WORKSPACE, EntryFields("Running"))
            assertTrue(queue.stop(id))
            val before = database.changes().changesFor(id)
            assertFalse(queue.updateRunning(identity(id), EntryFields("Stale"), START.minusSeconds(793)))
            assertEquals(before, database.changes().changesFor(id))
            assertNotNull(database.changes().entry(id)!!.stop)
        }
    }

    @Test
    fun sameInstantReplacementRejectsTheOriginalIdentity() {
        runBlocking {
            val id = queue.start(WORKSPACE, EntryFields("Original"))
            val replacement = queue.start(WORKSPACE, EntryFields("Replacement"))
            val before = database.changes().changesFor(id)
            assertFalse(queue.updateRunning(identity(id), EntryFields("Stale"), START.minusSeconds(793)))
            assertEquals(before, database.changes().changesFor(id))
            assertEquals(listOf(replacement), database.changes().runningEntries().map { it.id })
            assertEquals("Replacement", database.changes().entry(replacement)!!.description)
        }
    }

    @Test
    fun workspaceMismatchCannotWrite() {
        runBlocking {
            val id = queue.start(WORKSPACE, EntryFields("Running"))
            val before = database.changes().changesFor(id)
            assertFalse(queue.updateRunning(identity(id).copy(workspaceId = 999), EntryFields("Wrong workspace"), START))
            assertEquals(before, database.changes().changesFor(id))
        }
    }

    @Test
    fun competingStartCorrectionInvalidatesAnOlderEditor() {
        runBlocking {
            val id = queue.start(WORKSPACE, EntryFields("Running"))
            assertTrue(queue.updateRunning(identity(id), EntryFields("First edit"), START.minusSeconds(793)))
            val before = database.changes().changesFor(id)
            assertFalse(queue.updateRunning(identity(id), EntryFields("Stale"), START.minusSeconds(100)))
            assertEquals(before, database.changes().changesFor(id))
            assertEquals(START.minusSeconds(793), database.changes().entry(id)!!.start)
        }
    }

    @Test
    fun shortStopAndItsUndoCannotReviveTheRunningEditor() {
        runBlocking {
            queue = ChangeQueue(database, Dispatchers.IO, onQueued = {}, clock = clock, capturedStopSpan = { _, _ -> null })
            val id = queue.start(WORKSPACE, EntryFields("Short"))
            assertTrue(queue.stop(id))
            assertFalse(queue.updateRunning(identity(id), EntryFields("Stale"), START))
            assertTrue(queue.undoShortStop(id))
            val before = database.changes().changesFor(id)
            assertFalse(queue.updateRunning(identity(id), EntryFields("Stale after undo"), START))
            assertEquals(before, database.changes().changesFor(id))
            assertNotNull(database.changes().entry(id)!!.stop)
        }
    }

    @Test
    fun correctingTheServersResumedCloneChangesOnlyItsOwnElapsedTime() {
        runBlocking {
            val resumedAt = START.plusSeconds(900)
            // The server resumed the chain with this clone, and its state keeps 600 s from before.
            database.changes().upsertEntry(
                CachedTimeEntry(CLONE, WORKSPACE, null, null, "Pause chain", resumedAt, null, false, emptyList(), emptyList()),
            )
            val pause = TimerPause(
                paused = false, seconds = 600, workspaceId = WORKSPACE, description = "Pause chain", projectId = null,
                taskId = null, tagIds = emptyList(), tags = emptyList(), billable = false, cloneEntryId = CLONE,
            )
            val clone = ServerTimeEntry(id = CLONE, workspaceId = WORKSPACE, description = "Pause chain", start = resumedAt.toString())
            val held = HeldTimerState(TimerState(4, resumedAt, clone, pause, pomodoro = null, discardedPomodoro = null), receivedAt = resumedAt)
            fun controls() = serverTimerControls(database.changes().latestRunning(), held, timerApi = true) as TimerControls.Running
            val before = controls()
            val corrected = resumedAt.minusSeconds(61)
            assertTrue(queue.updateRunning(RunningEntryIdentity(CLONE, WORKSPACE, resumedAt), EntryFields("Pause chain"), corrected))

            val after = controls()
            assertEquals("the server's stored seconds stay", 600L, after.accumulatedSeconds)
            assertEquals(before.seconds(resumedAt.plusSeconds(60)) + 61, after.seconds(resumedAt.plusSeconds(60)))
            assertEquals(CLONE, after.entry.id)
            assertEquals(corrected, after.entry.start)
            assertNull(after.entry.stop)
            assertTrue("the edit waits for the sender", after.unsent)
            assertEquals(listOf(ChangeKind.UPDATE), database.changes().changesFor(CLONE).map { it.kind })
        }
    }

    @Test
    fun aRunningEntryAnswerKeepsAStartAndAnEditSentDuringItsRead() {
        runBlocking {
            val cache = AccountCache(database, clock)
            val store = RoomChangeStore(database)
            val local = queue.start(WORKSPACE, EntryFields("Running"))
            val startRead = cache.runningCheckStart()
            store.sent(database.changes().changesFor(local).single(), serverId = 501, answer = null)

            cache.checkedRunning(startRead, server = null)
            assertEquals("the sent start stays", listOf(501L), database.changes().runningEntries().map { it.id })

            assertTrue(queue.updateRunning(identity(501), EntryFields("Edited on the phone"), START))
            val editRead = cache.runningCheckStart()
            val stale = database.changes().entry(501)!!.copy(description = "Running")
            store.sent(database.changes().changesFor(501).single(), serverId = 501, answer = null)

            cache.checkedRunning(editRead, server = stale)
            assertEquals("Edited on the phone", database.changes().entry(501)!!.description)

            cache.checkedRunning(cache.runningCheckStart(), server = stale)
            assertEquals("a read begun after the send takes the server's values", "Running", database.changes().entry(501)!!.description)
        }
    }

    private fun reopen() {
        database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        queue = ChangeQueue(database, Dispatchers.IO, onQueued = {}, clock = clock)
    }

    private fun identity(id: Long) = RunningEntryIdentity(id, WORKSPACE, START)

    private companion object {
        const val FILE = "running-entry-editor-test.db"
        const val WORKSPACE = 20L
        const val CLONE = 9_001L
        val START: Instant = Instant.parse("2026-10-22T22:03:41.123Z")
    }
}
