package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey

/**
 * Redraws the Pomodoro widget at a planned end that no exact alarm rings for, and arms the next
 * redraw while the phase still runs. The widget implements it.
 */
fun interface PomodoroWidgetRedraw {
    suspend fun redraw(phase: PomodoroPhaseKey)
}
