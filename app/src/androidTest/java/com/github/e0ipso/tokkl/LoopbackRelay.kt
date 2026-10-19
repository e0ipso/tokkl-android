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
 * through untouched, so the app checks the server's own certificate.
 *
 * A test that needs to see what the app sends passes [watchRequests]. The relay calls it once for
 * each connection and hands the watcher it returns every chunk the app sends on that connection,
 * in order, before passing the chunk on.
 */
class LoopbackRelay(
    private val host: String,
    private val port: Int,
    private val watchRequests: () -> RequestWatcher? = { null },
) : Closeable {

    /** Sees one connection's request bytes as the relay passes them on. */
    fun interface RequestWatcher {
        /** The app sent the first [count] bytes of [bytes]. The relay reuses [bytes] for the next chunk. */
        fun sent(bytes: ByteArray, count: Int)
    }

    // The address the URL names. getLoopbackAddress() may be ::1 instead.
    private val listener = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val sockets = CopyOnWriteArrayList<Socket>()

    /** The port the relay listens on. */
    val localPort: Int = listener.localPort

    /** Connections the relay could not pass on to the server. */
    val failures = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true, name = "loopback-relay") {
            while (true) {
                val app = try {
                    listener.accept()
                } catch (e: IOException) {
                    return@thread
                }
                val server = try {
                    Socket(host, port)
                } catch (e: IOException) {
                    failures += "connect: $e"
                    app.close()
                    continue
                }
                sockets += app
                sockets += server
                val watcher = watchRequests()
                thread(isDaemon = true) { pipe(app.getInputStream(), server.getOutputStream(), watcher, app, server) }
                thread(isDaemon = true) { pipe(server.getInputStream(), app.getOutputStream(), null, app, server) }
            }
        }
    }

    /** Copies [input] to [output] until either side closes, showing each chunk to [watcher] first. */
    private fun pipe(input: InputStream, output: OutputStream, watcher: RequestWatcher?, vararg ends: Socket) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                watcher?.sent(buffer, read)
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
        sockets.forEach { it.close() }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
