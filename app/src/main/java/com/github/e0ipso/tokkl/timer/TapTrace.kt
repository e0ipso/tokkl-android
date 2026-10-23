package com.github.e0ipso.tokkl.timer

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * Marks the time from a Start or Stop tap until the screen has drawn what the tap did, as an async
 * section of the system trace. The macrobenchmark module measures the sections with
 * TraceSectionMetric against the 200 ms goal (docs/PRD.md, Speed).
 */
fun interface TapTrace {

    /** Opens [section] and returns what closes it. */
    fun begin(section: String): () -> Unit

    companion object {
        /** Nonsecret observations of existing handler/trace points, installed only by diagnostics. */
        @Volatile
        internal var diagnostic: ((String) -> Unit)? = null

        /** From a Start tap until the screen shows the timer running. */
        const val START = "TimerStart"

        /** From a Stop tap until the screen shows the idle timer. */
        const val STOP = "TimerStop"

        /** Traces nothing. On the JVM, android.os.Trace is a stub that throws. */
        val None = TapTrace { {} }

        /**
         * The system trace, which Perfetto records while a benchmark runs. A section costs a check
         * of a flag when nothing traces.
         */
        val System = TapTrace { section ->
            diagnostic?.invoke("timer.model.accept trace=$section actual_touch_receipt=UNKNOWN")
            val cookie = cookies.incrementAndGet()
            Trace.beginAsyncSection(section, cookie)
            val end: () -> Unit = {
                Trace.endAsyncSection(section, cookie)
                diagnostic?.invoke("timer.trace.end trace=$section reason=shown_or_superseded")
            }
            end
        }

        /** Async sections of one name are told apart by their cookie. */
        private val cookies = AtomicInteger()
    }
}
