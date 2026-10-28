package com.github.e0ipso.tokkl.pomodoro

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One exact alarm at the held phase's planned end, corrected by the state's clock offset and keyed
 * by `phase.start`. Every state recomputes it, a new phase or none cancels it, and a missed end
 * rings at once without moving the phase on. Rearming reads the held state and sends nothing.
 */
class PomodoroPhaseAlarmTest {

    private val key = PomodoroPhaseKey(T0)

    @Test
    fun armsOneAlarmAtThePlannedEndLessTheClockOffsetKeyedByThePhaseStart() = runBlocking {
        // The server's clock reads 90 s later than the phone's, so its planned end comes 90 s
        // earlier on the phone's clock.
        val f = AlarmFixture(heldState(revision = 3, offset = Duration.ofSeconds(90)))

        f.alarm.follow()

        assertEquals(T0.plusSeconds(1_500 - 90) to key, f.alarms.armed)
        assertEquals(1, f.alarms.scheduled.size)
        assertEquals(0, f.alarms.cancels)
        assertNull(f.controller.current)
    }

    @Test
    fun eachStateRecomputesTheAlarmAndAnUnchangedOneArmsNothingMore() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.alarm.follow()
        f.alarm.follow()
        assertEquals(listOf(T0.plusSeconds(1_500) to key), f.alarms.scheduled)

        // A later revision of the same phase, received with the server 2 s ahead.
        f.held = heldState(revision = 4, offset = Duration.ofSeconds(2))
        f.alarm.follow()

        assertEquals(T0.plusSeconds(1_498) to key, f.alarms.armed)
        assertEquals(2, f.alarms.scheduled.size)
        assertEquals(0, f.alarms.cancels)
    }

    @Test
    fun aNewPhaseCancelsTheOldRingAndArmsTheNewPhase() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        assertEquals(key, f.controller.current?.phase)

        // Another device confirmed Next phase: the rest started at the work's planned end.
        f.held = heldState(revision = 4, pomodoro = rest())
        f.alarm.follow()

        assertNull(f.controller.current)
        assertFalse(f.output.audio)
        assertFalse(f.output.vibrating)
        assertEquals(T0.plusSeconds(1_800) to PomodoroPhaseKey(T0.plusSeconds(1_500)), f.alarms.armed)
    }

    @Test
    fun noSessionCancelsTheAlarmAndStopsTheRing() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()

        f.held = heldState(revision = 4, pomodoro = null)
        f.alarm.follow()

        assertNull(f.alarms.armed)
        assertTrue(f.alarms.cancels > 0)
        assertNull(f.controller.current)
        assertFalse(f.output.audio)
    }

    @Test
    fun withoutTheTimerApiOrAHeldStateNothingIsArmed() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.available = false
        f.alarm.follow()
        f.alarm.follow(rearm = true)
        assertTrue(f.alarms.scheduled.isEmpty())

        f.available = true
        f.alarm.follow()
        assertEquals(key, f.alarms.armed?.second)

        // The capability goes away while armed, as on Toggl Track or after sign-out.
        f.available = false
        f.alarm.follow()
        assertNull(f.alarms.armed)

        f.available = true
        f.held = null
        f.alarm.follow(rearm = true)
        assertNull(f.alarms.armed)
        assertEquals(1, f.alarms.scheduled.size)
    }

    @Test
    fun aMissedEndIsArmedInThePastAndItsDeliveryRingsWithoutMovingThePhaseOn() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.clock.now = T0.plusSeconds(1_800)

        f.alarm.follow()

        // AlarmManager delivers a past time at once, and its receiver may start the ring.
        assertEquals(T0.plusSeconds(1_500) to key, f.alarms.armed)
        assertNull(f.controller.current)
        f.alarm.onAlarm(key)
        assertEquals(key, f.controller.current?.phase)
        assertEquals(f.ring, f.controller.current)
        // The phase stays where it was, in overtime since its planned end.
        assertEquals(PomodoroClock(overtime = true, seconds = 300), f.controller.current?.clockAt(f.clock.now))
    }

    @Test
    fun anEarlyDeliveryArmsThePlannedEndAgain() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.alarm.follow()
        f.alarms.armed = null
        f.clock.now = T0.plusSeconds(600)

        f.alarm.onAlarm(key)

        assertNull(f.controller.current)
        assertEquals(T0.plusSeconds(1_500) to key, f.alarms.armed)
    }

    @Test
    fun aDeliveryForAnotherPhaseRingsNothing() = runBlocking {
        val f = AlarmFixture(heldState(revision = 4, pomodoro = rest()))
        f.alarm.follow()
        f.clock.now = T0.plusSeconds(2_000)

        f.alarm.onAlarm(key)

        assertNull(f.controller.current)
        assertEquals(0, f.ringing.starts)
    }

    @Test
    fun rearmingArmsTheStoredPhaseAgainAndAPastEndOnlyThroughTheAlarm() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.alarm.follow()
        f.alarm.follow(rearm = true)
        assertEquals(2, f.alarms.scheduled.size)

        // A reboot after the planned end: no ring starts from the boot broadcast itself.
        val booted = AlarmFixture(heldState(revision = 3))
        booted.clock.now = T0.plusSeconds(3_600)
        booted.alarm.follow(rearm = true)
        assertEquals(T0.plusSeconds(1_500) to key, booted.alarms.armed)
        assertEquals(0, booted.ringing.starts)
    }

    @Test
    fun aDeniedPermissionArmsNothingRingsAPastEndAtOnceAndAGrantArmsAgain() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.alarms.denied = true
        f.alarm.follow()
        assertTrue(f.alarm.permissionDenied)
        assertTrue(f.alarms.scheduled.isEmpty())
        assertNull(f.controller.current)

        f.alarms.denied = false
        f.alarm.follow(rearm = true)
        assertEquals(T0.plusSeconds(1_500) to key, f.alarms.armed)

        val missed = AlarmFixture(heldState(revision = 3))
        missed.alarms.denied = true
        missed.clock.now = T0.plusSeconds(1_600)
        missed.alarm.follow()
        assertEquals(key, missed.controller.current?.phase)
    }

    @Test
    fun aRevisionWithinTheRingingPhaseKeepsItsRingAndArmsNothing() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        val scheduled = f.alarms.scheduled.size

        f.held = heldState(revision = 4, description = "Renamed elsewhere", offset = Duration.ofSeconds(1))
        f.alarm.follow()

        assertEquals(4L, f.controller.current?.revision)
        assertEquals(1, f.output.plays)
        assertEquals(0, f.ringing.stops)
        assertEquals(scheduled, f.alarms.scheduled.size)
    }

    @Test
    fun releaseCancelsTheAlarmAndStopsTheRing() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()

        f.alarm.release()

        assertNull(f.alarms.armed)
        assertNull(f.controller.current)
        assertFalse(f.output.vibrating)
    }

    @Test
    fun theAlarmEndFollowsAnOffsetOfEitherSign() = runBlocking {
        val behind = AlarmFixture(heldState(revision = 3, offset = Duration.ofSeconds(-30)))
        behind.alarm.follow()
        assertEquals(Instant.parse("2026-10-28T08:25:30Z"), behind.alarms.armed?.first)
    }
}
