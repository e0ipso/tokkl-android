package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Puts the phone in airplane mode, or takes it out, through the shell user, the only user who may
 * switch it, and waits until requests to [client]'s server fail for want of a network, or get
 * through again. `cmd connectivity airplane-mode` needs Android 10 or later.
 *
 * The wait asks the server with a plain HTTP client of its own. The app's client sends only to
 * the stored server, so after a sign-out, or with a relay stored as the server, its refusal would
 * read as an answer and the wait would end before the network did.
 */
object AirplaneMode {

    fun set(on: Boolean, client: TogglApiClient) = set(on, client.server)

    /** As [set] for a client, waiting on requests to [server]. */
    fun set(on: Boolean, server: ServerUrl) {
        DeviceShell.run("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (serverAnswers(server) == on) {
            if (System.nanoTime() > deadline) throw AssertionError("the network did not ${if (on) "go" else "come back"}")
            Thread.sleep(POLL_MS)
        }
    }

    /** Takes the phone out of airplane mode without waiting, for a test's clean-up. */
    fun off() {
        DeviceShell.run("cmd connectivity airplane-mode disable")
    }

    /** Any HTTP answer, or a TLS handshake this client does not trust, means the server is reachable. */
    private fun serverAnswers(server: ServerUrl): Boolean = try {
        probe.newCall(Request.Builder().url(server.api("me")).build()).execute().close()
        true
    } catch (_: SSLException) {
        true
    } catch (_: IOException) {
        false
    }

    private val probe = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        // A new connection each time: one kept from before the switch proves nothing.
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .build()

    private const val TIMEOUT_NANOS = 30_000_000_000L
    private const val POLL_MS = 200L
}
