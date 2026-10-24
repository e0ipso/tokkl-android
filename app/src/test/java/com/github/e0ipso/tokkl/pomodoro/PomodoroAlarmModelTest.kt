package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Authored regression source; execution is reserved for the backlog's wave gate. */
class PomodoroAlarmModelTest {
    private val at = Instant.parse("2026-10-24T09:00:00Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var stored: PomodoroSession? = session()
    private var reached: ReachedPhaseEnd? = ReachedPhaseEnd("work", at)
    private var nexts = mutableListOf<PomodoroPhaseKey>()
    private var ends = mutableListOf<PomodoroPhaseKey>()
    private var hold: CompletableDeferred<Unit>? = null
    private var failBeforeCommit = false
    private var failAfterCommit = false
    private var readFails = false
    private val model = PomodoroAlarmModel(
        current = { if (readFails) throw IOException("read"); stored }, reached = { reached },
        next = { key ->
            nexts.add(key)
            hold?.await()
            if (failBeforeCommit) throw IOException("disk")
            if (stored?.phaseKey() == key) {
                stored = stored!!.copy(phaseKind = "shortRest", phaseStartedAt = at.plusSeconds(120))
                reached = null
            }
            if (failAfterCommit) throw IOException("sender")
        },
        end = { key ->
            ends.add(key)
            if (stored?.phaseKey() == key) { stored = null; reached = null }
        },
        scope = scope, clock = Clock.fixed(at.plusSeconds(120), ZoneOffset.UTC),
    )

    @After fun closeScope() { scope.cancel() }

    @Test fun opensOnlyTheDurableReachedPhaseAndKeepsItsTransitionAndOvertime() = runBlocking {
        model.open(stored!!.phaseKey()).join()
        assertEquals(stored, model.state.value.session)
        assertFalse(model.state.value.closed)
        assertEquals(PomodoroSound.WORK_TO_SHORT_REST, model.state.value.session!!.ring().sound)
        assertEquals(PomodoroClock(true, 60), stored!!.clockAt(at.plusSeconds(120)))
        assertTrue(nexts.isEmpty())
        assertTrue(ends.isEmpty())
    }

    @Test fun missingMalformedOrStaleIdentityCannotExposeActions() = runBlocking {
        for (key in listOf(null, stored!!.phaseKey().copy(workspaceId = 2), stored!!.phaseKey().copy(completed = 1.0),
            stored!!.phaseKey().copy(startedAt = at.minusSeconds(1)), stored!!.phaseKey().copy(pomodoroStartedAt = at.minusSeconds(1)))) {
            model.open(key).join()
            assertTrue(model.state.value.closed)
            assertNull(model.state.value.session)
            assertNull(model.next())
            assertNull(model.end())
        }
    }

    @Test fun unreachedOrFuturePhaseDoesNotOpenEvenWithMatchingIdentity() = runBlocking {
        reached = null
        model.open(stored!!.phaseKey()).join()
        assertTrue(model.state.value.closed)
        stored = session().copy(phaseStartedAt = at.plusSeconds(180))
        reached = ReachedPhaseEnd("work", stored!!.phaseStartedAt)
        model.open(stored!!.phaseKey()).join()
        assertNull(model.state.value.session)
        assertTrue(model.state.value.closed)
    }

    @Test fun duplicateAndStaleDeliveriesLeaveTheCurrentAlarmAndDoNotPerformActions() = runBlocking {
        val key = stored!!.phaseKey()
        model.open(key).join()
        model.open(key).join()
        model.open(key.copy(workspaceId = 4)).join()
        assertEquals(key, model.state.value.session?.phaseKey())
        assertFalse(model.state.value.closed)
        assertTrue(nexts.isEmpty())
        assertTrue(ends.isEmpty())
    }

    @Test fun nextPassesTheShownIdentityOnceAndReloadsTheCommittedPhase() = runBlocking {
        val key = stored!!.phaseKey()
        model.open(key).join()
        hold = CompletableDeferred()
        val action = model.next()!!
        assertTrue(model.state.value.busy)
        assertNull(model.next())
        assertNull(model.end())
        hold!!.complete(Unit)
        action.join()
        assertEquals(listOf(key), nexts)
        assertEquals("shortRest", stored!!.phaseKind)
        assertTrue(model.state.value.closed)
        assertNull(model.state.value.session)
    }

    @Test fun endPassesTheShownIdentityAndClosesAfterTheDurableDelete() = runBlocking {
        val key = stored!!.phaseKey()
        model.open(key).join()
        model.end()!!.join()
        assertEquals(listOf(key), ends)
        assertNull(stored)
        assertTrue(model.state.value.closed)
    }

    @Test fun actionThatBecameStaleCannotEndTheReplacementSession() = runBlocking {
        val key = stored!!.phaseKey()
        model.open(key).join()
        val replacement = session().copy(workspaceId = 99)
        stored = replacement
        model.end()!!.join()
        assertEquals(listOf(key), ends)
        assertEquals(replacement, stored)
        assertTrue(model.state.value.closed)
    }

    @Test fun failedWriteKeepsTheCurrentAlarmAndLetsTheUserRetry() = runBlocking {
        model.open(stored!!.phaseKey()).join()
        failBeforeCommit = true
        model.next()!!.join()
        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.closed)
        assertEquals(stored, model.state.value.session)
        failBeforeCommit = false
        model.next()!!.join()
        assertTrue(model.state.value.closed)
    }

    @Test fun failureAfterCommitDoesNotRestoreOrReplayTheFinishedPhase() = runBlocking {
        model.open(stored!!.phaseKey()).join()
        failAfterCommit = true
        model.next()!!.join()
        assertTrue(model.state.value.closed)
        assertFalse(model.state.value.failed)
        assertNull(model.next())
        assertEquals(1, nexts.size)
    }

    @Test fun externalEndAndReadFailureDisableStaleButtons() = runBlocking {
        model.open(stored!!.phaseKey()).join()
        readFails = true
        model.refresh().join()
        assertTrue(model.state.value.failed)
        assertNull(model.state.value.session)
        assertNull(model.next())
        readFails = false
        stored = null
        reached = null
        model.refresh().join()
        assertTrue(model.state.value.closed)
    }

    @Test fun recreationReadsDurableStateAndNoLifecycleRefreshAdvancesIt() = runBlocking {
        val key = stored!!.phaseKey()
        model.open(key).join()
        repeat(3) { model.refresh().join() }
        assertEquals(key, model.state.value.session?.phaseKey())
        assertTrue(nexts.isEmpty())
        assertTrue(ends.isEmpty())
    }

    private fun session() = PomodoroSession(
        workspaceId = 1, workMinutes = 1.0, shortRestMinutes = 1.0, longRestMinutes = 1.0, longRestEvery = 4.0,
        phaseKind = "work", phaseStartedAt = at, pomodoroStartedAt = at, completed = 0.0, fields = EntryFields(),
    )
}
