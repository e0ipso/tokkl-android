package com.github.e0ipso.tokkl.api

import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

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
            // The guard follows same-origin redirects itself.
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(tls.socketFactory, trustManager)
            .addInterceptor(ConfiguredServerGuard(configured = { ConfiguredServer.current }, trust = { ConfiguredTrust.current }))
            .build()
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

/** JSON settings for both APIs. Models declare only the fields the app reads. */
internal val ApiJson: Json = Json { ignoreUnknownKeys = true }
