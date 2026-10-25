package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression source for the phase/store/service contract; no framework or network is needed. */
class PomodoroRingingTest {
    @Test
    fun phaseEndStartsOutputAndNeverAdvancesThePhase() = runBlocking {
        val f = Fixture()
        val original = f.start()
        f.due()
        f.store.onPhaseAlarm(original.phaseKey())

        assertEquals(original, f.store.current())
        assertEquals(ReachedPhaseEnd("work", START), f.store.reachedPhaseEnd())
        assertEquals(PomodoroSound.WORK_TO_SHORT_REST, f.output.sound)
        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
        assertEquals(0, f.database.creates)
    }

    @Test
    fun earlyDeliveryOnlyRearmsTheAlarm() = runBlocking {
        val f = Fixture()
        val original = f.start()
        f.store.onPhaseAlarm(original.phaseKey())

        assertNull(f.controller.current)
        assertFalse(f.output.audio)
        assertFalse(f.output.vibrating)
        assertEquals(START.plusSeconds(60), f.alarms.at)
        assertEquals(original.phaseKey(), f.alarms.phase)
    }

    @Test
    fun nextStopsBothOutputsAndKeepsTheNextPhaseWithoutANetworkDependency() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        val result = f.store.nextPhaseIfCurrent(original.phaseKey()) as PomodoroStep.Running

