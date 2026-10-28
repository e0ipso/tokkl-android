package com.github.e0ipso.tokkl.api

import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * The app's only [OkHttpClient]. Every request goes through it, or through
 * a client [sendingOnce] derives from it, so its connection pool, dispatcher
 * and security settings apply everywhere. SingleHttpClientTest fails when
 * other app code builds an HTTP client.
 *
 * Every request and redirect must stay on [ConfiguredServer.current], and a
 * plain `http://` server needs the user's confirmation in
 * [ConfiguredTrust.current]; see [ConfiguredServerGuard]. TLS goes through
 * [PinningTrustManager]: the platform's CAs first, then the certificate the
 * user pinned. OkHttp's own host name check stays on. Later security
 * settings go into [build], never into a second client.
 *
 * A request tagged [StreamingRead] reads its body with no read timeout, for
 * an event stream that waits between events by design. Every other setting
 * stays as it is for it.
 */
object SharedHttpClient {

    val instance: OkHttpClient by lazy { build() }

    /**
     * Cancels every call in flight. Signing out calls this once it has forgotten the server, so no
     * answer for the old account arrives after the phone has cleared its data.
     */
    fun cancelAll() {
        instance.dispatcher.cancelAll()
    }

    /**
     * [client] made to send each request once. OkHttp sends a request again on a new connection
     * when one fails, also after the server has answered, so a request whose answer was lost
     * would reach the server twice and its caller would read only the second answer. The derived
     * client fails the call instead, and leaves recovery to the caller, also from a stale pooled
     * connection. It shares the rest with [client]: connection pool, dispatcher, interceptors and
     * trust checks, so the guard, the pinning and [cancelAll] cover it as they cover [client].
     */
    fun sendingOnce(client: OkHttpClient): OkHttpClient =
        client.newBuilder().retryOnConnectionFailure(false).build()

    private fun build(): OkHttpClient {
        val trustManager = PinningTrustManager(platformTrustManager()) { host, port ->
            ConfiguredTrust.current.pinnedSha256(host, port)
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, IDLE_CONNECTION_SECONDS, TimeUnit.SECONDS))
            // The guard follows same-origin redirects itself.
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(tls.socketFactory, trustManager)
            .addInterceptor(StreamingReads)
            .addInterceptor(ConfiguredServerGuard(configured = { ConfiguredServer.current }, trust = { ConfiguredTrust.current }))
            .build()
    }

    /** OkHttp's default. */
    private const val MAX_IDLE_CONNECTIONS = 5

    /**
     * How long an idle connection stays in the pool. Tokkl runs on Node, which closes a keep-alive
     * connection after 5 idle seconds, and OkHttp reuses one idle for less than 10 seconds without
     * checking it. A request [sendingOnce] sent on such a connection failed, so a timer action a few
     * seconds after the last request said there was no connection. Under 5 seconds the pool drops
     * the connection first, and the request opens a new one.
     */
    private const val IDLE_CONNECTION_SECONDS = 4L

    /** Lifts the read timeout, and only that, for a request tagged [StreamingRead]. */
    private object StreamingReads : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.tag(StreamingRead::class.java) == null) return chain.proceed(request)
            return chain.withReadTimeout(0, TimeUnit.MILLISECONDS).proceed(request)
        }
    }

    /**
     * The platform's default trust manager. On Android it trusts the system
     * and user CAs that res/xml/network_security_config.xml names.
     */
    private fun platformTrustManager(): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .single()
}

/**
 * Tags a request whose response body stays open and waits between events, as
 * `GET /api/app/timer/events` does, so [SharedHttpClient] reads it with no read
 * timeout. Its caller must watch for silence and cancel the call itself.
 */
internal object StreamingRead

/** JSON settings for both APIs. Models declare only the fields the app reads. */
internal val ApiJson: Json = Json { ignoreUnknownKeys = true }
