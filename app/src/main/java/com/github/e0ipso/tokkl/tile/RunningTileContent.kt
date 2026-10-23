package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timer.continueEntry
import com.github.e0ipso.tokkl.timer.lastStopped

/**
 * What the Quick Settings tile shows (docs/PRD.md N4), read from the phone's cache, and so what a
 * tap on it does.
 */
sealed interface RunningTileContent {

    /** A timer runs: the tile is active, and a tap stops [entry], which ends a pause chain. */
    data class Running(val entry: CachedTimeEntry) : RunningTileContent

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

/** The tile for the cache's [running] entry, null when none runs, and its [entries]. */
fun runningTileContent(running: CachedTimeEntry?, entries: List<CachedTimeEntry>): RunningTileContent =
    when {
        running != null -> RunningTileContent.Running(running)
        else -> lastStopped(entries)?.let { RunningTileContent.Continue(it) } ?: RunningTileContent.Nothing
    }

/** The tile for what the timer [controls] show, a paused timer too, and the cache's [entries]. */
fun runningTileContent(controls: TimerControls, entries: List<CachedTimeEntry>): RunningTileContent =
    when (controls) {
        is TimerControls.Running -> RunningTileContent.Running(controls.entry)
        is TimerControls.Paused -> RunningTileContent.Paused(controls.fields.description, controls.target)
        TimerControls.Idle -> runningTileContent(running = null, entries)
    }

/**
 * A tap on the tile while it shows [shown]. It acts on what the tile shows, not on what the cache
 * holds by now, and goes through the change queue, so it is on the phone at once and never waits
 * for the network. A stop of an entry that no longer runs, or a Resume of a timer no longer
 * paused as shown, does nothing.
 */
suspend fun EntryChanges.tapTile(shown: RunningTileContent) {
    when (shown) {
        is RunningTileContent.Running -> stop(shown.entry.id)
        is RunningTileContent.Paused -> resume(shown.target)
        is RunningTileContent.Continue -> continueEntry(shown.last)
        RunningTileContent.Nothing -> Unit
    }
}
