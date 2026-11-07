package com.github.e0ipso.tokkl.api

import okhttp3.OkHttpClient

/**
 * The shared HTTP client as the app builds it, for tests. Its configured server is [stored] and
 * its trust decisions are [trust], and a test may change either at any time. Make one per test,
 * so tests share no client, server or trust.
 */
class TestApi(stored: ServerUrl?, trust: ServerTrust = InMemoryServerTrust(*listOfNotNull(stored).toTypedArray())) {
    /** The server the app has stored. Null refuses every request. */
    @Volatile
    var stored: ServerUrl? = stored

    /** The trust decisions the shared client reads on each request and handshake. */
    @Volatile
    var trust: ServerTrust = trust

    val configured: ConfiguredServer = ConfiguredServer { this.stored }

    val http: SharedHttpClient = SharedHttpClient(configured, CurrentTrust())

    /** An API client of [server] that sends through [httpClient], the shared client by default. */
    fun client(
        server: ServerUrl = checkNotNull(stored) { "no stored server" },
        credentials: ApiCredentials = ApiCredentials.ApiToken("tok-123"),
        httpClient: OkHttpClient = http.instance,
        trustListener: TrustListener? = null,
        successListener: SuccessListener? = null,
    ): TogglApiClient = TogglApiClient(server, credentials, httpClient, trustListener, successListener)

    /** [trust] as it is at each call. */
    private inner class CurrentTrust : ServerTrust {
        override fun isCleartextConfirmed(server: ServerUrl): Boolean = trust.isCleartextConfirmed(server)

        override fun confirmCleartext(server: ServerUrl) = trust.confirmCleartext(server)

        override fun pinnedSha256(host: String, port: Int): String? = trust.pinnedSha256(host, port)

        override fun pinCertificate(certificate: ServerCertificate) = trust.pinCertificate(certificate)
    }
}
