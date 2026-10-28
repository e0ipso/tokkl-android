package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Counts the requests the app sends through a [LoopbackRelay] to [server] whose request line ends
 * in [request], such as `/search/time_entries HTTP/1.1`. No other request or body may hold
 * [request], so counting it in the stream needs no HTTP parsing. The app's requests pass through
 * once it uses [url] as its server.
 */
class RequestCounter(server: ServerUrl, private val request: String) : Closeable {

    private val needle = request.toByteArray(Charsets.US_ASCII)
    private val total = AtomicInteger()
    private val connections = AtomicInteger()
    private val requestLines = CopyOnWriteArrayList<String>()
    private val relay = server.toString().toHttpUrl().let { LoopbackRelay(it.host, it.port) { OnOneConnection() } }

    /** Connections the relay could not pass on, which would make [count] wrong. */
    val failures: List<String> get() = relay.failures

    val url: ServerUrl = ServerUrl.parse("http://127.0.0.1:${relay.localPort}") ?: error("not a URL")

    /** The requests counted so far. */
    val count: Int get() = total.get()

    /**
     * The request lines that hold [request], such as `POST /api/app/timer/pause HTTP/1.1`, in the
     * order the relay saw them, so a test can say which requests it counted.
     */
    val lines: List<String> get() = requestLines.toList()

    override fun close() = relay.close()

    /** Counts [request] in one connection's requests, once even when it is split across two chunks. */
    private inner class OnOneConnection : LoopbackRelay.ConnectionWatcher {
        private val connection = connections.incrementAndGet()
        private var tail = ByteArray(0)

        /** This connection's text since its last line break. */
        private val line = StringBuilder()

        init {
            BlackholeDiagnostics.record("counter.connection listen_port=${url.toString().toHttpUrl().port} connection=$connection")
        }

        override fun sent(bytes: ByteArray, count: Int) {
            val seen = tail + bytes.copyOf(count)
            val matches = occurrences(seen)
            val cumulative = total.addAndGet(matches)
            if (matches > 0) {
                BlackholeDiagnostics.record(
                    "counter.match listen_port=${url.toString().toHttpUrl().port} connection=$connection matches=$matches cumulative=$cumulative",
                )
            }
            tail = seen.copyOfRange(maxOf(0, seen.size - needle.size + 1), seen.size)
            keepRequestLines(bytes, count)
        }

        /** Records each request line holding [request]. A body that runs into the next request line stays out. */
        private fun keepRequestLines(bytes: ByteArray, count: Int) {
            for (index in 0 until count) {
                val char = (bytes[index].toInt() and 0xff).toChar()
                if (char != '\n') {
                    line.append(char)
                    continue
                }
                val text = line.toString().trimEnd('\r')
                line.setLength(0)
                if (text.contains(request)) REQUEST_LINE.find(text)?.let { requestLines += it.value }
            }
        }

        private fun occurrences(bytes: ByteArray): Int =
            (0..bytes.size - needle.size).count { start -> needle.indices.all { bytes[start + it] == needle[it] } }
    }

    private companion object {
        val REQUEST_LINE = Regex("""(GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS) \S+ HTTP/1\.1$""")
    }
}
