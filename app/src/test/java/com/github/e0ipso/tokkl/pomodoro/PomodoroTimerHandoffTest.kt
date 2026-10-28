package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.cache.foreignPomodoroStart
import com.github.e0ipso.tokkl.cache.mayBeLocalRunning
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transaction and identity regressions. Room and server counterparts exercise the real queue. */
class PomodoroTimerHandoffTest {
    @Test
    fun sessionStartSwitchesEveryTimerAtOneCapturedGridBoundary() = runBlocking {
        val f = Fixture()
        f.db.timers += listOf(row(1, T0.minusSeconds(900)), row(2, T0.minusSeconds(1800)))
        f.now = T0.plusSeconds(420)
        val session = f.store.start(LENGTHS, FIELDS, 7)
        assertEquals(f.now, session.phaseStartedAt)
        assertEquals(listOf(f.now, f.now), f.db.stops.map { it.first })
        assertEquals(listOf(T0, T0), f.db.stops.map { it.second.stop })
        assertTrue(f.db.timers.isEmpty())
        assertEquals(1, f.callbacks)
        assertEquals(1, f.db.maxDepth)
    }

    @Test
    fun failedSessionSaveRestoresTimersAndDoesNotNotify() {
        val f = Fixture()
        val timer = row(1, T0.minusSeconds(900))
        f.db.timers += timer
        f.db.failSave = true
        assertThrows(IllegalStateException::class.java) { runBlocking { f.store.start(LENGTHS, FIELDS, 7) } }
        assertEquals(listOf(timer), f.db.timers)
        assertTrue(f.db.stops.isEmpty())
        assertNull(f.db.session)
        assertEquals(0, f.callbacks)
    }

    @Test
    fun partialSaveAndSessionRemovalRollBackTogetherAndRetryOnce() = runBlocking {
        val f = Fixture()
        val original = f.store.start(LENGTHS, FIELDS, 7)
        f.db.failCreate = true
        assertThrows(IllegalStateException::class.java) {
            f.db.transaction { f.store.endForTimerInTransaction(T0.plusSeconds(1800)) }
        }
        assertEquals(original, f.db.session)
        assertTrue(f.db.creates.isEmpty())
        f.db.failCreate = false
        val step = f.db.transaction { f.store.endForTimerInTransaction(T0.plusSeconds(1800)) }
        assertTrue(step is PomodoroStep.Saved)
        assertEquals(PomodoroStep.Idle, f.db.transaction { f.store.endForTimerInTransaction(T0.plusSeconds(1800)) })
        assertEquals(1, f.db.creates.size)
        assertEquals(1, f.db.maxDepth)
        assertNull(f.db.session)
    }

    @Test
    fun remoteStartDuringWorkCanEndThePomodoroBeforeTheCurrentRestStarted() = runBlocking {
        val f = Fixture()
        f.store.start(LENGTHS, FIELDS, 7)
        f.now = T0.plusSeconds(1500)
        f.store.nextPhase()
        val step = f.db.transaction { f.store.endForTimerInTransaction(T0.plusSeconds(900)) } as PomodoroStep.Saved
        assertEquals(T0.plusSeconds(900), step.span.stop)
        assertNull(f.db.session)
        assertEquals(1, f.db.creates.size)
    }

    @Test
    fun handoffSendsAfterCommitAndReentrantRefreshCannotSaveAgain() = runBlocking {
        val f = Fixture()
        f.store.start(LENGTHS, FIELDS, 7)
        f.now = T0.plusSeconds(1500)
        f.onCallback = {
            assertEquals(PomodoroStep.Idle, f.db.transaction { f.store.endForTimerInTransaction(f.now) })
        }
        val step = f.db.transaction { f.store.endForTimerInTransaction(f.now) }
        assertEquals("side effects wait for commit", 0, f.callbacks)
        f.store.timerHandoffCommitted(step)
        assertEquals(1, f.callbacks)
        assertEquals(1, f.db.creates.size)
    }

    @Test
    fun nextReadsTheRowInsideTheTransactionAfterACollidingStart() = runBlocking {
        val f = Fixture()
        f.store.start(LENGTHS, FIELDS, 7)
        f.now = T0.plusSeconds(1500)
        f.db.beforeTransaction = { f.db.session = null }
        assertEquals(PomodoroStep.Idle, f.store.nextPhase())
        assertNull(f.db.session)
        assertTrue(f.db.creates.isEmpty())
    }

    @Test
    fun shortHandoffDoesNotInventAnEntryAndSavedFieldsStayImmutable() = runBlocking {
        val f = Fixture()
        f.store.start(LENGTHS, FIELDS, 7)
        assertTrue(f.db.transaction { f.store.endForTimerInTransaction(T0.plusSeconds(1)) } is PomodoroStep.TooShort)
        assertTrue(f.db.creates.isEmpty())
        f.now = T0.plusSeconds(900)
        f.store.start(LENGTHS, FIELDS, 7)
        f.db.transaction { f.store.endForTimerInTransaction(T0.plusSeconds(1800)) }
        val frozen = f.db.creates.single()
        f.now = T0.plusSeconds(1800)
        f.store.start(LENGTHS, FIELDS.copy(description = "Later"), 7)
        f.store.updateFields(FIELDS.copy(description = "Edited later"))
        assertEquals(frozen, f.db.creates.single())
        assertEquals("Focus", frozen.description)
    }

