package com.github.e0ipso.tokkl.timer

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * Marks the time from a Start or Stop tap until the screen has drawn what the tap did, as an async
 * section of the system trace.
 */
fun interface TapTrace {

    /** Opens [section] and returns what closes it. */
    fun begin(section: String): () -> Unit

    companion object {
        /** From a Start tap until the screen shows the timer running. */
        const val START = "TimerStart"

        /** From a Stop tap until the screen shows the idle timer. */
        const val STOP = "TimerStop"

        /** Traces nothing. On the JVM, android.os.Trace is a stub that throws. */
        val None = TapTrace { {} }

        /**
         * The system trace, which Perfetto records on request. A section costs a check of a flag
         * when nothing traces.
         */
        val System = TapTrace { section ->
            val cookie = cookies.incrementAndGet()
            Trace.beginAsyncSection(section, cookie)
            val end: () -> Unit = { Trace.endAsyncSection(section, cookie) }
            end
        }

        /** Async sections of one name are told apart by their cookie. */
        private val cookies = AtomicInteger()
    }
}

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

    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)
}
