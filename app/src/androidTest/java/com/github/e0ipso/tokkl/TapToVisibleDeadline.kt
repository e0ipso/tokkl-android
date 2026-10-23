package com.github.e0ipso.tokkl

import java.util.concurrent.TimeUnit

/** One monotonic second from before a tap through every observation of its visible state. */
class TapToVisibleDeadline(private val action: String) {
    private val started = System.nanoTime()

    /** Polls observations only. The caller dispatches the action once, before awaiting state. */
    fun await(what: String, condition: () -> Boolean) {
        while (true) {
            val observed = condition()
            // Check even a successful observation: a slow UI read cannot extend the deadline.
            if (System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(1)) {
                throw AssertionError("$action: $what did not appear within one second of the tap")
            }
            if (observed) return
            Thread.sleep(10)
        }
    }
}
