package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.timerstate.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.PomodoroWidgetRedraw
import kotlinx.coroutines.flow.first

/**
 * Redraws the Pomodoro widget once the held phase that [PomodoroWidgetRedraw.redraw] names has
 * ended, or arms the redraw again at its planned end. Another phase changes nothing.
 */
internal class PomodoroWidgetRedrawer(
    private val states: PomodoroWidgetStateSource,
    private val scheduler: PomodoroAlarmScheduler,
) : PomodoroWidgetRedraw {

    override suspend fun redraw(context: Context, phase: PomodoroPhaseKey) {
        val state = states.state.first() as? PomodoroWidgetState.Active
        if (state?.session?.phase?.start == phase.start) {
            if (state.ended) {
                PomodoroWidget().updateAll(context)
            } else {
                scheduler.schedule(state.end, phase)
            }
        }
    }
}
