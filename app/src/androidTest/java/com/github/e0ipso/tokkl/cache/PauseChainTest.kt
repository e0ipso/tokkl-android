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
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The pause chain (docs/PRD.md T1) on the phone's database: Pause stops the running entry with a
 * captured Stop and keeps its fields, Resume starts a new entry with them, and each control acts
 * only on the timer it showed, in the session it was shown in.
 */
class PauseChainTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = AtomicReference(T0)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
    private lateinit var database: CacheDatabase
    private lateinit var queue: ChangeQueue
    private val dao get() = database.changes()

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        queue = ChangeQueue(
            database,
            Dispatchers.IO,
            onQueued = {},
            clock = clock,
            // A one-minute grid, on which a span under a minute is a short Stop.
            capturedStopSpan = { start, at ->
                val minutes = Duration.between(start, at).toMinutes()
                if (minutes < 1) null else SnappedSpan(start, start.plus(minutes, ChronoUnit.MINUTES))
            },
        )
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun eachResumeStartsANewEntryWithTheKeptFieldsAndTheClockGoesOnFromTheSnappedTotal() = runBlocking {
        val first = queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(10 * 60 + 40))
        assertTrue(queue.pause(running().target))

        val paused = controls() as TimerControls.Paused
        assertEquals("the stored span's whole minutes", 600L, paused.seconds)
        assertEquals(FIELDS, paused.fields)
        assertEquals(WORKSPACE, paused.workspaceId)
        assertEquals(T0.plusSeconds(600), dao.entry(first)!!.stop)

        now.set(T0.plusSeconds(15 * 60))
        val clone = queue.resume(paused.target)!!
        assertNotEquals("Resume never reopens the stopped entry", first, clone)
        val resumed = running()
        assertEquals(clone, resumed.entry.id)
        assertEquals(T0.plusSeconds(15 * 60), resumed.entry.start)
        assertEquals(FIELDS, resumed.entry.fields())
        assertEquals(600L, resumed.accumulatedSeconds)
        assertEquals(paused.target.chainId, resumed.target.chainId)

        now.set(T0.plusSeconds(25 * 60))
        assertTrue(queue.pause(resumed.target))
        assertEquals(1_200L, (controls() as TimerControls.Paused).seconds)
        assertEquals("both segments stay stopped", emptyList<CachedTimeEntry>(), dao.runningEntries())
    }

    @Test
    fun aShortSegmentAddsNothingOffersItsUndoAndStaysResumable() = runBlocking {
        val first = queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(30))
        assertTrue(queue.pause(running().target))

        val paused = controls() as TimerControls.Paused
        assertEquals(0L, paused.seconds)
        assertEquals(first, dao.shortStop(first)?.entryId)
        assertNotNull(queue.resume(paused.target))
        assertEquals(0L, running().accumulatedSeconds)
    }

    @Test
    fun anEditWhilePausedChangesTheNextEntryAndNotTheStoppedOne() = runBlocking {
        val first = queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        val queued = queue.unsentCount.first()

        assertTrue(queue.editPaused((controls() as TimerControls.Paused).target, FIELDS.copy(description = "Edited while paused", tags = listOf("docs", "docs"))))

        val edited = FIELDS.copy(description = "Edited while paused", tags = listOf("docs"))
        val paused = controls() as TimerControls.Paused
        assertEquals(edited, paused.fields)
        assertEquals("the edit queues nothing", queued, queue.unsentCount.first())
        assertEquals(FIELDS.description, dao.entry(first)!!.description)
        val clone = queue.resume(paused.target)!!
        assertEquals(edited, dao.entry(clone)!!.fields())
    }

    @Test
    fun aControlActsOnlyOnTheTimerItShowed() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        val shownRunning = running().target
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(shownRunning))
        assertFalse("a second Pause", queue.pause(shownRunning))

        val shownPaused = (controls() as TimerControls.Paused).target
        assertNull("another session's Resume", queue.resume(shownPaused.copy(session = shownPaused.session + 1)))
        assertNull("an older revision's Resume", queue.resume(shownPaused.copy(revision = shownPaused.revision - 1)))
        val clone = queue.resume(shownPaused)
        assertNull("a second Resume", queue.resume(shownPaused))
        assertEquals("one clone runs", listOf(clone), dao.runningEntries().map { it.id })
        assertFalse("a Stop shown while paused", queue.stop(shownPaused))
        assertEquals(clone, running().entry.id)
    }

    @Test
    fun twoResumesAtOnceStartOneEntry() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        val shown = (controls() as TimerControls.Paused).target

        val clones = (1..2).map { async(Dispatchers.IO) { queue.resume(shown) } }.awaitAll()

        assertEquals(listOf(null), clones.filter { it == null })
        assertEquals(clones.filterNotNull(), dao.runningEntries().map { it.id })
    }

    @Test
    fun aPausedChainSurvivesTheProcess() {
        context.deleteDatabase(FILE)
        val shown = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build().let { file ->
            try {
                val before = ChangeQueue(file, Dispatchers.IO, onQueued = {}, clock = clock)
                runBlocking {
                    before.start(WORKSPACE, FIELDS)
                    now.set(T0.plusSeconds(600))
                    assertTrue(before.pause((before.timerControls.first() as TimerControls.Running).target))
                    before.timerControls.first() as TimerControls.Paused
                }
            } finally {
                file.close()
            }
        }

        val reopened = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val after = ChangeQueue(reopened, Dispatchers.IO, onQueued = {}, clock = clock)
            assertEquals(shown, runBlocking { after.timerControls.first() })
            assertNotNull("the same target resumes it", runBlocking { after.resume(shown.target) })
        } finally {
            reopened.close()
            assertTrue(context.deleteDatabase(FILE))
        }
    }

    @Test
    fun stopWhilePausedEndsTheChainWithoutARequest() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        val queued = queue.unsentCount.first()

        assertTrue(queue.stop((controls() as TimerControls.Paused).target))

        assertEquals(TimerControls.Idle, controls())
        assertNull(dao.pauseChain())
        assertEquals("nothing goes to the server", queued, queue.unsentCount.first())
    }

    @Test
    fun anotherStartOrTheClonesStopEndsTheChain() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        now.set(T0.plusSeconds(900))
        queue.start(WORKSPACE, OTHER)
        assertNull("a new timer ends the chain", dao.pauseChain())
        assertEquals(0L, running().accumulatedSeconds)

        now.set(T0.plusSeconds(1_500))
        assertTrue(queue.pause(running().target))
        now.set(T0.plusSeconds(1_800))
        assertNotNull(queue.resume((controls() as TimerControls.Paused).target))
        now.set(T0.plusSeconds(2_400))
        assertTrue(queue.stop(running().target))
        assertNull("Stop ends the chain", dao.pauseChain())
        assertEquals(TimerControls.Idle, controls())
    }

    @Test
    fun discardingTheClonesRefusedStartPausesTheChainAgain() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        val paused = controls() as TimerControls.Paused
        now.set(T0.plusSeconds(900))
        val clone = queue.resume(paused.target)!!
        val create = dao.changesFor(clone).single()
        dao.hold(create.sequence, "Server validation failed")

        assertTrue(queue.discard(create.sequence))

        val again = controls() as TimerControls.Paused
        assertEquals(paused.seconds, again.seconds)
        assertEquals(paused.fields, again.fields)
        assertNotEquals("a Resume shown before acts on nothing", paused.target.revision, again.target.revision)
        assertNull(dao.entry(clone))
    }

    @Test
    fun theServersIdForTheCloneKeepsItInTheChain() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        now.set(T0.plusSeconds(900))
        val clone = queue.resume((controls() as TimerControls.Paused).target)!!

        RoomChangeStore(database).sent(dao.changesFor(clone).single(), serverId = 9_001, answer = null)

        val resumed = running()
        assertEquals(9_001L, resumed.entry.id)
        assertEquals(600L, resumed.accumulatedSeconds)
        assertEquals(9_001L, dao.pauseChain()!!.runningId)
    }

    @Test
    fun aRefreshEndsThePausedChainOnlyWhenATimerRunsElsewhere() = runBlocking {
        val cache = AccountCache(database, clock)
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))

        cache.checkedRunning(cache.runningCheckStart(), server = null)
        assertTrue("nothing runs on the server either", controls() is TimerControls.Paused)

        val elsewhere = CachedTimeEntry(5_000, WORKSPACE, null, null, "Started on the web", T0.plusSeconds(700), null, false, emptyList(), emptyList())
        cache.checkedRunning(cache.runningCheckStart(), server = elsewhere)

        assertNull(dao.pauseChain())
        val shown = running()
        assertEquals(elsewhere, shown.entry)
        assertEquals(0L, shown.accumulatedSeconds)
        assertEquals(0L, shown.target.chainId)
    }

    @Test
    fun aRefreshReadBeforeAResumeLeavesTheChainAlone() = runBlocking {
        val cache = AccountCache(database, clock)
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        val before = cache.runningCheckStart()
        now.set(T0.plusSeconds(900))
        val clone = queue.resume((controls() as TimerControls.Paused).target)!!

        cache.checkedRunning(before, server = null)

        assertEquals(clone, dao.pauseChain()!!.runningId)
        assertEquals(600L, running().accumulatedSeconds)
    }

    @Test
    fun signOutDropsTheChainAndAControlShownBeforeActsOnNothing() = runBlocking {
        queue.start(WORKSPACE, FIELDS)
        now.set(T0.plusSeconds(600))
        assertTrue(queue.pause(running().target))
        val shown = (controls() as TimerControls.Paused).target

        AccountCache(database, clock).clear()

        assertNull(dao.pauseChain())
        assertNotEquals("a new session", shown.session, dao.timerSession())
        assertNull(queue.resume(shown))
        assertEquals(TimerControls.Idle, controls())
    }

    private suspend fun controls(): TimerControls = queue.timerControls.first()

    private suspend fun running(): TimerControls.Running = controls() as TimerControls.Running

    private companion object {
        const val FILE = "pause-chain-test.db"
        const val WORKSPACE = 7L
        val T0: Instant = Instant.parse("2026-10-23T09:00:00Z")
        val FIELDS = EntryFields("Pause the timer", projectId = 3, tags = listOf("android"), billable = true)
        val OTHER = EntryFields("Something else")
    }
}
