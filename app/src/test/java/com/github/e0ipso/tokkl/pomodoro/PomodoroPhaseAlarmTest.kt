package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.plannedEnd
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One exact alarm per phase, cancelled when the user confirms, skips or ends, and none without a
 * session. The alarm records a reached end and leaves the phase there, including a re-arm that
 * finds the planned end already past.
 */
class PomodoroPhaseAlarmTest {

    private val now = AtomicReference(Instant.parse("2026-10-20T09:00:00Z"))
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val database = MemoryDatabase()
    private val alarms = FakePomodoroAlarmScheduler()
    private val phaseEnds = MemoryPomodoroPhaseEnds()
    private val store = PomodoroSessionStore(
        database,
        Dispatchers.Unconfined,
        clock,
        accountTime = { null },
        alarms = alarms,
        phaseEnds = phaseEnds,
    )

    @Test
    fun oneAlarmPerPhaseCancelledOnSkipConfirmAndEnd() = runBlocking {
        at("2026-10-20T09:00:00Z")
        val started = store.start(LENGTHS, FIELDS, WORKSPACE)
        val workEnd = plannedEnd(started.asPhase(), LENGTHS)
        assertEquals(parse("2026-10-20T09:25:00Z"), workEnd)
        assertEquals(workEnd, alarms.armed)
        assertEquals(listOf(workEnd), alarms.scheduled)
        assertEquals(listOf<Instant?>(null), alarms.cancelled)
        assertEquals(false, store.exactAlarmPermissionDenied())

        store.updateFields(FIELDS.copy(description = "Still focus"))
        assertEquals(listOf(workEnd), alarms.scheduled)
        assertEquals(workEnd, alarms.armed)

        at("2026-10-20T09:10:00Z")
        val skipped = store.nextPhase() as PomodoroStep.Running
        assertEquals("shortRest", skipped.session.phaseKind)
        val restEnd = plannedEnd(skipped.session.asPhase(), LENGTHS)
        assertEquals(parse("2026-10-20T09:15:00Z"), restEnd)
        assertEquals(restEnd, alarms.armed)
        assertEquals(listOf(workEnd, restEnd), alarms.scheduled)
        assertEquals(listOf(null, workEnd), alarms.cancelled)

        at("2026-10-20T09:20:00Z")
        val confirmed = store.nextPhase() as PomodoroStep.Saved
        val nextWork = checkNotNull(confirmed.session)
        assertEquals("work", nextWork.phaseKind)
        val nextEnd = plannedEnd(nextWork.asPhase(), LENGTHS)
        assertEquals(parse("2026-10-20T09:45:00Z"), nextEnd)
        assertEquals(nextEnd, alarms.armed)
        assertEquals(listOf(null, workEnd, restEnd), alarms.cancelled)

        val ended = store.end() as PomodoroStep.Saved
        assertNull(ended.session)
        assertNull(alarms.armed)
        assertNull(store.current())
        assertEquals(false, alarms.watching.last())
        assertEquals(listOf(null, workEnd, restEnd, nextEnd), alarms.cancelled)
    }

    @Test
    fun nothingIsScheduledWithoutASession() = runBlocking {
        assertEquals(PomodoroStep.Idle, store.nextPhase())
        assertEquals(PomodoroStep.Idle, store.end())
        store.rearm()

        assertEquals(emptyList<Instant>(), alarms.scheduled)
        assertNull(alarms.armed)
        assertNull(store.current())
        assertTrue(alarms.watching.all { !it })
    }

    @Test
    fun deniedPermissionLeavesTheSessionAndRearmsWhenGranted() = runBlocking {
        alarms.denied = true
        at("2026-10-20T09:00:00Z")
        val started = store.start(LENGTHS, FIELDS, WORKSPACE)

        assertEquals("work", started.phaseKind)
        assertEquals(true, store.exactAlarmPermissionDenied())
        assertEquals(emptyList<Instant>(), alarms.scheduled)
        assertNull(alarms.armed)
        assertEquals(true, alarms.watching.last())

        alarms.denied = false
        store.rearm()
        assertEquals(plannedEnd(started.asPhase(), LENGTHS), alarms.armed)
        assertEquals(started, store.current())
        assertNull(store.reachedPhaseEnd())
    }

