package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.pomodoro.IdlePomodoroAlarms
import com.github.e0ipso.tokkl.pomodoro.MemoryPomodoroPhaseEnds
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.shortcuts.FavoriteNames
import com.github.e0ipso.tokkl.shortcuts.ShortcutStart
import com.github.e0ipso.tokkl.shortcuts.favoriteShortcutId
import com.github.e0ipso.tokkl.shortcuts.startFavoriteShortcut
import com.github.e0ipso.tokkl.tile.RunningTileContent
import com.github.e0ipso.tokkl.tile.tapTile
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.timer.TimerModel
import com.github.e0ipso.tokkl.timer.TimerState
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real Room transactions, no sender: every assertion also applies while the phone is offline. */
class PomodoroHandoffQueueTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val filename = "pomodoro-handoff-${UUID.randomUUID()}.db"
    private val now = AtomicReference(T0)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private lateinit var db: CacheDatabase
    private lateinit var queue: ChangeQueue
    private lateinit var sessions: PomodoroSessionStore
    private lateinit var cache: AccountCache
    private val dao get() = db.changes()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var callbacks = 0

    @Before fun open() { connect() }
    private fun connect() {
        db = Room.databaseBuilder(context, CacheDatabase::class.java, filename).build()
        queue = ChangeQueue(
            db, Dispatchers.IO, { callbacks++ }, clock,
            stopSpan = { start, at, oneStep ->
                assertFalse("handoff must never use the one-step rule", oneStep)
                TIME.snapStop(start, at, oneStep)
            },
            capturedStopSpan = TIME::snapCapturedStop,
            pomodoro = { sessions },
        )
        sessions = PomodoroSessionStore.open(
            db, queue, Dispatchers.IO, clock, accountTime = { TIME }, onQueued = { callbacks++ },
            alarms = IdlePomodoroAlarms, phaseEnds = MemoryPomodoroPhaseEnds(),
        )
        cache = AccountCache(db, clock, pomodoro = { sessions })
    }
    @After fun removeFixture() {
        scope.cancel()
        if (::db.isInitialized) db.close()
        assertTrue(context.deleteDatabase(filename))
        assertFalse(context.getDatabasePath(filename).exists())
    }

    @Test fun sessionStopsAllRunningRowsOnTheGridUsingTheSameCapturedInstant() = runBlocking {
        dao.upsertEntry(row(21, T0.minusSeconds(1800)))
        dao.upsertEntry(row(22, T0.minusSeconds(900)))
        now.set(T0.plusSeconds(420))
        val session = sessions.start(LENGTHS, FOCUS, WORKSPACE)
        assertTrue(dao.runningEntries().isEmpty())
        for (id in listOf(21L, 22L)) {
            val stop = dao.changesFor(id).single()
            assertEquals(ChangeKind.STOP, stop.kind)
            assertEquals(session.phaseStartedAt, stop.capturedStop)
            assertEquals(T0, stop.stop)
            assertFalse(stop.shortStop)
        }
        assertEquals(1, callbacks)
    }

    @Test fun sameGridSwitchKeepsTrashUndoAndTheImmutableStopAheadOfRestore() = runBlocking {
        val timer = row(23, T0.plusSeconds(480))
        dao.upsertEntry(timer)
        now.set(T0.plusSeconds(481))
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        val stop = dao.changesFor(timer.id).single()
        assertTrue(stop.shortStop)
        assertEquals(now.get(), stop.capturedStop)
        assertNull(dao.entry(timer.id))
        assertEquals(timer.id, queue.shortStopUndo.first()!!.entryId)
        assertTrue(queue.undoShortStop(timer.id))
        assertEquals(listOf(ChangeKind.STOP, ChangeKind.RESTORE), dao.changesFor(timer.id).map { it.kind })
        assertEquals(stop, dao.changesFor(timer.id).first())
        assertEquals(T0.plusSeconds(900), dao.entry(timer.id)!!.start)
        assertEquals(dao.entry(timer.id)!!.start, dao.entry(timer.id)!!.stop)
        assertNull(dao.shortStop(timer.id))
        assertEquals(FOCUS, sessions.current()!!.fields)
    }

    @Test fun startFavoriteAndContinueModelsUseTheSameAtomicQueueHandoff() = runBlocking {
        for (action in listOf("Start", "Favorite", "Continue")) {
            db.clearAllTables()
            now.set(T0)
            sessions.start(LENGTHS, FOCUS, WORKSPACE)
            val model = TimerModel(
                queue.timerControls, cache.projects, cache.tasks,
                MutableStateFlow(AccountSettings("UTC", DayOfWeek.MONDAY, 15.0, defaultWorkspaceId = WORKSPACE)),
                queue, scope, clock,
            )
            val observer = scope.launch { model.state.collect {} }
            try {
                withTimeout(5_000) { model.state.first { it is TimerState.Idle } }
                now.set(T0.plusSeconds(1800))
                when (action) {
                    "Start" -> model.start()
                    "Favorite" -> model.startFrom(EntryFields("Favorite"))
                    else -> model.continueEntry(row(90, T0.minusSeconds(1800)).copy(stop = T0.minusSeconds(900)))
                }
                withTimeout(5_000) { model.starts.first() }
                assertHandoff(action)
            } finally { observer.cancel() }
        }
    }

    @Test fun tileContinueAndLauncherFavoriteUseTheSharedQueue() = runBlocking {
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        now.set(T0.plusSeconds(1800))
        queue.tapTile(RunningTileContent.Continue(row(90, T0.minusSeconds(1800)).copy(stop = T0.minusSeconds(900))))
        assertHandoff("tile")
        db.clearAllTables()
        now.set(T0)
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        now.set(T0.plusSeconds(1800))
        val favorite = CachedFavorite(0, "Shortcut", null, null, emptyList(), false)
        val result = startFavoriteShortcut(
            favoriteShortcutId(EntryFields("Shortcut")), true, listOf(favorite), emptyList(), WORKSPACE,
            FavoriteNames("Untitled") { name, project -> "$name $project" }, queue::start,
        )
        assertTrue(result is ShortcutStart.Started)
        assertHandoff("shortcut")
    }

    @Test fun foreignRefreshEndsAtItsStartAndRepeatOrRemappingCannotSaveAgain() = runBlocking {
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        val foreign = row(80, T0.plusSeconds(900))
        now.set(T0.plusSeconds(3600))
        cache.checkedRunning(cache.runningCheckStart(), foreign)
        val saved = dao.first()!!
        assertEquals(FOCUS.description, saved.description)
        assertEquals(foreign.start, saved.stop)
        assertNull(sessions.current())
        repeat(3) { cache.checkedRunning(cache.runningCheckStart(), foreign) }
        assertEquals(1, queue.unsentCount.first())
        assertEquals(saved, dao.first())
        db.clearAllTables()
        now.set(T0)
        val session = sessions.start(LENGTHS, FOCUS, WORKSPACE)
        dao.insertMapping(EntryIdMapping(-8, foreign.id))
        cache.checkedRunning(cache.runningCheckStart(), foreign)
        assertEquals(session, sessions.current())
        assertEquals(0, queue.unsentCount.first())
    }

    @Test fun failedTimerCreateRollsBackThePomodoroSaveAndSessionRemoval() = runBlocking {
        val session = sessions.start(LENGTHS, FOCUS, WORKSPACE)
        now.set(T0.plusSeconds(1800))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_running_create BEFORE INSERT ON pending_changes
            WHEN NEW.kind = 'CREATE' AND NEW.stop IS NULL
            BEGIN SELECT RAISE(ABORT, 'fixture running create failed'); END
        """.trimIndent())
        assertThrows(SQLiteException::class.java) { runBlocking { queue.start(WORKSPACE, EntryFields("New timer")) } }
        assertEquals(session, sessions.current())
        assertEquals(0, queue.unsentCount.first())
        assertTrue(dao.runningEntries().isEmpty())
        assertEquals(0, callbacks)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_running_create")
        queue.start(WORKSPACE, EntryFields("New timer"))
        assertHandoff("retry")
    }

    @Test fun failedSessionInsertRollsBackEveryStopAndUndoOffer() = runBlocking {
        val timer = row(81, T0)
        dao.upsertEntry(timer)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_session BEFORE INSERT ON pomodoro_session
            BEGIN SELECT RAISE(ABORT, 'fixture session save failed'); END
        """.trimIndent())
        now.set(T0.plusSeconds(1))
        assertThrows(SQLiteException::class.java) { runBlocking { sessions.start(LENGTHS, FOCUS, WORKSPACE) } }
        assertEquals(timer, dao.entry(timer.id))
        assertEquals(0, queue.unsentCount.first())
        assertNull(queue.shortStopUndo.first())
        assertNull(sessions.current())
        assertEquals(0, callbacks)
    }

    @Test fun failedRefreshSaveRollsBackTheIncomingRowAndKeepsTheSessionForRetry() = runBlocking {
        val session = sessions.start(LENGTHS, FOCUS, WORKSPACE)
        val foreign = row(82, T0.plusSeconds(900))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_partial BEFORE INSERT ON pending_changes
            BEGIN SELECT RAISE(ABORT, 'fixture partial save failed'); END
        """.trimIndent())
        cache.checkedRunning(cache.runningCheckStart(), foreign)
        assertEquals(session, sessions.current())
        assertNull(dao.entry(foreign.id))
        assertEquals(0, callbacks)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_partial")
        cache.checkedRunning(cache.runningCheckStart(), foreign)
        assertNull(sessions.current())
        assertEquals(foreign, dao.entry(foreign.id))
        assertEquals(1, queue.unsentCount.first())
    }

    @Test fun concurrentStartsSaveOnePartialAndQueueEachStartInOrder() = runBlocking {
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        now.set(T0.plusSeconds(1800))
        val ids = listOf("First", "Second").map { name ->
            async(Dispatchers.Default) { queue.start(WORKSPACE, EntryFields(name)) }
        }.awaitAll()
        assertNotEquals(ids[0], ids[1])
        assertNull(sessions.current())
        val entries = cache.timeEntries.first()
        assertEquals(1, entries.count { it.description == FOCUS.description })
        assertEquals(1, dao.runningEntries().size)
        assertEquals(1, entries.count { it.stop == null })
        assertEquals(4, queue.unsentCount.first()) // partial, start, captured Stop, replacement start
        assertTrue(dao.changesFor(ids.min()).first().sequence > dao.first()!!.sequence)
    }

    @Test fun timerStartCollidingWithPhaseConfirmationCannotResurrectTheEndedSession() = runBlocking {
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        now.set(T0.plusSeconds(1800))
        val next = async(Dispatchers.Default) { sessions.nextPhase() }
        val start = async(Dispatchers.Default) { queue.start(WORKSPACE, EntryFields("Colliding timer")) }
        next.await()
        start.await()
        assertHandoff("phase collision")
        assertEquals(1, cache.timeEntries.first().count { it.description == FOCUS.description })
    }

    @Test fun pausedCheckpointsSurviveASessionAndARefusedResumeStillRestoresThem() = runBlocking {
        dao.upsertEntry(row(83, T0.minusSeconds(900)))
        val running = queue.timerControls.first() as TimerControls.Running
        assertTrue(queue.pause(running.target))
        val paused = queue.timerControls.first() as TimerControls.Paused
        val checkpoints = dao.pauseSegments(paused.target.chainId)
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        assertEquals(checkpoints, dao.pauseSegments(paused.target.chainId))
        assertEquals(paused, queue.timerControls.first())
        now.set(T0.plusSeconds(1800))
        assertNull(queue.resume(paused.target.copy(revision = paused.target.revision + 1)))
        assertTrue(sessions.current() != null)
        val resumed = queue.resume(paused.target)!!
        val create = dao.changesFor(resumed).single()
        dao.hold(create.sequence, "refused fixture")
        assertTrue(queue.discard(create.sequence))
        val restored = queue.timerControls.first() as TimerControls.Paused
        assertEquals(paused.seconds, restored.seconds)
        assertEquals(paused.fields, restored.fields)
        assertEquals(checkpoints, dao.pauseSegments(paused.target.chainId))
        assertNull(sessions.current())
        assertEquals(1, cache.timeEntries.first().count { it.description == FOCUS.description && it.start == T0 })
    }

    @Test fun heldStopsStayHeldWhileThePartialAndNewTimerRemainDurableAcrossReopen() = runBlocking {
        dao.upsertEntry(row(84, T0.minusSeconds(900)))
        sessions.start(LENGTHS, FOCUS, WORKSPACE)
        val stop = dao.changesFor(84).single()
        dao.hold(stop.sequence, "held stop")
        now.set(T0.plusSeconds(1800))
        val runningId = queue.start(WORKSPACE, EntryFields("Running"))
        val partial = dao.first()!!
        assertEquals(FOCUS.description, partial.description)
        assertEquals(3, queue.unsentCount.first())
        db.close()
        connect()
        assertNull(sessions.current())
        assertTrue(dao.change(stop.sequence)!!.held)
        assertEquals(partial, dao.first())
        assertEquals(runningId, dao.runningEntries().single().id)
        assertEquals(3, queue.unsentCount.first())
    }

    private suspend fun assertHandoff(label: String) {
        assertNull(label, sessions.current())
        val partial = dao.first()!!
        val running = dao.runningEntries().single()
        assertEquals(label, ChangeKind.CREATE, partial.kind)
        assertEquals(label, FOCUS.description, partial.description)
        assertEquals(label, now.get(), partial.stop)
        assertEquals(label, partial.stop, running.start)
        assertEquals(label, 2, queue.unsentCount.first())
        assertTrue(label, dao.changesFor(running.id).single().sequence > partial.sequence)
    }
    private companion object {
        const val WORKSPACE = 7L
        val T0: Instant = Instant.parse("2026-10-24T09:00:00Z")
        val TIME = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, 15.0)
        val LENGTHS = PomodoroSessionConfig(25.0, 5.0, 15.0, 4.0)
        val FOCUS = EntryFields("Partial Pomodoro")
        fun row(id: Long, at: Instant) = CachedTimeEntry(id, WORKSPACE, null, null, "Earlier timer", at, null, false, emptyList(), emptyList())
    }
}
