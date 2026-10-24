package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Refused starts and segment checkpoints in the real Room queue, without a network sender. */
class PauseChainDiscardQueueTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = AtomicReference(T0)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private lateinit var database: CacheDatabase
    private lateinit var queue: ChangeQueue
    private var fileCreated = false
    private val dao get() = database.changes()

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        connect()
    }

    @After
    fun close() {
        database.close()
        if (fileCreated) {
            assertTrue("the disposable checkpoint fixture is removed", context.deleteDatabase(FILE))
            assertFalse(context.getDatabasePath(FILE).exists())
        }
    }

    @Test
    fun discardingAnAlreadyPausedRefusedResumeRestoresAllFieldsAndTheNextResumeTotal() = runBlocking {
        val before = pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(before.target, CHANGED)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        assertTrue(queue.editPaused(paused().target, CHANGED.copy(description = "Edited after pause")))
        val unrelated = queue.create(WORKSPACE, EntryFields("Unrelated"), T0.minusSeconds(120), T0.minusSeconds(60))
        val unrelatedChanges = dao.changesFor(unrelated)
        val keptChanges = dao.changesFor(BASE_ENTRY)
        val create = holdCreate(refused)

        assertTrue(queue.discard(create.sequence))

        val after = paused()
        assertEquals(before.seconds, after.seconds)
        assertEquals(before.fields, after.fields)
        assertEquals(before.workspaceId, after.workspaceId)
        assertNotEquals(before.target.revision, after.target.revision)
        assertNull(dao.entry(refused))
        assertTrue(dao.changesFor(refused).isEmpty())
        assertEquals(unrelatedChanges, dao.changesFor(unrelated))
        assertEquals(keptChanges, dao.changesFor(BASE_ENTRY))
        val chain = dao.pauseChain()
        assertFalse("a repeat discard does nothing", queue.discard(create.sequence))
        assertEquals(chain, dao.pauseChain())
        assertNull("the old target is stale", queue.resume(before.target))

        now.set(T0.plusSeconds(1_500))
        val resumed = queue.resume(after.target)!!
        assertEquals(FIELDS, dao.entry(resumed)!!.fields())
        assertEquals(WORKSPACE, dao.entry(resumed)!!.workspaceId)
        assertEquals(600L, running().accumulatedSeconds)
    }

    @Test
    fun aStillRunningRefusedResumeReturnsToItsPreStartMetadata() = runBlocking {
        val before = pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(before.target, CHANGED)!!
        val create = holdCreate(refused)

        assertTrue(queue.discard(create.sequence))

        assertEquals(before.seconds, paused().seconds)
        assertEquals(FIELDS, paused().fields)
        assertNull(dao.entry(refused))
        assertTrue(dao.runningEntries().isEmpty())
    }

    @Test
    fun discardingAnEarlierRefusedSegmentPreservesALaterAnsweredSegmentWithTheSameFields() = runBlocking {
        pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(paused().target)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        val create = holdCreate(refused)
        now.set(T0.plusSeconds(1_500))
        val later = queue.resume(paused().target)!!
        now.set(T0.plusSeconds(1_620))
        assertTrue(queue.pause(running().target))
        val laterCreate = dao.changesFor(later).first { it.kind == ChangeKind.CREATE }
        RoomChangeStore(database).sent(laterCreate, serverId = 9_010, answer = null)
        val laterChanges = dao.changesFor(9_010)
        val chainId = dao.pauseChain()!!.chainId
        assertEquals(120L, dao.pauseSegments(chainId).single { it.entryId == 9_010L }.seconds)

        assertTrue(queue.discard(create.sequence))

        assertEquals(720L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
        assertEquals(laterChanges, dao.changesFor(9_010))
        assertEquals(T0.plusSeconds(1_620), dao.entry(9_010)!!.stop)
        assertTrue(dao.pauseSegments(chainId).single { it.entryId == 9_010L }.paused)
    }

    @Test
    fun anEarlierDiscardKeepsTheLaterRunningEntryAndRebasesItsFutureDiscard() = runBlocking {
        pausedBase()
        now.set(T0.plusSeconds(900))
        val earlier = queue.resume(paused().target, CHANGED)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        val earlierCreate = holdCreate(earlier)
        now.set(T0.plusSeconds(1_500))
        val later = queue.resume(paused().target)!!
        val laterChanges = dao.changesFor(later)

        assertTrue(queue.discard(earlierCreate.sequence))

        assertEquals(later, running().entry.id)
        assertEquals(CHANGED, running().entry.fields())
        assertEquals(600L, running().accumulatedSeconds)
        assertEquals(laterChanges, dao.changesFor(later))
        assertTrue(queue.discard(holdCreate(later).sequence))
        assertEquals(600L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
    }

    @Test
    fun twoPausedDiscardsRemoveEachContributionOnceAndRestoreTheOriginalFields() = runBlocking {
        pausedBase()
        now.set(T0.plusSeconds(900))
        val earlier = queue.resume(paused().target, CHANGED)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        val earlierCreate = holdCreate(earlier)
        now.set(T0.plusSeconds(1_500))
        val later = queue.resume(paused().target, CHANGED.copy(description = "Later kept fields"))!!
        now.set(T0.plusSeconds(1_620))
        assertTrue(queue.pause(running().target))
        val laterCreate = holdCreate(later)

        assertTrue(queue.discard(earlierCreate.sequence))
        assertEquals(720L, paused().seconds)
        assertEquals("Later kept fields", paused().fields.description)
        assertTrue(queue.discard(laterCreate.sequence))

        assertEquals(600L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
        assertFalse(queue.discard(earlierCreate.sequence))
        assertFalse(queue.discard(laterCreate.sequence))
        assertEquals(600L, paused().seconds)
    }

    @Test
    fun editingTheStoppedEntryTimesDoesNotChangeItsRecordedChainContribution() = runBlocking {
        pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(paused().target)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        assertTrue(queue.update(refused, CHANGED, T0.plusSeconds(900), T0.plusSeconds(1_800)))
        assertEquals(T0.plusSeconds(1_800), dao.entry(refused)!!.stop)
        assertEquals(900L, paused().seconds)

        assertTrue(queue.discard(holdCreate(refused).sequence))

        assertEquals(600L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
    }

    @Test
    fun aManualCreateWithCoincidentFieldsAndTimesCannotClaimTheChainsContribution() = runBlocking {
        val before = pausedBase()
        val chain = dao.pauseChain()
        val manual = queue.create(WORKSPACE, FIELDS, T0, T0.plusSeconds(600))

        assertTrue(queue.discard(holdCreate(manual).sequence))

        assertEquals(chain, dao.pauseChain())
        assertEquals(before, paused())
        assertEquals(T0.plusSeconds(600), dao.entry(BASE_ENTRY)!!.stop)
    }

    @Test
    fun discardingAShortPausedCreateAlsoRemovesItsUndoAndQueuedRestore() = runBlocking {
        pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(paused().target, CHANGED)!!
        now.set(T0.plusSeconds(920))
        assertTrue(queue.pause(running().target))
        assertEquals(refused, dao.shortStop(refused)!!.entryId)
        assertTrue(queue.undoShortStop(refused))
        val create = holdCreate(refused)

        assertTrue(queue.discard(create.sequence))

        assertEquals(600L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
        assertNull(dao.shortStop(refused))
        assertNull(dao.entry(refused))
        assertTrue(dao.changesFor(refused).isEmpty())
        assertFalse(queue.undoShortStop(refused))
    }

    @Test
    fun theInitialRefusedStartCanBeDiscardedAfterItsFirstPause() = runBlocking {
        val refused = queue.start(WORKSPACE, FIELDS)
        running()
        now.set(T0.plusSeconds(300))
        assertTrue(queue.pause(running().target, CHANGED))
        val create = holdCreate(refused)

        assertTrue(queue.discard(create.sequence))

        assertEquals(0L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
        assertTrue(dao.runningEntries().isEmpty())
    }

    @Test
    fun checkpointsSurviveReopeningTheDatabaseAndDisappearWhenTheChainEnds() = runBlocking {
        database.close()
        assertFalse("the fixture must have a fresh filename", context.getDatabasePath(FILE).exists())
        fileCreated = true
        database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        connect()
        pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(paused().target, CHANGED)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        val create = holdCreate(refused)
        val chainId = dao.pauseChain()!!.chainId
        database.close()
        database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        connect()

        assertTrue(queue.discard(create.sequence))

        assertEquals(600L, paused().seconds)
        assertEquals(FIELDS, paused().fields)
        assertEquals(listOf(BASE_ENTRY), dao.pauseSegments(chainId).map { it.entryId })
        assertTrue(queue.stop(paused().target))
        assertNull(dao.pauseChain())
        assertTrue(dao.pauseSegments(chainId).isEmpty())
    }

    @Test
    fun discardingARefusedSegmentFromAnEndedChainLeavesANewerChainAlone() = runBlocking {
        pausedBase()
        now.set(T0.plusSeconds(900))
        val refused = queue.resume(paused().target)!!
        now.set(T0.plusSeconds(1_200))
        assertTrue(queue.pause(running().target))
        val create = holdCreate(refused)
        now.set(T0.plusSeconds(1_500))
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(1_800))
        assertTrue(queue.pause(running().target))
        val newer = dao.pauseChain()
        val segments = dao.pauseSegments(newer!!.chainId)

        assertTrue(queue.discard(create.sequence))

        assertEquals(newer, dao.pauseChain())
        assertEquals(segments, dao.pauseSegments(newer.chainId))
        assertEquals(300L, paused().seconds)
    }

    private fun connect() {
        queue = ChangeQueue(
            database, Dispatchers.IO, onQueued = {}, clock = clock,
            capturedStopSpan = { start, at ->
                if (Duration.between(start, at).seconds < 60) null else SnappedSpan(start, at)
            },
        )
    }

    private suspend fun pausedBase(): TimerControls.Paused {
        val local = queue.start(WORKSPACE, FIELDS)
        RoomChangeStore(database).sent(dao.changesFor(local).single(), BASE_ENTRY, answer = null)
        running()
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        return paused()
    }

    private fun holdCreate(entryId: Long): PendingChange {
        val create = dao.changesFor(entryId).first { it.kind == ChangeKind.CREATE }
        dao.hold(create.sequence, "Server validation failed")
        return create
    }

    private suspend fun paused(): TimerControls.Paused = queue.timerControls.first() as TimerControls.Paused
    private suspend fun running(): TimerControls.Running = queue.timerControls.first() as TimerControls.Running

    private companion object {
        const val FILE = "pause-chain-discard-queue-test.db"
        const val WORKSPACE = 7L
        const val BASE_ENTRY = 9_001L
        val T0: Instant = Instant.parse("2026-10-23T09:00:00Z")
        val FIELDS = EntryFields("Kept", projectId = 3, taskId = 9, tags = listOf("android"), billable = true)
        val CHANGED = EntryFields("Refused", projectId = 4, taskId = 10, tags = listOf("other"), billable = false)
    }
}
