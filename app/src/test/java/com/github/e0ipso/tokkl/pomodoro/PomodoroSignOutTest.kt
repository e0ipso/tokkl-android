package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.settings.SignOutModel
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account-lifetime and warning-count regressions of the device-local session, independent of a
 * network. The phase alarm and its ring follow the server's state, not this store; sign-out
 * releases them through PomodoroPhaseAlarm.release, which PomodoroPhaseAlarmTest covers.
 */
class PomodoroSignOutTest {
    @Test
    fun activeWorkIsDiscardedWithoutCreatingAnEntry() = runBlocking {
        val f = Fixture()
        f.start()
        f.now = START.plusSeconds(30)
        f.store.forget()
        f.assertCleared()
        assertEquals(0, f.database.queued)
        assertEquals(0, f.callbacks)
    }

    @Test
    fun shortAndLongRestsAreDiscardedWithoutSaving() = runBlocking {
        for (kind in listOf("shortRest", "longRest")) {
            val f = Fixture()
            val work = f.start()
            f.database.session = work.copy(phaseKind = kind, phaseStartedAt = START.plusSeconds(60))
            f.store.forget()
            f.assertCleared()
            assertEquals(0, f.database.queued)
        }
    }

    @Test
    fun queueAndCurrentWorkAreCountedOnceAndCancelPreservesBoth() = runBlocking {
        val f = Fixture()
        f.database.queued = 2
        f.start()
        f.now = START.plusSeconds(30)
        val model = SignOutModel(f.store::unsavedChangeCount, { f.store.forget(); true }, this)
        model.signOut()
        // The warning runs in this scope, so let the pending command finish.
        kotlinx.coroutines.yield()
        assertEquals(3, model.state.value.unsentToConfirm)
        model.cancel()
        assertEquals(SignOutModel.State(), model.state.value)
        assertEquals(2, f.database.queued)
        assertFalse(f.store.isForgotten)
        assertEquals(3, f.store.unsavedChangeCount())
    }

    @Test
    fun completedPomodoroMovesIntoTheQueueAndTheNewZeroLengthWorkIsNotCountedAgain() = runBlocking {
        val f = Fixture()
        f.start()
        f.now = START.plusSeconds(60)
        f.store.nextPhase()
        assertEquals(1, f.store.unsavedChangeCount())
        f.now = START.plusSeconds(120)
        assertTrue(f.store.nextPhase() is PomodoroStep.Saved)
        assertEquals(1, f.database.queued)
        assertEquals(1, f.store.unsavedChangeCount())
        f.now = START.plusSeconds(150)
        assertEquals(2, f.store.unsavedChangeCount())
        f.store.end()
        assertEquals(2, f.database.queued)
        assertEquals(2, f.store.unsavedChangeCount())
    }

