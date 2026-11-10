package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import kotlin.coroutines.CoroutineContext

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

    /**
     * An API client of [server] that sends through [httpClient], the shared client by default. Its
     * calls run on [io], by default the calling thread, as the tests' unconfined scopes run their
     * coroutines, so a test sees each answer once the call that sent it returns.
     */
    fun client(
        server: ServerUrl = checkNotNull(stored) { "no stored server" },
        credentials: ApiCredentials = ApiCredentials.ApiToken("tok-123"),
        httpClient: OkHttpClient = http.instance,
        trustListener: TrustListener? = null,
        successListener: SuccessListener? = null,
        io: CoroutineContext = Dispatchers.Unconfined,
    ): TogglApiClient = TogglApiClient(server, credentials, httpClient, trustListener, successListener, io)

    /** The app's [TokklApi] over [client] with the same arguments, as the signed-in session builds it. */
    fun api(
        server: ServerUrl = checkNotNull(stored) { "no stored server" },
        credentials: ApiCredentials = ApiCredentials.ApiToken("tok-123"),
        httpClient: OkHttpClient = http.instance,
        trustListener: TrustListener? = null,
        successListener: SuccessListener? = null,
        io: CoroutineContext = Dispatchers.Unconfined,
    ): TokklApi = TokklApi(client(server, credentials, httpClient, trustListener, successListener, io))

    /** Opens each API over [httpClient], the shared client by default, as sign-in does. */
    fun connector(httpClient: OkHttpClient = http.instance): ApiConnector = apiConnector(httpClient)

    /** [trust] as it is at each call. */
    private inner class CurrentTrust : ServerTrust {
        override fun isCleartextConfirmed(server: ServerUrl): Boolean = trust.isCleartextConfirmed(server)

        override fun confirmCleartext(server: ServerUrl) = trust.confirmCleartext(server)

        override fun pinnedSha256(host: String, port: Int): String? = trust.pinnedSha256(host, port)

        override fun pinCertificate(certificate: ServerCertificate) = trust.pinCertificate(certificate)
    }
}
