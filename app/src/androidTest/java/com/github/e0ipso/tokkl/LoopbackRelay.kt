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
 */
class LoopbackRelay(private val host: String, private val port: Int) : Closeable {

    private val listener = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val sockets = CopyOnWriteArrayList<Socket>()

    /** The port the relay listens on. */
    val localPort: Int = listener.localPort

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
                    app.close()
                    continue
                }
                sockets += app
                sockets += server
                thread(isDaemon = true) { pipe(app.getInputStream(), server.getOutputStream(), app, server) }
                thread(isDaemon = true) { pipe(server.getInputStream(), app.getOutputStream(), app, server) }
            }
        }
    }

    private fun pipe(input: InputStream, output: OutputStream, vararg ends: Socket) {
        try {
            input.copyTo(output, BUFFER_SIZE)
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