    @Test
    fun foreignSelectionRequiresANewerRunningRowSurvivingTheLocalOverlay() {
        val foreign = row(10, T0.plusSeconds(900))
        val mapped = row(11, T0.plusSeconds(60))
        val stopped = row(12, T0.plusSeconds(120)).copy(stop = T0.plusSeconds(180))
        val local = row(-13, T0.plusSeconds(180))
        val equal = row(14, T0)
        val older = row(15, T0.minusSeconds(60))
        val overlaid = row(16, T0.plusSeconds(240))
        val rows = listOf(foreign, mapped, stopped, local, equal, older, overlaid)
        val cached = rows.associateBy { it.id }.toMutableMap()
        cached[overlaid.id] = overlaid.copy(stop = T0.plusSeconds(300))
        assertEquals(foreign, foreignPomodoroStart(T0, rows, cached::get) { it.id == mapped.id })
        assertNull(foreignPomodoroStart(foreign.start, rows, cached::get) { it.id == mapped.id })
    }

    @Test
    fun localPostEchoMatchesServerPrecisionButNeverAnExistingForeignId() {
        val entry = row(-1, T0.plusMillis(123))
        val create = PendingChange.of(ChangeKind.CREATE, entry).copy(maybeSent = true)
        val echo = entry.copy(id = 90, start = T0)
        assertTrue(create.mayBeLocalRunning(echo))
        assertFalse(create.copy(existingIds = listOf(90)).mayBeLocalRunning(echo))
        assertFalse(create.mayBeLocalRunning(echo.copy(description = "Foreign")))
        assertFalse(create.mayBeLocalRunning(echo.copy(start = T0.plusSeconds(1))))
    }

    private class Fixture {
        var now: Instant = T0
        val clock = object : Clock() {
            override fun instant(): Instant = now
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
        }
        val db = MemoryDatabase()
        var callbacks = 0
        var onCallback: () -> Unit = {}
        val store = PomodoroSessionStore(
            db, Dispatchers.Unconfined, clock, accountTime = { TIME },
            onQueued = { callbacks++; onCallback() },
        )
    }

    private class MemoryDatabase : PomodoroSessionDatabase {
        var session: PomodoroSession? = null
        val timers = mutableListOf<CachedTimeEntry>()
        val stops = mutableListOf<Pair<Instant, CachedTimeEntry>>()
        val creates = mutableListOf<PendingChange>()
        var failSave = false
        var failCreate = false
        var beforeTransaction: (() -> Unit)? = null
        private var depth = 0
        var maxDepth = 0
        override fun current(): PomodoroSession? = session
        override fun save(session: PomodoroSession) {
            check(depth == 1)
            this.session = session
            check(!failSave) { "session write failed" }
        }
        override fun delete() { check(depth == 1); session = null }
        override fun stopTimers(at: Instant): Boolean {
            check(depth == 1)
            val hadTimers = timers.isNotEmpty()
            timers.forEach { timer ->
                val span = checkNotNull(TIME.snapCapturedStop(timer.start, at))
                stops += at to timer.copy(start = span.start, stop = span.stop)
            }
            timers.clear()
            return hadTimers
        }
        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            check(depth == 1)
            val id = -(creates.size + 1L)
            creates += PendingChange.of(ChangeKind.CREATE, row(id, start).copy(
                workspaceId = workspaceId, description = fields.description, tags = fields.tags, stop = stop,
            ))
            check(!failCreate) { "queue write failed" }
            return id
        }
        override fun <T> transaction(block: () -> T): T {
            beforeTransaction?.also { beforeTransaction = null }?.invoke()
            val previous = session
            val priorTimers = timers.toList()
            val priorStops = stops.toList()
            val priorCreates = creates.toList()
            depth++
            maxDepth = maxOf(maxDepth, depth)
            try { return block() } catch (e: Exception) {
                session = previous
                timers.clear(); timers.addAll(priorTimers)
                stops.clear(); stops.addAll(priorStops)
                creates.clear(); creates.addAll(priorCreates)
                throw e
            } finally { depth-- }
        }
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-24T09:00:00Z")
        val LENGTHS = PomodoroSessionConfig(25.0, 5.0, 15.0, 4.0)
        val FIELDS = EntryFields("Focus")
        val TIME = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, 15.0)
        fun row(id: Long, at: Instant) = CachedTimeEntry(id, 7, null, null, "Focus", at, null, false, emptyList(), emptyList())
    }
}
