package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Counts the requests the app sends through a [LoopbackRelay] to [server] whose request line ends
 * in [request], such as `/search/time_entries HTTP/1.1`. No other request or body may hold
 * [request], so counting it in the stream needs no HTTP parsing. The app's requests pass through
 * once it uses [url] as its server.
 */
class RequestCounter(server: ServerUrl, request: String) : Closeable {

    private val needle = request.toByteArray(Charsets.US_ASCII)
    private val total = AtomicInteger()
    private val relay = server.toString().toHttpUrl().let { LoopbackRelay(it.host, it.port) { OnOneConnection() } }

    /** Connections the relay could not pass on, which would make [count] wrong. */
    val failures: List<String> get() = relay.failures

    val url: ServerUrl = ServerUrl.parse("http://127.0.0.1:${relay.localPort}") ?: error("not a URL")

    /** The requests counted so far. */
    val count: Int get() = total.get()

    override fun close() = relay.close()

    /** Counts [request] in one connection's requests, once even when it is split across two chunks. */
    private inner class OnOneConnection : LoopbackRelay.ConnectionWatcher {
        private var tail = ByteArray(0)

        override fun sent(bytes: ByteArray, count: Int) {
            val seen = tail + bytes.copyOf(count)
            total.addAndGet(occurrences(seen))
            tail = seen.copyOfRange(maxOf(0, seen.size - needle.size + 1), seen.size)
        }

        private fun occurrences(bytes: ByteArray): Int =
            (0..bytes.size - needle.size).count { start -> needle.indices.all { bytes[start + it] == needle[it] } }
    }
}
