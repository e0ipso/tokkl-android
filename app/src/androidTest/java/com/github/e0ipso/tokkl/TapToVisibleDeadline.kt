package com.github.e0ipso.tokkl

import android.util.Log
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.timer.TapTrace
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** One monotonic second from before a tap through every observation of its visible state. */
class TapToVisibleDeadline(private val action: String) {
    private val started = System.nanoTime()

    init {
        BlackholeDiagnostics.mark("deadline action=$action started_nano_ns=$started")
    }

    /** Polls observations only. The caller dispatches the action once, before awaiting state. */
    fun await(what: String, condition: () -> Boolean) {
        var firstObservation = true
        while (true) {
            val observationStarted = System.nanoTime()
            val observed = condition()
            val observationFinished = System.nanoTime()
            // Check even a successful observation: a slow UI read cannot extend the deadline.
            val expired = System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(1)
            // At most first and terminal observations, never a record for every poll.
            if (firstObservation || observed || expired) {
                BlackholeDiagnostics.record(
                    "observation action=$action check=$what begin_nano_ns=$observationStarted " +
                        "end_nano_ns=$observationFinished observed=$observed expired=$expired",
                )
            }
            firstObservation = false
            if (expired) {
                throw AssertionError("$action: $what did not appear within one second of the tap")
            }
            if (observed) return
            Thread.sleep(10)
        }
    }
}

/** Scoped to the two diagnostic cases. No payloads, entry IDs, coordinates or account data. */
internal object BlackholeDiagnostics {
    private const val LIMIT = 160
    private val records = AtomicInteger()
    @Volatile private var active = false
    @Volatile private var phase = "inactive"
    private var previousRequestDiagnostic: ((String) -> Unit)? = null
    private var previousTapDiagnostic: ((String) -> Unit)? = null

    fun begin(name: String) {
        records.set(0)
        phase = name
        previousRequestDiagnostic = RunningCheck.currentRequestDiagnostic
        previousTapDiagnostic = TapTrace.diagnostic
        active = true
        RunningCheck.currentRequestDiagnostic = { record(it) }
        TapTrace.diagnostic = { record(it) }
        record("begin clock=System.nanoTime_ns actual_touch_receipt=UNKNOWN")
    }

    fun mark(name: String) {
        if (!active) return
        phase = name
        record("phase")
    }

    fun record(event: String) {
        if (!active) return
        val received = System.nanoTime()
        val number = records.incrementAndGet()
        if (number <= LIMIT) {
            Log.i("TokklBlackhole", "record=$number nano_ns=$received phase=$phase $event")
        } else if (number == LIMIT + 1) {
            Log.i("TokklBlackhole", "nano_ns=$received diagnostic_limit=$LIMIT further_events=UNKNOWN")
        }
    }

    fun finish() {
        record("finish")
        RunningCheck.currentRequestDiagnostic = previousRequestDiagnostic
        TapTrace.diagnostic = previousTapDiagnostic
        active = false
        phase = "inactive"
    }
}
