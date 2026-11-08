package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.api.ApiCallCancellation
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How [TimerSync] holds each account's stream, such as [TimerEventConnections]. */
interface TimerSyncConnections<A : Any> {

    /**
     * Starts holding the stream for [account] over [link], off the calling thread, and reports
     * through the link's callbacks, ending with [TimerSyncLink.ended].
     */
    fun open(account: A, link: TimerSyncLink)

    /**
     * Hears that a stream ended in a way no reconnect can fix, [StreamEnd.Refused] or
     * [StreamEnd.SignedOut], for the [account] that held it. It is called once per such end.
     */
    fun stopped(account: A, end: StreamEnd)
}

/**
 * The one owner of the timer event stream: at most one call open at a time, for one account, and
 * the reconnects after it ends. [run] holds the stream for an account, [stop] cancels it with every
 * reconnect, and the network callbacks reconnect at once when a network comes back.
 *
 * - A stream that sends no event or comment for about [SILENCE_MILLIS] is closed as dead. Tokkl
 *   sends a keep-alive every 25 s.
 * - After a stream ends or fails, the next connection waits [FIRST_DELAY_MILLIS], doubling after
 *   each failure up to [MAX_DELAY_MILLIS]. A stream that stayed open for [HEALTHY_MILLIS] starts
 *   that over. A network that comes back connects at once.
 * - A 401 or 403 ends it for that account: [TimerSyncConnections.stopped] hears it and nothing reconnects until [run]
 *   names another account.
 *
 * Callbacks of a stream this owner has cancelled or replaced change nothing, so a late answer of
 * a signed-out or replaced account cannot restart, stop or prompt for the current one.
 *
 * Every method may be called from any thread and returns at once: it hands its work to [scope],
 * whose dispatcher runs one task at a time, in the order the calls came. That dispatcher alone
 * reads and changes the stream's state, so it needs no lock. The watchdog and the reconnects are
 * delays in [scope].
 *
 * @param A what tells accounts apart. The app uses the account's [TimerStateSource].
 * @param scope runs every change of the stream's state, one at a time, such as a scope on
 *   `Dispatchers.Default.limitedParallelism(1)`.
 * @param now milliseconds on a clock that never goes back, for the watchdog and the backoff.
 * @param connections open each stream and hear the ends no reconnect can fix.
 */
