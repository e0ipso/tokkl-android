package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * One exact alarm for the held session's phase, or none.
 *
 * [permissionDenied] is what the Pomodoro page reads when a phase end will not wake the phone.
 */
interface PomodoroAlarmScheduler {
    val permissionDenied: Boolean

    /**
     * Arms [triggerAt] for [phase], replacing the alarm already armed. A time already past rings
     * at once. The phase travels with the delivery, so a late alarm cannot ring a newer phase.
     * Returns false when [permissionDenied].
     */
    fun schedule(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean

    /** Drops the armed alarm. Does nothing when none is armed. */
    fun cancel()
}

/** The phase whose end rings, as the Pomodoro ringing service reports it. The Pomodoro widget redraws on it. */
interface PomodoroRingingPhase {

    /** Each state the ringing service reports, as the phase it rings, or null while nothing rings. */
    val ringingPhase: Flow<PomodoroPhaseKey?>
}

/**
 * Redraws the Pomodoro widget at a planned end that no exact alarm rings for, and arms the next
 * redraw while the phase still runs. The widget implements it.
 */
fun interface PomodoroWidgetRedraw {
    suspend fun redraw(phase: PomodoroPhaseKey)
}
