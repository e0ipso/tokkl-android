package com.github.e0ipso.tokkl.timerstate

import kotlinx.coroutines.flow.Flow

/** The phase whose end rings, as the Pomodoro ringing service reports it. The Pomodoro widget redraws on it. */
interface PomodoroRingingPhase {

    /** Each state the ringing service reports, as the phase it rings, or null while nothing rings. */
    val ringingPhase: Flow<PomodoroPhaseKey?>
}
