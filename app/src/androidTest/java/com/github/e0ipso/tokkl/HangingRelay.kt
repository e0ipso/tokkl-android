package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A plain TCP relay from the phone's 127.0.0.1 to [server], which a test can make hang. While it
 * hangs, it accepts connections and answers nothing, as a network that has gone quiet does, and it
 * cuts the connections it was relaying, so a pooled connection cannot slip past. [release] drops
 * the hung connections, which the app sees as a network failure, and relays again.
 *
 * It can also hold one answer: [holdAnswerTo] lets a request through to the server and keeps the
 * server's answer from the app until [releaseAnswer], as a slow network does.
 */
class HangingRelay(server: ServerUrl) : Closeable {

    private val target = server.api().let { it.host to it.port }
    private val listener = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val relayed = CopyOnWriteArrayList<Socket>()
    private val hung = CopyOnWriteArrayList<Socket>()

    @Volatile
    private var hanging = false

    /** The start of the request whose answer to hold, until a connection sends it. */
    private val awaited = AtomicReference<ByteArray?>()

    /** Opens when [releaseAnswer] lets the held answer through. */
    @Volatile
    private var answerGate = CountDownLatch(0)

    @Volatile
    private var holding = false

    /** The relay as a server URL for the app. */
    val url: ServerUrl = checkNotNull(ServerUrl.parse("http://127.0.0.1:${listener.localPort}"))

    /** How many connections hang now. */
    val hungConnections: Int get() = hung.size

    init {
        thread(isDaemon = true, name = "hanging-relay") {
            while (true) {
                val app = try {
                    listener.accept()
                } catch (e: IOException) {
                    return@thread
                }
                if (hanging) {
                    hung += app
                } else {
                    relay(app)
                }
            }
        }
    }

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
        hanging = true
        relayed.forEach { it.close() }
        relayed.clear()
    }

    /** Drops the hung connections and relays new ones. */
    fun release() {
        hanging = false
        hung.forEach { it.close() }
        hung.clear()
    }

    private fun relay(app: Socket) {
        val server = try {
            Socket(target.first, target.second)
        } catch (e: IOException) {
            app.close()
            return
        }
        relayed += app
        relayed += server
        val exchange = Exchange()
        thread(isDaemon = true) { pipe(app.getInputStream(), server.getOutputStream(), exchange::requested, app, server) }
        thread(isDaemon = true) { pipe(server.getInputStream(), app.getOutputStream(), { _, _ -> exchange.answering() }, app, server) }
    }

    /** Copies [input] to [output] until either side closes, showing each chunk to [beforeWrite] first. */
    private fun pipe(input: InputStream, output: OutputStream, beforeWrite: (ByteArray, Int) -> Unit, vararg ends: Socket) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                beforeWrite(buffer, read)
                output.write(buffer, 0, read)
            }
        } catch (e: IOException) {
            // A side closed its connection.
        } finally {
            ends.forEach { it.close() }
        }
    }

    /**
     * One connection. HTTP/1.1 sends a request only after the answer to the one before has ended,
     * so the first answer bytes after the awaited request are its answer.
     */
    private inner class Exchange {
        private var tail = ByteArray(0)

        @Volatile
        private var holdNext = false

        /** The app sent the first [count] bytes of [bytes], which the relay passes on next. */
        fun requested(bytes: ByteArray, count: Int) {
            val seen = tail + bytes.copyOf(count)
            val request = awaited.get()
            if (request != null && seen.contains(request) && awaited.compareAndSet(request, null)) holdNext = true
            tail = seen.copyOfRange(maxOf(0, seen.size - TAIL_SIZE), seen.size)
        }

        /** The server's answer is on its way to the app; waits while it is held. */
        fun answering() {
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
        listener.close()
        relayed.forEach { it.close() }
        hung.forEach { it.close() }
    }

    private companion object {
        const val BUFFER_SIZE = 8192

        /** Longer than any request start a test holds an answer to, so one split across two chunks still shows. */
        const val TAIL_SIZE = 256
    }
}
