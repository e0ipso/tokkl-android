package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ApiCallCancellation

/**
 * The time and the delayed work of [TimerSync], so its watchdog and backoff can be checked without
 * real waits.
 */
interface TimerSyncClock {

    /** Milliseconds on a clock that never goes back. */
    fun now(): Long

    /** Runs [task] once, [delayMillis] from now, unless the returned function cancels it first. */
    fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit
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
 * - A 401 or 403 ends it for that account: [stopped] hears it and nothing reconnects until [run]
 *   names another account.
 *
 * Callbacks of a stream this owner has cancelled or replaced change nothing, so a late answer of
 * a signed-out or replaced account cannot restart, stop or prompt for the current one. Every
 * method may be called from any thread.
 *
 * @param A what tells accounts apart. The app uses the account's [TimerStateSource].
 * @param open starts holding the stream for the account over [TimerSyncLink], off the calling
 *   thread, and reports through the link's callbacks, ending with [TimerSyncLink.ended].
 * @param stopped hears that a stream ended in a way no reconnect can fix, [StreamEnd.Refused] or
 *   [StreamEnd.SignedOut], for the account that held it. It is called once per such end.
 */
class TimerSync<A : Any>(
    private val clock: TimerSyncClock,
    private val open: (account: A, link: TimerSyncLink) -> Unit,
    private val stopped: (account: A, end: StreamEnd) -> Unit,
) {

    private val lock = Any()

    /** The account the stream is held for, or null while stopped. */
    private var account: A? = null

    /** The account whose credential the server refused, which [run] does not retry. */
    private var refused: A? = null

    /** The open or opening call, or null while none is. */
    private var link: TimerSyncLink? = null

    private var cancelWatchdog: (() -> Unit)? = null

    /** The scheduled reconnect, which is current only while it is this object. */
    private var reconnect: Any? = null
    private var cancelReconnect: (() -> Unit)? = null

    /** Failures since the last healthy stream, which set the next delay. */
    private var failures = 0
    private var heardAt = 0L
    private var connectedAt: Long? = null

    /**
     * Holds the stream for [account]. A stream held for another account is cancelled first. While
     * it already holds or waits to reconnect the stream for [account], or the server refused
     * [account]'s credential, it does nothing, so a repeated start never opens a second call.
     */
    fun run(account: A) {
        val opened = synchronized(lock) {
            if (account == this.account || account == refused) return
            dropLocked()
            this.account = account
            refused = null
            failures = 0
            connectLocked()
        }
        open(account, opened)
    }

    /**
     * Cancels the stream and every reconnect, and forgets the account. Sign-out, losing the
     * timer state capability and the end of the service call it.
     */
    fun stop() {
        synchronized(lock) {
            dropLocked()
            account = null
            refused = null
        }
    }

    /**
     * A network became the phone's default. While waiting to reconnect, it connects at once with
     * the delays started over. While a call is open or opening, it does nothing.
     */
    fun networkAvailable() {
        val (account, opened) = synchronized(lock) {
            val account = account ?: return
            if (link != null) return
            dropLocked()
            failures = 0
            account to connectLocked()
        }
        open(account, opened)
    }

    /** The phone lost its default network, and an open call with it. Closes the call to reconnect later. */
    fun networkLost() {
        synchronized(lock) {
            if (link != null) retryLocked()
        }
    }

    private fun connected(link: TimerSyncLink) {
        synchronized(lock) {
            if (link !== this.link) return
            connectedAt = clock.now()
            heardAt = clock.now()
        }
    }

    private fun alive(link: TimerSyncLink) {
        synchronized(lock) {
            if (link === this.link) heardAt = clock.now()
        }
    }

    private fun ended(link: TimerSyncLink, end: StreamEnd) {
        val gone = synchronized(lock) {
            if (link !== this.link) return
            when (end) {
                StreamEnd.Ended, is StreamEnd.Failed -> {
                    retryLocked()
                    return
                }
                StreamEnd.Refused, StreamEnd.SignedOut -> {
                    val account = this.account
                    dropLocked()
                    this.account = null
                    if (end == StreamEnd.Refused) refused = account
                    account
                }
            }
        }
        if (gone != null) stopped(gone, end)
    }

    /** A new link for [account], watched for silence from now. Call it holding [lock]. */
    private fun connectLocked(): TimerSyncLink {
        val next = TimerSyncLink(::connected, ::alive, ::ended)
        link = next
        heardAt = clock.now()
        connectedAt = null
        watchLocked(next, SILENCE_MILLIS)
        return next
    }

    private fun watchLocked(watched: TimerSyncLink, delayMillis: Long) {
        cancelWatchdog = clock.schedule(delayMillis) { checkSilence(watched) }
    }

    private fun checkSilence(watched: TimerSyncLink) {
        synchronized(lock) {
            if (watched !== link) return
            val silent = clock.now() - heardAt
            if (silent >= SILENCE_MILLIS) retryLocked() else watchLocked(watched, SILENCE_MILLIS - silent)
        }
    }

    /** Closes the call, if any, and schedules the next connection. Call it holding [lock]. */
    private fun retryLocked() {
        val healthy = connectedAt?.let { clock.now() - it >= HEALTHY_MILLIS } == true
        dropLocked()
        if (healthy) failures = 0
        val delay = delayAfter(failures)
        failures++
        val ticket = Any()
        reconnect = ticket
        cancelReconnect = clock.schedule(delay) { reconnect(ticket) }
    }

    private fun reconnect(ticket: Any) {
        val (account, opened) = synchronized(lock) {
            if (reconnect !== ticket) return
            reconnect = null
            cancelReconnect = null
            val account = account ?: return
            account to connectLocked()
        }
        open(account, opened)
    }

    /** Cancels the call, its watchdog and the scheduled reconnect. Call it holding [lock]. */
    private fun dropLocked() {
        link?.cancel()
        link = null
        connectedAt = null
        cancelWatchdog?.invoke()
        cancelWatchdog = null
        cancelReconnect?.invoke()
        cancelReconnect = null
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
