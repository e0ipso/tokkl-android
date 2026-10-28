package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring of a held phase: it starts at the phase's planned end with the sound of the server's
 * `transition`, goes on with no limit until a state with another phase or none arrives, keeps
 * going through a new revision of the same phase, and an old phase's delivery, button or service
 * start cannot touch it. No framework or network is needed.
 */
class PomodoroRingingTest {

    private val key = PomodoroPhaseKey(T0)

    @Test
    fun thePhaseEndStartsBothOutputsWithTheTransitionsSoundAndNeverMovesThePhaseOn() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()

        assertEquals(key, f.controller.current?.phase)
        assertEquals(PomodoroSound.WORK_TO_SHORT_REST, f.output.sound)
        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
        assertEquals(f.ring, f.output.notification)
        assertEquals(3L, f.output.notification?.revision)
    }

    @Test
    fun everyServerTransitionSelectsItsOwnSound() = runBlocking {
        val cases = mapOf(
            PhaseTransition.WORK_TO_SHORT_REST to PomodoroSound.WORK_TO_SHORT_REST,
            PhaseTransition.WORK_TO_LONG_REST to PomodoroSound.WORK_TO_LONG_REST,
            PhaseTransition.SHORT_REST_TO_WORK to PomodoroSound.SHORT_REST_TO_WORK,
            PhaseTransition.LONG_REST_TO_WORK to PomodoroSound.LONG_REST_TO_WORK,
        )
        for ((transition, sound) in cases) {
            // The sound follows the transition the server sent, whatever the phase's kind.
            val f = AlarmFixture(heldState(revision = 3, pomodoro = work().copy(transition = transition, upcomingPhase = PhaseKind.LONG_REST)))
            f.ringNow()
            assertEquals(sound, f.output.sound)
            assertTrue(f.output.audio)
            assertTrue(f.output.vibrating)
        }
        assertEquals(4, cases.values.toSet().size)
    }

    @Test
    fun duplicateDeliveriesDoNotRestartOrMultiplyTheOutput() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        repeat(5) { f.alarm.onAlarm(key) }

        assertEquals(1, f.output.plays)
        assertEquals(1, f.output.vibrations)
        assertEquals(0, f.output.audioStops)
        assertTrue(f.output.audio)
    }

    @Test
    fun daysOfOvertimeHaveNoSilenceOrDurationCap() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        f.clock.now = T0.plus(Duration.ofDays(365))
        f.alarm.follow()
        f.alarm.onAlarm(key)

        assertTrue(f.output.audio)
        assertTrue(f.output.vibrating)
        assertEquals(1, f.output.plays)
        assertEquals(key, f.controller.current?.phase)
    }

    @Test
    fun aNewRevisionOfTheRingingPhaseKeepsPlayingAndShowsThatRevision() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        val posts = f.output.posts

        f.held = heldState(revision = 4, description = "Renamed on the web")
        f.alarm.follow()

        assertEquals(1, f.output.plays)
        assertEquals(0, f.output.audioStops)
        assertEquals(posts + 1, f.output.posts)
        assertEquals(4L, f.output.notification?.revision)
    }

    @Test
    fun aConfirmOnAnyDeviceStopsBothOutputs() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()

        f.held = heldState(revision = 4, pomodoro = rest())
        f.alarm.follow()

        assertNull(f.controller.current)
        assertFalse(f.output.audio)
        assertFalse(f.output.vibrating)
        assertNull(f.output.notification)
    }

    @Test
    fun anOfflineButtonLeavesTheRingAndTheStateAsTheyWere() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        val server = FakeServer(MutableStateFlow(f.held)).apply { answer = TimerActionResult.Offline }

        assertEquals(TimerActionResult.Offline, f.alarm.sendPhaseAction(server, key, revision = 3, end = false))
        assertEquals(TimerActionResult.Offline, f.alarm.sendPhaseAction(server, key, revision = 3, end = true))
        f.alarm.follow()

        assertEquals(listOf("next 3", "end 3"), server.calls)
        assertEquals(key, f.controller.current?.phase)
        assertTrue(f.output.audio)
        assertEquals(1, f.output.plays)
    }

    @Test
    fun oldPhaseDeliveriesButtonsRepostsAndServiceStartsCannotTouchTheNewPhase() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        f.held = heldState(revision = 4, pomodoro = rest())
        f.alarm.follow()
        f.clock.now = T0.plusSeconds(1_800)
        f.alarm.onAlarm(PomodoroPhaseKey(T0.plusSeconds(1_500)))
        val rest = checkNotNull(f.controller.current)
        val posts = f.output.posts
        val server = FakeServer(MutableStateFlow(f.held))

        f.alarm.onAlarm(key)
        f.alarm.repost(key)
        assertNull(f.alarm.sendPhaseAction(server, key, revision = 3, end = false))
        assertNull(f.alarm.sendPhaseAction(server, key, revision = 3, end = true))
        assertFalse(f.alarm.withRingingPhase(key) { error("an old phase played") })

        assertTrue(server.calls.isEmpty())
        assertEquals(rest, f.controller.current)
        assertEquals(posts, f.output.posts)
        assertEquals(1, f.output.audioStops)
    }

    @Test
    fun aRestartedServiceRingsOnlyTheHeldPhasePastItsPlannedEnd() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.alarm.follow()
        assertFalse(f.alarm.withRingingPhase(null) { error("not due") })

        f.clock.now = T0.plusSeconds(1_500)
        // A new process: the service restarts from the stored state with nothing in memory.
        val output = FakeOutput()
        val restarted = PomodoroRingController(output)
        assertTrue(f.alarm.withRingingPhase(null) { restarted.start(it) })
        assertEquals(key, restarted.current?.phase)
        assertTrue(output.audio)
        assertTrue(output.vibrating)

        f.held = heldState(revision = 4, pomodoro = null)
        assertFalse(f.alarm.withRingingPhase(null) { error("the session ended") })
        f.available = false
        f.held = heldState(revision = 5)
        assertFalse(f.alarm.withRingingPhase(null) { error("no timer API") })
    }

    @Test
    fun aSwipeRepostsWithoutStoppingEitherOutput() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.ringNow()
        val posts = f.output.posts

        f.alarm.repost(key)

        assertEquals(posts + 1, f.output.posts)
        assertEquals(1, f.output.plays)
        assertEquals(1, f.output.vibrations)
        assertTrue(f.output.audio)
    }

    @Test
    fun theServiceCheckAndAConfirmElsewhereCannotRace() = runBlocking {
        val f = AlarmFixture(heldState(revision = 3))
        f.alarm.follow()
        f.clock.now = T0.plusSeconds(1_500)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val start = launch {
            f.alarm.withRingingPhase(key) {
                entered.complete(Unit)
                release.await()
                f.controller.start(it)
            }
        }
        entered.await()
        f.held = heldState(revision = 4, pomodoro = rest())
        val confirmed = async(start = CoroutineStart.UNDISPATCHED) { f.alarm.follow() }
        assertFalse(confirmed.isCompleted)
        release.complete(Unit)
        start.join()
        confirmed.await()

        assertNull(f.controller.current)
        assertFalse(f.output.audio)
        assertFalse(f.output.vibrating)
    }

    @Test
    fun aRingOfAnotherPhaseStopsTheOldOutputFirst() {
        val output = FakeOutput()
        val controller = PomodoroRingController(output)
        val first = checkNotNull(heldState(revision = 3).ring())
        val second = checkNotNull(heldState(revision = 4, pomodoro = rest()).ring())

        controller.start(first)
        controller.start(second)

        assertEquals(2, output.plays)
        assertEquals(1, output.audioStops)
        assertEquals(1, output.vibrationStops)
        assertEquals(second, controller.current)
        assertEquals(PomodoroSound.SHORT_REST_TO_WORK, output.sound)
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
}
