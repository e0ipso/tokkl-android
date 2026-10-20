package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * A [LoopbackRelay] to [server] that a test can make hang. While it hangs, it accepts connections
 * and answers nothing, as a network that has gone quiet does, and it cuts the connections it was
 * relaying, so a pooled connection cannot slip past. [release] drops the hung connections, which
 * the app sees as a network failure, and relays again.
 *
 * It can also hold one answer: [holdAnswerTo] lets a request through to the server and keeps the
 * server's answer from the app until [releaseAnswer], as a slow network does.
 */
class HangingRelay(server: ServerUrl) : Closeable {

    /** The start of the request whose answer to hold, until a connection sends it. */
    private val awaited = AtomicReference<ByteArray?>()

    /** Opens when [releaseAnswer] lets the held answer through. */
    @Volatile
    private var answerGate = CountDownLatch(0)

    @Volatile
    private var holding = false

    private val relay = server.api().let { LoopbackRelay(it.host, it.port) { Exchange() } }

    /** The relay as a server URL for the app. */
    val url: ServerUrl = checkNotNull(ServerUrl.parse("http://127.0.0.1:${relay.localPort}"))

    /** How many connections hang now. */
    val hungConnections: Int get() = relay.heldConnections

    /** The relay holds the answer that [holdAnswerTo] asked for. */
    val holdsAnswer: Boolean get() = holding

    /**
     * Holds the server's answer to the next request that starts with [request], such as
     * `GET /api/v9/me/time_entries?`, until [releaseAnswer]. The server reads and answers the
     * request at once; only the app waits. Plain http only, since the relay reads the request.
     */
    fun holdAnswerTo(request: String) {
        answerGate = CountDownLatch(1)
        awaited.set(request.toByteArray(Charsets.US_ASCII))
    }

    /** Passes the held answer on to the app, and holds no later one. */
    fun releaseAnswer() {
        awaited.set(null)
        answerGate.countDown()
    }

    /** Hangs every new connection and cuts the relayed ones. */
    fun hang() {
        relay.holdNewConnections = true
        relay.cutRelayed()
    }

    /** Drops the hung connections and relays new ones. */
    fun release() {
        relay.holdNewConnections = false
        relay.dropHeld()
    }

    /**
     * One connection. HTTP/1.1 sends a request only after the answer to the one before has ended,
     * so the first answer bytes after the awaited request are its answer.
     */
    private inner class Exchange : LoopbackRelay.ConnectionWatcher {
        private var tail = ByteArray(0)

        @Volatile
        private var holdNext = false

        override fun sent(bytes: ByteArray, count: Int) {
            val seen = tail + bytes.copyOf(count)
            val request = awaited.get()
            if (request != null && seen.contains(request) && awaited.compareAndSet(request, null)) holdNext = true
            tail = seen.copyOfRange(maxOf(0, seen.size - TAIL_SIZE), seen.size)
        }

        /** Waits while the answer is held. */
        override fun answering(bytes: ByteArray, count: Int) {
            if (!holdNext) return
            holdNext = false
            holding = true
            try {
                answerGate.await()
            } finally {
                holding = false
            }
        }
    }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    override fun close() {
        releaseAnswer()
        relay.close()
    }

    private companion object {
        /** Longer than any request start a test holds an answer to, so one split across two chunks still shows. */
        const val TAIL_SIZE = 256
    }
}
