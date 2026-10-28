package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
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
    fun correctingAResumedSegmentChangesOnlyItsElapsedContribution() {
        runBlocking {
            val fields = EntryFields("Pause chain")
            val first = queue.start(WORKSPACE, fields)
            queue = ChangeQueue(database, Dispatchers.IO, onQueued = {}, clock = Clock.fixed(START.plusSeconds(600), ZoneOffset.UTC))
            val running = queue.timerControls.first() as TimerControls.Running
            assertTrue(queue.pause(running.target))
            val stopped = database.changes().entry(first)!!
            val paused = queue.timerControls.first() as TimerControls.Paused
            assertEquals(600L, paused.seconds)
            val resumedAt = START.plusSeconds(900)
            queue = ChangeQueue(database, Dispatchers.IO, onQueued = {}, clock = Clock.fixed(resumedAt, ZoneOffset.UTC))
            val clone = queue.resume(paused.target)!!
            val before = queue.timerControls.first() as TimerControls.Running
            val chain = database.changes().pauseChain()!!
            val corrected = resumedAt.minusSeconds(61)
            assertTrue(queue.updateRunning(RunningEntryIdentity(clone, WORKSPACE, resumedAt), fields, corrected))

            val after = queue.timerControls.first() as TimerControls.Running
            assertEquals("stored segments and chain identity are unchanged", chain, database.changes().pauseChain())
            assertEquals(stopped, database.changes().entry(first))
            assertEquals(before.accumulatedSeconds, after.accumulatedSeconds)
            assertEquals(before.seconds(resumedAt.plusSeconds(60)) + 61, after.seconds(resumedAt.plusSeconds(60)))
            assertEquals(clone, after.entry.id)
            assertEquals(corrected, after.entry.start)
            assertNull(after.entry.stop)
            assertEquals(listOf(ChangeKind.CREATE, ChangeKind.UPDATE), database.changes().changesFor(clone).map { it.kind })
        }
    }

    @Test
    fun pauseAndResumeCannotMakeAnEarlierEditorsIdentityCurrentAgain() {
        runBlocking {
            val first = queue.start(WORKSPACE, EntryFields("Original"))
            val original = identity(first)
            val shown = queue.timerControls.first() as TimerControls.Running
            assertTrue(queue.pause(shown.target))
            val paused = queue.timerControls.first() as TimerControls.Paused
            val beforePause = database.changes().changesFor(first)
            assertFalse(queue.updateRunning(original, EntryFields("Stale while paused"), START))
            assertEquals(beforePause, database.changes().changesFor(first))
            assertEquals(paused, queue.timerControls.first())
            val clone = queue.resume(paused.target)!!
            val beforeResume = database.changes().changesFor(clone)
            assertFalse(queue.updateRunning(original, EntryFields("Stale after Resume"), START))
            assertEquals(beforeResume, database.changes().changesFor(clone))
            assertEquals(beforePause, database.changes().changesFor(first))
            assertEquals(listOf(clone), database.changes().runningEntries().map { it.id })
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
        val START: Instant = Instant.parse("2026-10-22T22:03:41.123Z")
    }
}
