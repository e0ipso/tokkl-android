package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions

/**
 * One button in the Quick Settings long-press dialog, bound to the [TimerTarget] the dialog was
 * drawn with. A later change to the cache or the timer state does not rewrite it:
 * [TimerSurfaceActions.tap] gets this target, and does nothing when the phone no longer shows
 * that timer.
 */
data class TileControl(val action: TimerSurfaceAction, val target: TimerTarget)

/**
 * Pause, where offered, and Stop, or Resume and Stop, for the timer [controls] show. Null when
 * nothing runs and nothing is paused, which is when a long press has no compact controls to offer.
 *
 * @property description the entry's description, blank when it has none.
 * @property paused true while the chain is paused, so the dialog says Paused and offers Resume.
 */
data class TileControlsPanel(
    val description: String,
    val paused: Boolean,
    val buttons: List<TileControl>,
)

/**
 * The compact controls for [controls], or null when the timer is idle. Pause shows only where the
 * controls offer it: with the server's timer API and outside a Pomodoro session.
 */
fun tileControlsPanel(controls: TimerControls): TileControlsPanel? = when (controls) {
    is TimerControls.Running -> TileControlsPanel(
        description = controls.entry.description,
        paused = false,
        buttons = listOfNotNull(
            TileControl(TimerSurfaceAction.Pause, controls.target).takeIf { controls.pausable },
            TileControl(TimerSurfaceAction.Stop, controls.target),
        ),
    )
    is TimerControls.Paused -> TileControlsPanel(
        description = controls.fields.description,
        paused = true,
        buttons = listOf(
            TileControl(TimerSurfaceAction.Resume, controls.target),
            TileControl(TimerSurfaceAction.Stop, controls.target),
        ),
    )
    TimerControls.Idle -> null
}
