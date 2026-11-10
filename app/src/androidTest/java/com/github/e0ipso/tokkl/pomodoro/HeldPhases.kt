package com.github.e0ipso.tokkl.pomodoro

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.TimerPomodoro
import com.github.e0ipso.tokkl.api.TimerPomodoroPhase
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.components.accountRefresh
import com.github.e0ipso.tokkl.components.capabilities
import com.github.e0ipso.tokkl.components.heldTimerState
import com.github.e0ipso.tokkl.components.timerStateIntake
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.model.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import java.time.Duration
import java.time.Instant

/**
 * Device-test helpers that give the app a server Pomodoro phase to ring, without waiting out a
 * real phase. The app must be signed in to the test server and have read it once, so it knows
 * the timer API; [learnTheTimerApi] does that. A state held here gets a revision far above any
 * the server has sent, so neither the event stream nor a refresh replaces it: the alarm, the ring
 * and the notifications see only what the test holds.
 */
internal object HeldPhases {
    private val app: TokklApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TokklApplication

    /** Far above the revision of any test account's timer on the test server. */
    private const val FIRST_REVISION = 1_000_000L

    /** Refreshes the signed-in account, which probes the timer API, and checks that the server has it. */
    fun learnTheTimerApi() {
        runBlocking { app.accountRefresh.refresh().join() }
        assertTrue("the test server offers the timer API", app.capabilities.state.value.timerState)
    }

    /**
     * Holds a session whose [transition] phase started [length] plus [overtime] ago, so its planned
     * end passed [overtime] ago; a negative [overtime] leaves that much of the phase. Returns the
     * held state.
     */
    fun hold(
        transition: PhaseTransition = PhaseTransition.WORK_TO_SHORT_REST,
        overtime: Duration = Duration.ofSeconds(65),
        length: Duration = Duration.ofMinutes(1),
        number: Int = 1,
    ): HeldTimerState {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val end = now.minus(overtime)
        val start = end.minus(length)
        val kind = when (transition) {
            PhaseTransition.WORK_TO_SHORT_REST, PhaseTransition.WORK_TO_LONG_REST -> PhaseKind.WORK
            PhaseTransition.SHORT_REST_TO_WORK -> PhaseKind.SHORT_REST
            PhaseTransition.LONG_REST_TO_WORK -> PhaseKind.LONG_REST
        }
        val upcoming = when (transition) {
            PhaseTransition.WORK_TO_SHORT_REST -> PhaseKind.SHORT_REST
            PhaseTransition.WORK_TO_LONG_REST -> PhaseKind.LONG_REST
            else -> PhaseKind.WORK
        }
        val session = TimerPomodoro(
            workMinutes = 1, shortRestMinutes = 1, longRestMinutes = 1, longRestEvery = 4,
            phase = TimerPomodoroPhase(
                kind,
                start,
                completed = if (kind == PhaseKind.WORK) number - 1 else number,
                plannedEnd = end,
            ),
            upcomingPhase = upcoming, transition = transition, number = number,
            entryId = ENTRY, pomodoroStart = start,
        )
        // No running entry, so the cache, the running notification and the widget stay as they are.
        return hold(
            TimerState(
                nextRevision(),
                now,
                runningEntry = null,
                pause = null,
                pomodoro = session,
                discardedPomodoro = null,
            ),
        )
    }

    /** Holds a state without a session, as after End session on any device. */
    fun holdNoSession(): HeldTimerState = hold(
        TimerState(
            nextRevision(),
            Instant.now(),
            runningEntry = null,
            pause = null,
            pomodoro = null,
            discardedPomodoro = null,
        ),
    )

    /** Holds [state] as if the server had just sent it. */
    fun hold(state: TimerState): HeldTimerState {
        val intake = app.timerStateIntake
        assertTrue("the holder took the state", intake.accept(intake.begin(), state))
        return checkNotNull(app.heldTimerState)
    }

    fun key(held: HeldTimerState): PomodoroPhaseKey = checkNotNull(held.ring()).phase

    /** Waits until the ringing service plays and vibrates, as the platform shows it. */
    fun awaitPlaying(): ObservedRing = PomodoroRingObservation.awaitPlaying()

    /** Waits until the ringing service has stopped both outputs and ended, as the platform shows it. */
    fun awaitStopped(): ObservedRing = PomodoroRingObservation.awaitStopped()

    private fun nextRevision(): Long = maxOf(FIRST_REVISION, (app.heldTimerState?.revision ?: 0) + 1)

    private const val ENTRY = 9_000_001L
}
