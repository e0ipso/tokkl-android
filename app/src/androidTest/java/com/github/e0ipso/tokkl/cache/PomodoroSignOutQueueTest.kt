package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.pomodoro.IdlePomodoroAlarms
import com.github.e0ipso.tokkl.pomodoro.MemoryPomodoroPhaseEnds
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroStep
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real Room sign-out snapshots and durable retry cleanup, without a sender or network. */
class PomodoroSignOutQueueTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val filename = "pomodoro-sign-out-${UUID.randomUUID()}.db"
    private val now = AtomicReference(START)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private lateinit var db: CacheDatabase
    private lateinit var queue: ChangeQueue
    private lateinit var store: PomodoroSessionStore
    private lateinit var cache: AccountCache

    @Before fun open() { connect() }

    private fun connect() {
        db = Room.databaseBuilder(context, CacheDatabase::class.java, filename).build()
        queue = ChangeQueue(db, Dispatchers.IO, onQueued = {}, clock = clock, pomodoro = { store })
        store = PomodoroSessionStore.open(
            db, queue, Dispatchers.IO, clock, accountTime = { TIME },
            alarms = IdlePomodoroAlarms, phaseEnds = MemoryPomodoroPhaseEnds(),
        )
        cache = AccountCache(db, clock, pomodoro = { store })
    }

    @After fun removeFixture() {
        if (::db.isInitialized) db.close()
        assertTrue(context.deleteDatabase(filename))
        assertFalse(context.getDatabasePath(filename).exists())
    }

    @Test fun queuedCompletedWorkAndCurrentSpanSurviveReopenAndAreNeverCountedTwice() = runBlocking {
        queue.create(7, EntryFields("Manual"), START.minusSeconds(900), START)
        store.start(LENGTHS, EntryFields("Focus"), 7)
        now.set(START.plusSeconds(60))
        store.nextPhase()
        assertEquals(2, store.unsavedChangeCount())
        now.set(START.plusSeconds(120))
        assertTrue(store.nextPhase() is PomodoroStep.Saved)
        assertEquals(2, queue.unsentCount.first())
        assertEquals(2, store.unsavedChangeCount())
        now.set(START.plusSeconds(150))
        assertEquals(3, store.unsavedChangeCount())
        db.close()
        connect()
        assertEquals(2, queue.unsentCount.first())
        assertEquals(3, store.unsavedChangeCount())
    }

    @Test fun countCannotObserveBothTheOldOpenSpanAndItsNewQueuedCreate() = runBlocking {
        store.start(LENGTHS, EntryFields("Focus"), 7)
        now.set(START.plusSeconds(60))
        store.nextPhase()
        now.set(START.plusSeconds(120))
        val reads = (1..20).map { async(Dispatchers.IO) { store.unsavedChangeCount() } }
        val transition = async(Dispatchers.IO) { store.nextPhase() }
        assertTrue(reads.awaitAll().all { it == 1 })
        assertTrue(transition.await() is PomodoroStep.Saved)
        assertEquals(1, store.unsavedChangeCount())
    }

    @Test fun heldAndPossiblySentPomodoroCreatesAreIncludedInTheOrdinaryQueueCount() = runBlocking {
        store.start(LENGTHS, EntryFields("Focus"), 7)
        now.set(START.plusSeconds(60))
        store.end()
        val create = checkNotNull(db.changes().first())
        db.changes().setMaybeSent(create.sequence, true)
        db.changes().hold(create.sequence, "Server refused")
        now.set(START.plusSeconds(120))
        store.start(LENGTHS, EntryFields("Current"), 7)
        now.set(START.plusSeconds(150))
        assertEquals(2, store.unsavedChangeCount())
        assertEquals(1, queue.heldCount.first())
        assertTrue(checkNotNull(db.changes().change(create.sequence)).maybeSent)
    }

    @Test fun offlineForgetAndCacheClearRemoveAllCredentialBoundWorkAcrossReopen() = runBlocking {
        store.start(LENGTHS, EntryFields("Old account"), 7)
        now.set(START.plusSeconds(60))
        store.end()
        val create = checkNotNull(db.changes().first())
        db.changes().setMaybeSent(create.sequence, true)
        db.changes().hold(create.sequence, "Retry")
        now.set(START.plusSeconds(120))
        store.start(LENGTHS, EntryFields("Current old work"), 7)
        val old = store
        store.forget()
        cache.clear()
        assertNull(db.pomodoroSessions().current())
        assertNull(db.changes().first())
        assertEquals(0, queue.unsentCount.first())
        assertEquals(0, queue.heldCount.first())
        db.close()
        connect()
        assertNull(store.current())
        assertEquals(0, store.unsavedChangeCount())
        now.set(START.plusSeconds(600))
        val next = store.start(LENGTHS, EntryFields("Next account"), 7)
        assertEquals(PomodoroStep.Idle, old.endForTimerInTransaction(now.get()))
        assertEquals(next, store.current())
        assertEquals(0, queue.unsentCount.first())
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-20T09:00:00Z")
        val LENGTHS = PomodoroSessionConfig(1.0, 1.0, 1.0, 4.0)
        val TIME = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, null)
    }
}
