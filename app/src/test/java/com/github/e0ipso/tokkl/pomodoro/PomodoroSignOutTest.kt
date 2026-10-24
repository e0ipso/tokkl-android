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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Account-lifetime, warning-count and output regressions, independent of a network. */
class PomodoroSignOutTest {
    @Test
    fun activeWorkIsDiscardedWithoutCreatingAnEntryAndAllLocalEffectsAreReleased() = runBlocking {
        val f = Fixture()
        f.start()
        f.now = START.plusSeconds(30)
        f.store.forget()
        f.assertCleared()
        assertEquals(0, f.database.queued)
        assertEquals(0, f.callbacks)
    }

    @Test
    fun shortAndLongRestsAreDiscardedWithoutSavingOrKeepingAnAlarm() = runBlocking {
        for (kind in listOf("shortRest", "longRest")) {
            val f = Fixture()
            val work = f.start()
            f.database.session = work.copy(phaseKind = kind, phaseStartedAt = START.plusSeconds(60))
            f.store.rearm()
            f.store.forget()
            f.assertCleared()
            assertEquals(0, f.database.queued)
        }
    }

    @Test
    fun ringingSignOutStopsSoundVibrationAndNotificationAndDropsTheReachedRecord() = runBlocking {
        val f = Fixture()
        val work = f.start()
        f.now = START.plusSeconds(60)
        f.store.onPhaseAlarm(work.phaseKey())
        assertTrue(f.output.audio)
        assertTrue(f.output.vibration)
        assertTrue(f.output.notification)
        f.store.forget()
        f.assertCleared()
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
        val old = f.start()
        f.now = START.plusSeconds(60)
        f.store.onPhaseAlarm(old.phaseKey())
        f.store.forget()
        val nextStore = f.newStore()
        f.now = START.plusSeconds(600)
        val next = nextStore.start(LENGTHS, EntryFields("Next account"), 7)
        f.now = START.plusSeconds(660)
        nextStore.onPhaseAlarm(next.phaseKey())
        val posts = f.output.posts

        assertEquals(PomodoroStep.Idle, f.store.nextPhase())
        assertEquals(PomodoroStep.Idle, f.store.end())
        assertNull(f.store.updateFields(EntryFields("Old account")))
        assertNull(f.store.current())
        assertEquals(0, f.store.unsavedChangeCount())
        assertThrows(IllegalStateException::class.java) {
            runBlocking { f.store.start(LENGTHS, EntryFields("Old account"), 7) }
        }
        f.store.onPhaseAlarm(old.phaseKey())
        f.store.rearm()
        f.store.rearm(deferRingingToAlarm = true)
        f.store.repostRinging(old.phaseKey())
        assertFalse(f.store.withRingingPhase(null) { error("sticky callback from old store") })
        assertFalse(nextStore.withRingingPhase(old.phaseKey()) { error("old notification") })
        assertEquals(PomodoroStep.Idle, nextStore.nextPhaseIfCurrent(old.phaseKey()))
        assertEquals(PomodoroStep.Idle, nextStore.endIfCurrent(old.phaseKey()))
        f.store.disarm()
        f.store.discardRunningSession()

        assertEquals(next, nextStore.current())
        assertEquals(posts, f.output.posts)
        assertTrue(f.output.audio)
        assertEquals(0, f.database.queued)
    }

    @Test
    fun signOutWaitsForValidatedPlaybackThenStopsItAndRejectsWaitingActions() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.now = START.plusSeconds(60)
        f.store.onPhaseAlarm(phase)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val playback = launch {
            f.store.withRingingPhase(phase) {
                entered.complete(Unit)
                release.await()
                f.controller.start(it)
            }
        }
        entered.await()
        val end = async(start = CoroutineStart.UNDISPATCHED) { f.store.endIfCurrent(phase) }
        val signOut = async(start = CoroutineStart.UNDISPATCHED) { f.store.forget() }
        assertTrue(f.store.isForgotten)
        assertFalse(signOut.isCompleted)
        release.complete(Unit)
        playback.join()
        assertEquals(PomodoroStep.Idle, end.await())
        signOut.await()
        f.assertCleared()
        assertEquals(0, f.callbacks)
        assertEquals(0, f.database.queued)
    }

    @Test
    fun lateHandoffCallbackCannotScheduleCredentialBoundRetriesOrCancelTheNewAlarm() = runBlocking {
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
        val alarm = f.alarms.at
        f.store.timerHandoffCommitted(saved)
        assertEquals(0, f.callbacks)
        assertEquals(alarm, f.alarms.at)
        assertEquals(next, nextStore.current())
        assertEquals(0, f.database.queued)
    }

    @Test
    fun failedSoundCleanupStillCancelsTheAlarmAndClearsTheDurableReachedRecord() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.now = START.plusSeconds(60)
        f.store.onPhaseAlarm(phase)
        f.failStop = true
        assertTrue(runCatching { f.store.forget() }.isFailure)
        assertTrue(f.store.isForgotten)
        assertNull(f.database.session)
        assertNull(f.alarms.at)
        assertFalse(f.alarms.watching)
        assertNull(f.store.reachedPhaseEnd())
        f.failStop = false
        f.store.forget()
        f.assertCleared()
    }

    private class Fixture {
        var now: Instant = START
        var time: AccountTime? = null
        var callbacks = 0
        var failStop = false
        val database = MemoryDatabase()
        val alarms = FakeAlarms()
        val output = FakeOutput()
        val controller = PomodoroRingController(output)
        val store = newStore()
        fun newStore() = PomodoroSessionStore(
            database, Dispatchers.Unconfined,
            object : Clock() {
                override fun instant(): Instant = now
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId?): Clock = this
            },
            accountTime = { time }, onQueued = { callbacks++ }, alarms = alarms,
            ringing = object : PomodoroRinging {
                override suspend fun start(ring: PomodoroRing) = controller.start(ring)
                override suspend fun stop() {
                    if (failStop) error("audio device failed")
                    controller.stop()
                }
            },
        )
        suspend fun start() = store.start(LENGTHS, EntryFields("Focus"), 7)
        suspend fun assertCleared() {
            assertNull(store.current())
            assertNull(database.session)
            assertNull(store.reachedPhaseEnd())
            assertNull(alarms.at)
            assertFalse(alarms.watching)
            assertNull(controller.current)
            assertFalse(output.audio)
            assertFalse(output.vibration)
            assertFalse(output.notification)
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

    private class FakeAlarms : PomodoroAlarmScheduler {
        var at: Instant? = null
        var watching = false
        override val permissionDenied = false
        override fun schedule(triggerAt: Instant): Boolean { at = triggerAt; return true }
        override fun cancel() { at = null }
        override fun watchSession(running: Boolean) { watching = running }
    }

    private class FakeOutput : PomodoroRingOutput {
        var audio = false
        var vibration = false
        var notification = false
        var posts = 0
        override fun show(ring: PomodoroRing) { notification = true; posts++ }
        override fun play(sound: PomodoroSound) { audio = true }
        override fun vibrate() { vibration = true }
        override fun stopAudio() { audio = false }
        override fun stopVibration() { vibration = false }
        override fun removeNotification() { notification = false }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-20T09:00:00Z")
        val LENGTHS = PomodoroSessionConfig(1.0, 1.0, 1.0, 4.0)
    }
}