        assertEquals("shortRest", result.session.phaseKind)
        f.assertStopped()
        assertNull(f.store.reachedPhaseEnd())
        assertEquals(result.session.phaseKey(), f.alarms.phase)
        assertEquals(0, f.database.creates)
    }

    @Test
    fun endStopsBothAndQueuesTheEntryBeforeAnyServerAnswer() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        val result = f.store.endIfCurrent(original.phaseKey()) as PomodoroStep.Saved

        assertNull(result.session)
        assertNull(f.store.current())
        assertEquals(1, f.database.creates)
        assertEquals(1, f.queued)
        f.assertStopped()
        assertNull(f.alarms.at)
    }

    @Test
    fun duplicateAlarmDeliveryDoesNotRestartOrMultiplyTheOutput() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        val starts = f.output.starts
        repeat(5) { f.store.onPhaseAlarm(original.phaseKey()) }

        assertEquals(starts, f.output.starts)
        assertEquals(1, f.output.vibrationStarts)
        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
        assertEquals(original, f.store.current())
    }

    @Test
    fun everyReleasedTransitionSelectsItsOwnSound() = runBlocking {
        val cases = listOf(
            Triple("work", 0.0, PomodoroSound.WORK_TO_SHORT_REST),
            Triple("work", 3.0, PomodoroSound.WORK_TO_LONG_REST),
            Triple("shortRest", 1.0, PomodoroSound.SHORT_REST_TO_WORK),
            Triple("longRest", 4.0, PomodoroSound.LONG_REST_TO_WORK),
        )
        for ((kind, completed, sound) in cases) {
            val f = Fixture()
            val session = f.start().copy(phaseKind = kind, completed = completed)
            f.database.session = session
            f.due()
            f.store.onPhaseAlarm(session.phaseKey())

            assertEquals(sound, f.output.sound)
            assertEquals(session, f.store.current())
            assertTrue(f.output.audio)
            assertTrue(f.output.vibrating)
        }
        assertEquals(4, cases.map { it.third }.toSet().size)
    }

    @Test
    fun daysOfOvertimeHaveNoSilenceOrDurationCap() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        f.now = START.plusSeconds(365L * 24 * 60 * 60)
        f.store.onPhaseAlarm(original.phaseKey())

        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
        assertEquals(1, f.output.starts)
        assertEquals(original, f.store.current())
        assertEquals(0, f.database.creates)
    }

    @Test
    fun staleAlarmNextEndAndDismissCannotChangeTheNewPhase() = runBlocking {
        val f = Fixture()
        val old = f.startAndRing().phaseKey()
        f.store.nextPhase()
        val rest = checkNotNull(f.store.current())
        f.now = START.plusSeconds(120)
        f.store.onPhaseAlarm(rest.phaseKey())
        val posts = f.output.posts

        f.store.onPhaseAlarm(old)
        assertEquals(PomodoroStep.Idle, f.store.nextPhaseIfCurrent(old))
        assertEquals(PomodoroStep.Idle, f.store.endIfCurrent(old))
        f.store.repostRinging(old)
        assertFalse(f.store.withRingingPhase(old) { error("stale service start") })

        assertEquals(rest, f.store.current())
        assertEquals(rest.phaseKey(), f.controller.current?.phase)
        assertEquals(posts, f.output.posts)
        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
    }

    @Test
    fun endedSessionsAndDifferentWorkspacesRejectOldServiceStarts() = runBlocking {
        val f = Fixture()
        val old = f.startAndRing().phaseKey()
        f.store.end()
        assertFalse(f.store.withRingingPhase(old) { error("ended session played") })
        assertFalse(f.store.withRingingPhase(null) { error("sticky idle service played") })
        val next = f.store.start(LENGTHS, EntryFields(), 99)
        f.now = next.phaseStartedAt.plusSeconds(60)
        f.store.onPhaseAlarm(next.phaseKey())
        assertFalse(f.store.withRingingPhase(old) { error("old workspace played") })
        assertEquals(99L, f.controller.current?.phase?.workspaceId)
    }

    @Test
    fun swipingRepostsWithoutStoppingEitherOutput() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        val posts = f.output.posts
        f.store.repostRinging(original.phaseKey())

        assertEquals(posts + 1, f.output.posts)
        assertEquals(1, f.output.starts)
        assertEquals(1, f.output.vibrationStarts)
        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
        assertEquals(original, f.store.current())
    }

    @Test
    fun recreatedServiceResumesOnlyAPersistedReachedPhase() = runBlocking {
        val f = Fixture()
        val original = f.start()
        assertFalse(f.store.withRingingPhase(null) { error("not due") })
        f.due()
        assertFalse(f.store.withRingingPhase(null) { error("not recorded") })
        f.store.onPhaseAlarm(original.phaseKey())
        val recreatedOutput = FakeOutput()
        val recreated = PomodoroRingController(recreatedOutput)
        assertTrue(f.store.withRingingPhase(null) { recreated.start(it) })
        assertEquals(original.phaseKey(), recreated.current?.phase)
        assertTrue(recreatedOutput.audio)
        assertTrue(recreatedOutput.vibrating)
        f.store.end()
        assertFalse(f.store.withRingingPhase(null) { error("ended sticky restart") })
    }

    @Test
    fun bootRecoveryDeliversAnExactAlarmInsteadOfStartingMediaPlaybackFromBoot() = runBlocking {
        val f = Fixture()
        val original = f.start()
        f.due()
        f.store.rearm(deferRingingToAlarm = true)

        assertNull(f.controller.current)
        assertEquals(f.now, f.alarms.at)
        assertEquals(original.phaseKey(), f.alarms.phase)
        assertEquals(original, f.store.current())
        f.store.onPhaseAlarm(original.phaseKey())
        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
    }

    @Test
    fun aClockChangeBeforeTheEndStopsOutputAndRearms() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        f.now = START.plusSeconds(30)
        f.store.rearm()

        f.assertStopped()
        assertNull(f.store.reachedPhaseEnd())
        assertEquals(START.plusSeconds(60), f.alarms.at)
        assertEquals(original, f.store.current())
    }

    @Test
    fun disarmAndDiscardReleaseBothOutputs() = runBlocking {
        val f = Fixture()
        f.startAndRing()
        f.store.disarm()
        f.assertStopped()
        assertFalse(f.store.withRingingPhase(null) { error("disarmed restart") })
        f.store.rearm()
        assertTrue(f.output.audio)
        f.store.discardRunningSession()
        f.assertStopped()
        assertNull(f.store.current())
    }

    @Test
    fun validationAndPlaybackCannotRaceNextPhase() = runBlocking {
        val f = Fixture()
        val original = f.startAndRing()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val start = launch {
            f.store.withRingingPhase(original.phaseKey()) {
                entered.complete(Unit)
                release.await()
                f.controller.start(it)
            }
        }
        entered.await()
        val next = async(start = CoroutineStart.UNDISPATCHED) { f.store.nextPhaseIfCurrent(original.phaseKey()) }
        assertFalse(next.isCompleted)
        release.complete(Unit)
        start.join()
        next.await()
        f.assertStopped()
        assertEquals("shortRest", f.store.current()?.phaseKind)
    }

    @Test
    fun anActionWithNoRemainingSessionStillReleasesTheOutputs() = runBlocking {
        val f = Fixture()
        f.startAndRing()
        f.database.session = null
        assertEquals(PomodoroStep.Idle, f.store.end())
        f.assertStopped()
        assertNull(f.store.reachedPhaseEnd())
    }

    @Test
    fun replacingARingStopsTheOldOutputAndDismissalOnlyRepostsItsOwnPhase() {
        val output = FakeOutput()
        val controller = PomodoroRingController(output)
        val first = PomodoroRing(PomodoroPhaseKey(1, "work", START, START, 0.0), PomodoroSound.WORK_TO_SHORT_REST)
        val second = first.copy(phase = first.phase.copy(kind = "shortRest", startedAt = START.plusSeconds(60)))
        controller.start(first)
        controller.start(second)
        val posts = output.posts
        controller.dismissed(first.phase)
        assertEquals(posts, output.posts)
        controller.dismissed(second.phase)
        assertEquals(posts + 1, output.posts)
        assertEquals(2, output.starts)
        assertEquals(1, output.audioStops)
        assertEquals(1, output.vibrationStops)
        assertEquals(second, controller.current)
    }

    @Test
    fun stopAttemptsVibrationCleanupEvenWhenAudioCleanupFails() {
        val output = object : PomodoroRingOutput {
            var cancelled = false
            var removed = false
            override fun show(ring: PomodoroRing) = Unit
            override fun play(sound: PomodoroSound) = Unit
            override fun vibrate() = Unit
            override fun stopAudio(): Unit = throw IllegalStateException("audio device failed")
            override fun stopVibration() { cancelled = true }
            override fun removeNotification() { removed = true }
        }
        val controller = PomodoroRingController(output)
        val result = runCatching { controller.stop() }
        assertTrue(result.isFailure)
        assertTrue(output.cancelled)
        assertTrue(output.removed)
        assertNull(controller.current)
    }

    private class Fixture {
        var now: Instant = START
        val database = MemoryDatabase()
        val alarms = FakeAlarms()
        val output = FakeOutput()
        val controller = PomodoroRingController(output)
        var queued = 0
        val store = PomodoroSessionStore(
            database, Dispatchers.Unconfined,
            object : Clock() {
                override fun instant(): Instant = now
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId?): Clock = this
            },
            accountTime = { null }, onQueued = { queued++ }, alarms = alarms,
            ringing = object : PomodoroRinging {
                override suspend fun start(ring: PomodoroRing) = controller.start(ring)
                override suspend fun stop() = controller.stop()
            },
        )
        suspend fun start(): PomodoroSession {
            val session = store.start(LENGTHS, EntryFields(description = "Focus offline"), 7)
            output.audioStops = 0
            output.vibrationStops = 0
            return session
        }
        fun due() { now = START.plusSeconds(60) }
        suspend fun startAndRing(): PomodoroSession {
            val original = start()
            due()
            store.onPhaseAlarm(original.phaseKey())
            return original
        }
        fun assertStopped() {
            assertNull(controller.current)
            assertFalse(output.audio)
            assertFalse(output.vibrating)
            assertFalse(output.notification)
        }
    }

    private class FakeOutput : PomodoroRingOutput {
        var audio = false
        var vibrating = false
        var notification = false
        var sound: PomodoroSound? = null
        var starts = 0
        var vibrationStarts = 0
        var audioStops = 0
        var vibrationStops = 0
        var posts = 0
        override fun show(ring: PomodoroRing) { notification = true; posts++ }
        override fun play(sound: PomodoroSound) { this.sound = sound; audio = true; starts++ }
        override fun vibrate() { vibrating = true; vibrationStarts++ }
        override fun stopAudio() { audio = false; audioStops++ }
        override fun stopVibration() { vibrating = false; vibrationStops++ }
        override fun removeNotification() { notification = false }
    }

    private class MemoryDatabase : PomodoroSessionDatabase {
        var session: PomodoroSession? = null
        var creates = 0
        override fun current(): PomodoroSession? = session
        override fun save(session: PomodoroSession) { this.session = session }
        override fun delete() { session = null }
        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            creates++
            return -creates.toLong()
        }
        override fun <T> transaction(block: () -> T): T = block()
    }

    private class FakeAlarms : PomodoroAlarmScheduler {
        var at: Instant? = null
        var phase: PomodoroPhaseKey? = null
        override val permissionDenied = false
        override fun schedule(triggerAt: Instant): Boolean { at = triggerAt; return true }
        override fun schedule(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean {
            this.phase = phase
            return schedule(triggerAt)
        }
        override fun cancel() { at = null; phase = null }
        override fun watchSession(running: Boolean) = Unit
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-20T09:00:00Z")
        val LENGTHS = PomodoroSessionConfig(1.0, 1.0, 1.0, 4.0)
    }
}
