package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget

/** What one button in the Quick Settings long-press dialog does. */
enum class TileControlAction { Pause, Resume, Stop }

/**
 * One button, bound to the [TimerTarget] the dialog was drawn with. A later change to the cache
 * does not rewrite it: [useTileControl] sends this target, and the queue's session and revision
 * checks drop it when the phone no longer shows that timer.
 */
data class TileControl(val action: TileControlAction, val target: TimerTarget)

/**
 * Pause and Stop, or Resume and Stop, for the timer [controls] show. Null when nothing runs and
 * nothing is paused, which is when a long press has no compact controls to offer.
 *
 * @property description the entry's description, blank when it has none.
 * @property paused true while the chain is paused, so the dialog says Paused and offers Resume.
 */
data class TileControlsPanel(
    val description: String,
    val paused: Boolean,
    val buttons: List<TileControl>,
)

/** The compact controls for [controls], or null when the timer is idle. */
fun tileControlsPanel(controls: TimerControls): TileControlsPanel? = when (controls) {
    is TimerControls.Running -> TileControlsPanel(
        description = controls.entry.description,
        paused = false,
        buttons = listOf(
            TileControl(TileControlAction.Pause, controls.target),
            TileControl(TileControlAction.Stop, controls.target),
        ),
    )
    is TimerControls.Paused -> TileControlsPanel(
        description = controls.fields.description,
        paused = true,
        buttons = listOf(
            TileControl(TileControlAction.Resume, controls.target),
            TileControl(TileControlAction.Stop, controls.target),
        ),
    )
    TimerControls.Idle -> null
}

/**
 * Runs [control] through the queue. Pause, Resume and Stop each take the target the button was
 * drawn with, as the widget and the notification do, so the call works offline and a stale button
 * does nothing.
 */
suspend fun EntryChanges.useTileControl(control: TileControl) {
    when (control.action) {
        TileControlAction.Pause -> pause(control.target)
        TileControlAction.Resume -> resume(control.target)
        TileControlAction.Stop -> stop(control.target)
    }
}
