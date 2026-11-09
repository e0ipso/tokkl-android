package com.github.e0ipso.tokkl.timer

/**
 * The trace section of the last Start or Stop tap, open from the tap until the screen reports that
 * it drew what the tap did. Use it on the main thread.
 */
internal class TapSections(private val trace: TapTrace) {

    private var open: OpenTap? = null

    /** Opens [section] of the trace until the screen draws a state that is [done]. */
    fun begin(section: String, done: (TimerState) -> Boolean) {
        // A screen that stopped showing the timer never reported the last tap's state.
        open?.end?.invoke()
        open = OpenTap(trace.begin(section), done)
    }

    /** The screen has drawn [state], which ends the open section once it shows what the tap did. */
    fun shown(state: TimerState) {
        val tap = open ?: return
        if (tap.done(state)) {
            open = null
            tap.end()
        }
    }

    /** A tap's open trace section, which [end] closes once the screen shows a state that is [done]. */
    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)
}
