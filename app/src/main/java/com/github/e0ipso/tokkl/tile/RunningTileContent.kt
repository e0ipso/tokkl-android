package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.timer.continueEntry
import com.github.e0ipso.tokkl.timer.lastStopped

/**
 * What the Quick Settings tile shows (docs/PRD.md N4), read from the phone's cache and the
 * server's timer state it last received, and so what a tap on it does.
 */
sealed interface RunningTileContent {

    /**
     * A timer runs: the tile is active, and a tap stops [entry], as [target] shows it, which ends
     * a pause chain.
     */
    data class Running(val entry: CachedTimeEntry, val target: TimerTarget) : RunningTileContent

    /**
     * The timer is paused (docs/PRD.md T1): the tile is inactive, says so, and a tap resumes
     * [description] as [target] shows it.
     */
    data class Paused(val description: String, val target: TimerTarget) : RunningTileContent

    /** No timer runs: the tile is inactive, and a tap continues [last], the stopped entry that started last. */
    data class Continue(val last: CachedTimeEntry) : RunningTileContent

    /** No timer runs and no entry is cached to continue, as after sign-out: the tile is unavailable. */
    data object Nothing : RunningTileContent
}

/** The tile for what the timer [controls] show, a paused timer too, and the cache's [entries]. */
fun runningTileContent(controls: TimerControls, entries: List<CachedTimeEntry>): RunningTileContent =
    when (controls) {
        is TimerControls.Running -> RunningTileContent.Running(controls.entry, controls.target)
        is TimerControls.Paused -> RunningTileContent.Paused(controls.fields.description, controls.target)
        TimerControls.Idle -> lastStopped(entries)?.let { RunningTileContent.Continue(it) } ?: RunningTileContent.Nothing
    }

/**
 * A tap on the tile while it shows [shown]. It acts on what the tile shows, not on what the cache
 * holds by now. Stop and Continue go through the change queue, so they are on the phone at once
 * and never wait for the network. Resume goes through [surface] to the server, and comes back as
 * the request for the caller to send. A stop of an entry that no longer runs, or a Resume of a
 * timer no longer paused as shown, does nothing.
 */
suspend fun tapTile(shown: RunningTileContent, surface: TimerSurfaceActions, changes: EntryChanges): SurfaceTap? =
    when (shown) {
        is RunningTileContent.Running -> surface.tap(TimerSurfaceAction.Stop, shown.target)
        is RunningTileContent.Paused -> surface.tap(TimerSurfaceAction.Resume, shown.target)
        is RunningTileContent.Continue -> {
            changes.continueEntry(shown.last)
            null
        }
        RunningTileContent.Nothing -> null
    }