    @Test
    fun restOwnsItsPrecedingWorkButRestTimeCannotTurnTooShortWorkIntoAnotherChange() = runBlocking {
        val f = Fixture()
        f.time = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, 15.0)
        val work = f.start()
        f.now = START.plusSeconds(60)
        assertEquals(0, f.store.unsavedChangeCount())
        f.store.nextPhase()
        f.now = START.plusSeconds(3600)
        assertEquals(0, f.store.unsavedChangeCount())
        f.database.session = work.copy(phaseKind = "longRest", phaseStartedAt = START.plusSeconds(900))
        assertEquals(1, f.store.unsavedChangeCount())
    }

    @Test
    fun onlyQueuedChangesKeepTheExistingGenericCountAndEmptyAccountsHaveNoWarning() = runBlocking {
        val f = Fixture()
        assertEquals(0, f.store.unsavedChangeCount())
        f.database.queued = 4
        assertEquals(4, f.store.unsavedChangeCount())
        f.database.queued = 0
        var clears = 0
        val model = SignOutModel(f.store::unsavedChangeCount, { clears++; true }, CoroutineScope(Dispatchers.Unconfined))
        model.signOut()
        assertEquals(1, clears)
        assertEquals(SignOutModel.State(), model.state.value)
    }

    @Test
    fun warningReadsTheQueueAndSessionWithinTheSameTransaction() = runBlocking {
        val f = Fixture()
        f.start()
        f.now = START.plusSeconds(30)
        f.database.requireTransactionForReads = true
        assertEquals(1, f.store.unsavedChangeCount())
        assertFalse(f.database.inTransaction)
    }

    @Test
    fun unreadableWarningSnapshotFailsClosedWithoutSigningOut() {
        val f = Fixture()
        f.database.failCount = true
        var clears = 0
        val model = SignOutModel(f.store::unsavedChangeCount, { clears++; true }, CoroutineScope(Dispatchers.Unconfined))
        model.signOut()
        assertEquals(0, clears)
        assertEquals(SignOutModel.State(failed = true), model.state.value)
    }

    @Test
    fun staleCallbacksAndRetainedCommandsCannotTouchALaterAccountsSession() = runBlocking {
        val f = Fixture()
        f.start()
        f.now = START.plusSeconds(60)
        f.store.forget()
        val nextStore = f.newStore()
        f.now = START.plusSeconds(600)
        val next = nextStore.start(LENGTHS, EntryFields("Next account"), 7)
        f.now = START.plusSeconds(660)

        assertEquals(PomodoroStep.Idle, f.store.nextPhase())
        assertEquals(PomodoroStep.Idle, f.store.end())
        assertNull(f.store.updateFields(EntryFields("Old account")))
        assertNull(f.store.current())
        assertEquals(0, f.store.unsavedChangeCount())
        assertThrows(IllegalStateException::class.java) {
            runBlocking { f.store.start(LENGTHS, EntryFields("Old account"), 7) }
        }

        assertEquals(next, nextStore.current())
        assertEquals(0, f.database.queued)
    }

    @Test
    fun lateHandoffCallbackCannotScheduleCredentialBoundRetries() = runBlocking {
        val f = Fixture()
        f.start()
        f.now = START.plusSeconds(60)
        val saved = f.database.transaction { f.store.endForTimerInTransaction(f.now) }
        assertTrue(saved is PomodoroStep.Saved)
        assertEquals(0, f.callbacks)
        f.store.forget()
        // The ordinary queue's sign-out steps discard the already committed save.
        f.database.queued = 0
        val nextStore = f.newStore()
        f.now = START.plusSeconds(600)
        val next = nextStore.start(LENGTHS, EntryFields("New account"), 7)
        f.store.timerHandoffCommitted(saved)
        assertEquals(0, f.callbacks)
        assertEquals(next, nextStore.current())
        assertEquals(0, f.database.queued)
    }

    private class Fixture {
        var now: Instant = START
        var time: AccountTime? = null
        var callbacks = 0
        val database = MemoryDatabase()
        val store = newStore()
        fun newStore() = PomodoroSessionStore(
            database, Dispatchers.Unconfined,
            object : Clock() {
                override fun instant(): Instant = now
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId?): Clock = this
            },
            accountTime = { time }, onQueued = { callbacks++ },
        )
        suspend fun start() = store.start(LENGTHS, EntryFields("Focus"), 7)
        suspend fun assertCleared() {
            assertNull(store.current())
            assertNull(database.session)
        }
    }

    private class MemoryDatabase : PomodoroSessionDatabase {
        var session: PomodoroSession? = null
        var queued = 0
        var inTransaction = false
        var requireTransactionForReads = false
        var failCount = false
        override fun current(): PomodoroSession? {
            if (requireTransactionForReads) check(inTransaction)
            return session
        }
        override fun save(session: PomodoroSession) { this.session = session }
        override fun delete() { session = null }
        override fun queuedCount(): Int {
            check(inTransaction)
            check(!failCount) { "disk read failed" }
            return queued
        }
        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            check(inTransaction)
            queued++
            return -queued.toLong()
        }
        override fun <T> transaction(block: () -> T): T {
            check(!inTransaction)
            inTransaction = true
            return try { block() } finally { inTransaction = false }
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-20T09:00:00Z")
        val LENGTHS = PomodoroSessionConfig(1.0, 1.0, 1.0, 4.0)
    }
}
