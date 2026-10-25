package com.github.e0ipso.tokkl

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A plain TCP relay from the phone's 127.0.0.1 to [host] and [port], so a test can reach a test
 * server under `localhost`, a host name that the harness's certificate does not name. TLS passes
 * through untouched, so the app checks the server's own certificate. It is the test sources' only
 * relay: [HangingRelay] and [RequestCounter] build on it.
 *
 * A test that needs to see what passes through passes [watchConnections]. The relay calls it once
 * for each connection it relays and shows the watcher it returns every chunk on that connection,
 * in order, before passing the chunk on.
 *
 * While [holdNewConnections] is set, the relay accepts each new connection and answers nothing, as
 * a network that has gone quiet does. [dropHeld] closes those, and [cutRelayed] closes the ones
 * it is passing on. [relayTo] sends new connections to another server at the same local port.
 */
class LoopbackRelay(
    host: String,
    port: Int,
    private val watchConnections: () -> ConnectionWatcher? = { null },
) : Closeable {

    /** Sees one connection's bytes as the relay passes them on. */
    fun interface ConnectionWatcher {
        /** The app sent the first [count] bytes of [bytes]. The relay reuses [bytes] for the next chunk. */
        fun sent(bytes: ByteArray, count: Int)

        /**
         * The server's answer goes on with the first [count] bytes of [bytes] once this returns, so
         * a watcher can hold it back. An [IOException] thrown here cuts the connection on both
         * sides and the chunk never reaches the app. The relay reuses [bytes] for the next chunk.
         */
        fun answering(bytes: ByteArray, count: Int) {}
    }

    // The address the URL names. getLoopbackAddress() may be ::1 instead.
    private val listener = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val relayed = CopyOnWriteArrayList<Socket>()
    private val held = CopyOnWriteArrayList<Socket>()

    /** The server's host and port, for the connections the relay accepts next. */
    @Volatile
    private var target = host to port

    /** The port the relay listens on. */
    val localPort: Int = listener.localPort

    /** Connections the relay could not pass on to the server. */
    val failures = CopyOnWriteArrayList<String>()

    /** Whether the relay holds each connection it accepts from now on, instead of relaying it. */
    @Volatile
    var holdNewConnections = false

    /** How many connections the relay holds now. */
    val heldConnections: Int get() = held.size

    init {
        thread(isDaemon = true, name = "loopback-relay") {
            while (true) {
                val app = try {
                    listener.accept()
                } catch (e: IOException) {
                    return@thread
                }
                if (holdNewConnections) {
                    held += app
                } else {
                    relay(app)
                }
            }
        }
    }

    /**
     * Relays the connections it accepts from now on to [host] and [port]. The open ones stay with
     * the server they reached until [cutRelayed].
     */
    fun relayTo(host: String, port: Int) {
        target = host to port
    }

    /** Cuts the connections the relay is passing on, on both sides. */
    fun cutRelayed() {
        relayed.removeIf { it.close(); true }
    }

    /** Closes the connections the relay holds, which the app sees as a network failure. */
    fun dropHeld() {
        held.removeIf { it.close(); true }
    }

    private fun relay(app: Socket) {
        val (host, port) = target
        val server = try {
            Socket(host, port)
        } catch (e: IOException) {
            failures += "connect: $e"
            app.close()
            return
        }
        relayed += app
        relayed += server
        val watcher = watchConnections()
        // Both directions' streams first: a pipe that ends closes both sockets, and a stream asked
        // for after that throws on a thread of its own, which ends the test process.
        val fromApp: InputStream
        val toServer: OutputStream
        val fromServer: InputStream
        val toApp: OutputStream
        try {
            fromApp = app.getInputStream()
            toServer = server.getOutputStream()
            fromServer = server.getInputStream()
            toApp = app.getOutputStream()
        } catch (e: IOException) {
            failures += "streams: $e"
            app.close()
            server.close()
            return
        }
        thread(isDaemon = true) { pipe(fromApp, toServer, watcher?.let { it::sent }, app, server) }
        thread(isDaemon = true) { pipe(fromServer, toApp, watcher?.let { it::answering }, app, server) }
    }

    /** Copies [input] to [output] until either side closes, showing each chunk to [beforeWrite] first. */
    private fun pipe(input: InputStream, output: OutputStream, beforeWrite: ((ByteArray, Int) -> Unit)?, vararg ends: Socket) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                beforeWrite?.invoke(buffer, read)
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: IOException) {
            // A side closed its connection.
        } finally {
            ends.forEach { it.close() }
        }
    }

    override fun close() {
        listener.close()
        cutRelayed()
        dropHeld()
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