    @Test
    fun theAlarmRecordsTheEndWithoutAdvancingThePhase() = runBlocking {
        at("2026-10-20T09:00:00Z")
        val started = store.start(LENGTHS, FIELDS, WORKSPACE)
        at("2026-10-20T09:10:00Z")
        alarms.armed = null
        alarms.scheduled.clear()
        store.onPhaseAlarm()
        assertNull(store.reachedPhaseEnd())
        assertEquals(started, store.current())
        assertEquals(listOf(plannedEnd(started.asPhase(), LENGTHS)), alarms.scheduled)

        at("2026-10-20T09:25:00Z")
        store.onPhaseAlarm()
        assertEquals(ReachedPhaseEnd("work", started.phaseStartedAt), store.reachedPhaseEnd())
        assertEquals(started, store.current())
        assertEquals(plannedEnd(started.asPhase(), LENGTHS), alarms.armed)

        at("2026-10-20T09:30:00Z")
        val confirmed = store.nextPhase() as PomodoroStep.Running
        assertEquals("shortRest", confirmed.session.phaseKind)
        assertNull(store.reachedPhaseEnd())
    }

    @Test
    fun rearmBeforeThePlannedEndSchedulesThatEnd() = runBlocking {
        at("2026-10-20T09:00:00Z")
        val started = store.start(LENGTHS, FIELDS, WORKSPACE)
        alarms.scheduled.clear()
        at("2026-10-20T09:05:00Z")

        store.rearm()

        val end = plannedEnd(started.asPhase(), LENGTHS)
        assertEquals(end, alarms.armed)
        assertEquals(listOf(end), alarms.scheduled)
        assertNull(store.reachedPhaseEnd())
        assertEquals(started, store.current())
    }

    @Test
    fun aRebootPastThePlannedEndRingsAndLeavesOvertime() = runBlocking {
        at("2026-10-20T09:00:00Z")
        val started = store.start(LENGTHS, FIELDS, WORKSPACE)
        at("2026-10-20T09:40:00Z")

        store.rearm()

        assertEquals(started, store.current())
        assertEquals(ReachedPhaseEnd("work", started.phaseStartedAt), store.reachedPhaseEnd())
        assertNull(alarms.armed)
        assertEquals(true, alarms.watching.last())

        val confirmed = store.nextPhase() as PomodoroStep.Running
        assertEquals("shortRest", confirmed.session.phaseKind)
        assertEquals(parse("2026-10-20T09:40:00Z"), confirmed.session.phaseStartedAt)
        assertNull(store.reachedPhaseEnd())
        assertEquals(plannedEnd(confirmed.session.asPhase(), LENGTHS), alarms.armed)
    }

    private fun at(text: String) {
        now.set(parse(text))
    }

    private fun parse(text: String): Instant = Instant.parse(text)

    private fun PomodoroSession.asPhase() = SessionPhase(phaseKind, phaseStartedAt, completed)

    private class MemoryDatabase : PomodoroSessionDatabase {
        var session: PomodoroSession? = null

        override fun current(): PomodoroSession? = session

        override fun save(session: PomodoroSession) {
            this.session = session
        }

        override fun delete() {
            session = null
        }

        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long = -1

        override fun <T> transaction(block: () -> T): T = block()
    }

    private class FakePomodoroAlarmScheduler : PomodoroAlarmScheduler {
        var denied = false
        val scheduled = mutableListOf<Instant>()
        val cancelled = mutableListOf<Instant?>()
        var armed: Instant? = null
        val watching = mutableListOf<Boolean>()

        override val permissionDenied: Boolean
            get() = denied

        override fun schedule(triggerAt: Instant): Boolean {
            if (denied) return false
            armed = triggerAt
            scheduled += triggerAt
            return true
        }

        override fun cancel() {
            cancelled += armed
            armed = null
        }

        override fun watchSession(running: Boolean) {
            watching += running
        }
    }

    private companion object {
        const val WORKSPACE = 7L
        val LENGTHS = PomodoroSessionConfig(25.0, 5.0, 15.0, 4.0)
        val FIELDS = EntryFields(description = "Focus", projectId = 3, taskId = 9, tags = listOf("deep"), billable = true)
    }
}
