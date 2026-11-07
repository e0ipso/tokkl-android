package com.github.e0ipso.tokkl

import android.util.Log
import com.github.e0ipso.tokkl.timer.TapTrace
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Two monotonic seconds from before a tap through every observation of its visible state. A tap
 * that waited for the network would run into OkHttp's 10-second timeouts first, so this proves
 * the tap stays on the phone. A debug build on a CI emulator can take a second to draw its first
 * Running frame.
 */
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
            val expired = System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(LIMIT_SECONDS)
            // At most first and terminal observations, never a record for every poll.
            if (firstObservation || observed || expired) {
                BlackholeDiagnostics.record(
                    "observation action=$action check=$what begin_nano_ns=$observationStarted " +
                        "end_nano_ns=$observationFinished observed=$observed expired=$expired",
                )
            }
            firstObservation = false
            if (expired) {
                throw AssertionError("$action: $what did not appear within $LIMIT_SECONDS seconds of the tap")
            }
            if (observed) return
            Thread.sleep(10)
        }
    }

    private companion object {
        const val LIMIT_SECONDS = 2L
    }
}

/** Scoped to the two diagnostic cases. No payloads, entry IDs, coordinates or account data. */
internal object BlackholeDiagnostics {
    private const val LIMIT = 160
    private val records = AtomicInteger()

    @Volatile private var active = false

    @Volatile private var phase = "inactive"

    fun begin(name: String) {
        records.set(0)
        phase = name
        active = true
        record("begin clock=System.nanoTime_ns actual_touch_receipt=UNKNOWN")
    }

    /**
     * Gives the Timer page view models made from now on a tap trace that also records when the
     * model accepts a Start or Stop tap and when the screen shows what it did. Call it before the
     * activity opens; [finish] puts the system trace back.
     */
    fun traceTimerTaps() {
        GlobalContext.get().declare<TapTrace>(RecordingTapTrace, allowOverride = true)
    }

    private val RecordingTapTrace = TapTrace { section ->
        record("timer.model.accept trace=$section actual_touch_receipt=UNKNOWN")
        val end = TapTrace.System.begin(section)
        val recorded: () -> Unit = {
            end()
            record("timer.trace.end trace=$section reason=shown_or_superseded")
        }
        recorded
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

    /**
     * Request paths only, so an unrelated background call cannot hide behind a relay count. Then
     * which of the app's current-entry readers is inside an OkHttp call, read from the threads'
     * stacks, so the app needs no hook for it.
     */
    fun recordHttpCalls() {
        if (!active) return
        val dispatcher = appHttp.instance.dispatcher
        val calls = dispatcher.runningCalls() + dispatcher.queuedCalls()
        record("http.calls=" + calls.joinToString { it.request().url.encodedPath })
        record("http.producers=" + producersInCalls().joinToString())
    }

    /** The simple names of [CURRENT_READERS] on the stack of each thread that is inside OkHttp now. */
    private fun producersInCalls(): List<String> = Thread.getAllStackTraces().values.mapNotNull { frames ->
        if (frames.none { it.className.startsWith("okhttp3.") }) return@mapNotNull null
        frames.firstNotNullOfOrNull { frame -> CURRENT_READERS.firstOrNull { frame.className.startsWith(it) } }
    }.map { it.substringAfterLast('.') }

    /** The classes whose current-entry reads, `GET /api/v9/me/time_entries/current`, the diagnostics name. */
    private val CURRENT_READERS = listOf(
        "com.github.e0ipso.tokkl.tile.TileRefresh",
        "com.github.e0ipso.tokkl.cache.RunningCheckWorker",
        "com.github.e0ipso.tokkl.cache.ChangeTries",
    )

    fun finish() {
        record("finish")
        GlobalContext.get().declare(TapTrace.System, allowOverride = true)
        active = false
        phase = "inactive"
    }
}
