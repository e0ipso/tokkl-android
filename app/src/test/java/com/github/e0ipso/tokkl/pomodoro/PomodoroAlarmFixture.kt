package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers

/** A phone clock the test moves. */
internal class MovableClock(var now: Instant = T0) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}

/** AlarmManager as the phase alarm sees it: one alarm at a time, replaced by each schedule. */
internal class FakeAlarms : PomodoroAlarmScheduler {
    var denied = false
    var armed: Pair<Instant, PomodoroPhaseKey>? = null
    val scheduled = mutableListOf<Pair<Instant, PomodoroPhaseKey>>()
    var cancels = 0

    override val permissionDenied: Boolean get() = denied

    override fun schedule(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean {
        if (denied) return false
        armed = triggerAt to phase
        scheduled += triggerAt to phase
        return true
    }

    override fun cancel() {
        cancels++
        armed = null
    }
}

/** The ringing service's sound, vibration and notification, recorded. */
internal class FakeOutput : PomodoroRingOutput {
    var audio = false
    var vibrating = false
    var notification: PomodoroRing? = null
    var sound: PomodoroSound? = null
    var plays = 0
    var vibrations = 0
    var audioStops = 0
    var vibrationStops = 0
    var posts = 0

    override fun show(ring: PomodoroRing) {
        notification = ring
        posts++
    }

    override fun play(sound: PomodoroSound) {
        this.sound = sound
        audio = true
        plays++
    }

    override fun vibrate() {
        vibrating = true
        vibrations++
    }

    override fun stopAudio() {
        audio = false
        audioStops++
    }

    override fun stopVibration() {
        vibrating = false
        vibrationStops++
    }

    override fun removeNotification() {
        notification = null
    }
}

/**
 * The ringing service as ServicePomodoroRinging drives it, with its start played at once: a start
 * of the phase already ringing only shows that ring, and a stop that keeps the ringing phase
 * leaves it.
 */
internal class ServiceLikeRinging(private val controller: PomodoroRingController) : PomodoroRinging {
    var starts = 0
    var stops = 0

    override suspend fun start(ring: PomodoroRing) {
        starts++
        controller.start(ring)
    }

    override suspend fun update(ring: PomodoroRing) {
        if (controller.current?.phase == ring.phase) controller.start(ring)
    }

    override suspend fun stop(keep: PomodoroPhaseKey?) {
        val current = controller.current
        if (keep != null && (current == null || current.phase == keep)) return
        stops++
        controller.stop()
    }
}

/** A phase alarm over a held state, a capability flag, fake alarms and a ringing service with fake output. */
internal class AlarmFixture(initial: HeldTimerState? = heldState(revision = 3)) {
    var held: HeldTimerState? = initial
    var available = true
    val clock = MovableClock()
    val alarms = FakeAlarms()
    val output = FakeOutput()
    val controller = PomodoroRingController(output)
    val ringing = ServiceLikeRinging(controller)
    val alarm = PomodoroPhaseAlarm({ held }, { available }, alarms, ringing, Dispatchers.Unconfined, clock)

    /** The ring of the held phase, as the alarm derives it. */
    val ring: PomodoroRing get() = checkNotNull(held?.ring())

    /** Arms the held phase and lets its alarm go off once its planned end has come. */
    suspend fun ringNow() {
        alarm.follow()
        clock.now = maxOf(clock.now, ring.end)
        alarm.onAlarm(ring.phase)
    }
}
