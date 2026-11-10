package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.api
import com.github.e0ipso.tokkl.api.instance
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

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
            if (System.nanoTime() > deadline) {
                throw AssertionError(
                    "the network did not ${if (on) "go" else "come back"}",
                )
            }
            Thread.sleep(POLL_MS)
        }
        if (on) awaitNoIdleAppConnection(deadline)
    }

    /**
     * Waits until the app's client keeps no idle connection. The probe fails as soon as the network
     * goes, but the app evicts its kept connections only when its own network callback runs, or
     * after they idle for 4 s. A request sent on a kept connection before then is written to a
     * socket the network no longer reaches, and can fail only at the 10 s read timeout, too late for
     * a test that waits 10 s for "Can't reach the server". Without a kept connection the request
     * fails before it leaves the phone, as it does for a user who goes offline and taps a moment
     * later.
     */
    private fun awaitNoIdleAppConnection(deadline: Long) {
        val pool = appHttp.instance.connectionPool
        while (pool.idleConnectionCount() > 0) {
            if (System.nanoTime() > deadline) {
                throw AssertionError("the app kept ${pool.idleConnectionCount()} idle connections offline")
            }
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
