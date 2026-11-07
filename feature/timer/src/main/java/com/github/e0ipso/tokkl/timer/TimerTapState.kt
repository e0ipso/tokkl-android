package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.timerstate.TimerControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What [TimerViewModel]'s taps share: the taps shown over the cache in [local], the controls the
 * cache read in [cached], the view model's [scope], and the trace of the last Start or Stop tap.
 * Use it on the main thread.
 *
 * @param snapshot the timer state as of now, without labels, which the taps act on.
 */
internal class TimerTapState(
    val local: MutableStateFlow<TimerTaps>,
    val cached: StateFlow<CachedControls>,
    val scope: CoroutineScope,
    private val trace: TapTrace,
    private val snapshot: () -> TimerState,
) {
    /** The trace section of the last Start or Stop tap, until the screen shows what it did. */
    private var openTap: OpenTap? = null

    /** The state as of now, without labels. */
    fun shown(): TimerState = snapshot()

    fun controls(): TimerControls? = (cached.value as? CachedControls.Read)?.controls

    fun running(): CachedTimeEntry? = controls()?.runningEntry()

    /** The edit shown over the running entry [shown], while it still matches the cache's entry. */
    fun editFor(shown: TimerState.Running): FieldEdit? {
        val read = cached.value as? CachedControls.Read ?: return null
        val entry = read.controls.runningEntry()?.takeIf { it.id == shown.entryId } ?: return null
        return local.value.edit?.takeIf { it.matches(entry, read) }
    }

    /** Waits until the controls pass [done], or gives up after [SETTLE_MS]. */
    suspend fun settle(done: (TimerControls) -> Boolean) {
        withTimeoutOrNull(SETTLE_MS) {
            cached.first { it is CachedControls.Read && done(it.controls) }
        }
    }

    /** Opens [section] of the trace until the screen draws a state that is [done]. */
    fun beginTap(section: String, done: (TimerState) -> Boolean) {
        // A screen that stopped showing the timer never reported the last tap's state.
        openTap?.end()
        openTap = OpenTap(trace.begin(section), done)
    }

    /** The screen has drawn [state], which ends the open tap's section once it shows what the tap did. */
    fun drawn(state: TimerState) {
        val tap = openTap ?: return
        if (tap.done(state)) {
            openTap = null
            tap.end()
        }
    }

    /** A tap's open trace section, which [end] closes once the screen shows a state that is [done]. */
    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)

    private companion object {
        /**
         * How long a tap shows what it did while the cache does not: far longer than a write to
         * the phone's database or a server's answer reaching the cache takes, so only a cache that
         * never shows the tap reaches it.
         */
        const val SETTLE_MS = 5_000L
    }
}