class TimerSync<A : Any>(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val connections: TimerSyncConnections<A>,
) {

    /** The account the stream is held for, or null while stopped. */
    private var account: A? = null

    /** The account whose credential the server refused, which [run] does not retry. */
    private var refused: A? = null

    /** The open or opening call, or null while none is. */
    private var link: TimerSyncLink? = null

    private var watchdog: Job? = null

    /** The scheduled reconnect. */
    private var reconnect: Job? = null

    /** Failures since the last healthy stream, which set the next delay. */
    private var failures = 0
    private var heardAt = 0L
    private var connectedAt: Long? = null

    /**
     * Holds the stream for [account]. A stream held for another account is cancelled first. While
     * it already holds or waits to reconnect the stream for [account], or the server refused
     * [account]'s credential, it does nothing, so a repeated start never opens a second call.
     * [canRun] checks the caller's ownership when the stream's turn comes, after every call made
     * before this one. A service startup delayed by account I/O cannot reopen or replace a stream
     * after its service has stopped.
     */
    fun run(account: A, canRun: suspend () -> Boolean = { true }) {
        scope.launch {
            if (!canRun()) return@launch
            if (account == this@TimerSync.account || account == refused) return@launch
            drop()
            this@TimerSync.account = account
            refused = null
            failures = 0
            connections.open(account, connect())
        }
    }

    /**
     * Cancels the stream and every reconnect, and forgets the account. Sign-out, losing the
     * timer state capability and the end of the service call it. Returns the job that does it,
     * for a caller that waits.
     */
    fun stop(): Job = scope.launch {
        drop()
        account = null
        refused = null
    }

    /**
     * A network became the phone's default. While waiting to reconnect, it connects at once with
     * the delays started over. While a call is open or opening, it does nothing.
     */
    fun networkAvailable() {
        scope.launch {
            val account = account ?: return@launch
            if (link != null) return@launch
            drop()
            failures = 0
            connections.open(account, connect())
        }
    }

    /** The phone lost its default network, and an open call with it. Closes the call to reconnect later. */
    fun networkLost() {
        scope.launch {
            if (link != null) retry()
        }
    }

    private fun connected(link: TimerSyncLink) {
        scope.launch {
            if (link !== this@TimerSync.link) return@launch
            connectedAt = now()
            heardAt = now()
        }
    }

    private fun alive(link: TimerSyncLink) {
        scope.launch {
            if (link === this@TimerSync.link) heardAt = now()
        }
    }

    private fun ended(link: TimerSyncLink, end: StreamEnd) {
        scope.launch {
            if (link !== this@TimerSync.link) return@launch
            when (end) {
                StreamEnd.Ended, is StreamEnd.Failed -> retry()

                StreamEnd.Refused, StreamEnd.SignedOut -> {
                    val gone = account
                    drop()
                    account = null
                    if (end == StreamEnd.Refused) refused = gone
                    if (gone != null) connections.stopped(gone, end)
                }
            }
        }
    }

    /** A new link, watched for silence from now. */
    private fun connect(): TimerSyncLink {
        val next = TimerSyncLink(::connected, ::alive, ::ended)
        link = next
        heardAt = now()
        connectedAt = null
        watch(next, SILENCE_MILLIS)
        return next
    }

    private fun watch(watched: TimerSyncLink, delayMillis: Long) {
        watchdog = scope.launch {
            delay(delayMillis)
            checkSilence(watched)
        }
    }

    private fun checkSilence(watched: TimerSyncLink) {
        if (watched !== link) return
        val silent = now() - heardAt
        if (silent >= SILENCE_MILLIS) retry() else watch(watched, SILENCE_MILLIS - silent)
    }

    /** Closes the call, if any, and schedules the next connection. */
    private fun retry() {
        val healthy = connectedAt?.let { now() - it >= HEALTHY_MILLIS } == true
        drop()
        if (healthy) failures = 0
        val delay = delayAfter(failures)
        failures++
        reconnect = scope.launch {
            delay(delay)
            reconnect = null
            val account = account ?: return@launch
            connections.open(account, connect())
        }
    }

    /** Cancels the call, its watchdog and the scheduled reconnect. */
    private fun drop() {
        link?.cancel()
        link = null
        connectedAt = null
        watchdog?.cancel()
        watchdog = null
        reconnect?.cancel()
        reconnect = null
    }

    companion object {
        /** Silence after which a stream counts as dead: more than two of Tokkl's 25 s keep-alives. */
        const val SILENCE_MILLIS = 60_000L

        /** How long a stream must stay open for its end to count as a fresh start. */
        const val HEALTHY_MILLIS = 60_000L

        const val FIRST_DELAY_MILLIS = 1_000L
        const val MAX_DELAY_MILLIS = 120_000L

        /** The wait before the next connection after [failures] failures in a row. */
        internal fun delayAfter(failures: Int): Long =
            minOf(MAX_DELAY_MILLIS, FIRST_DELAY_MILLIS shl minOf(failures, MAX_SHIFT))

        /** Past this many doublings the delay is at its cap anyway. */
        private const val MAX_SHIFT = 16
    }
}

/**
 * One call of the timer event stream, from its opening to its end. The stream reports through it,
 * and [TimerSync] cancels the call through [cancellation]. Only the latest link of a [TimerSync]
 * counts; the callbacks of any other change nothing.
 */
class TimerSyncLink internal constructor(
    private val onConnected: (TimerSyncLink) -> Unit,
    private val onAlive: (TimerSyncLink) -> Unit,
    private val onEnded: (TimerSyncLink, StreamEnd) -> Unit,
) {

    /** Cancels the link's HTTP call, also one registered after cancelling. */
    val cancellation = ApiCallCancellation()

    /** False once cancelled: the stream takes no more events. */
    @Volatile
    var active: Boolean = true
        private set

    /** The server answered with the stream's headers. */
    fun connected() = onConnected(this)

    /** An event or a comment arrived. */
    fun alive() = onAlive(this)

    /** The stream is over, as [end] says. */
    fun ended(end: StreamEnd) = onEnded(this, end)

    internal fun cancel() {
        active = false
        cancellation.cancel()
    }
}
