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
import kotlin.concurrent.thread

/**
 * A plain TCP relay from the phone's 127.0.0.1 to [server], which a test can make hang. While it
 * hangs, it accepts connections and answers nothing, as a network that has gone quiet does, and it
 * cuts the connections it was relaying, so a pooled connection cannot slip past. [release] drops
 * the hung connections, which the app sees as a network failure, and relays again.
 */
class HangingRelay(server: ServerUrl) : Closeable {

    private val target = server.api().let { it.host to it.port }
    private val listener = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val relayed = CopyOnWriteArrayList<Socket>()
    private val hung = CopyOnWriteArrayList<Socket>()

    @Volatile
    private var hanging = false

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
        thread(isDaemon = true) { pipe(app.getInputStream(), server.getOutputStream(), app, server) }
        thread(isDaemon = true) { pipe(server.getInputStream(), app.getOutputStream(), app, server) }
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
        relayed.forEach { it.close() }
        hung.forEach { it.close() }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
