package com.github.e0ipso.tokkl.api

import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext

/**
 * Opens the API of a server with credentials over the app's one HTTP client, as sign-in does for
 * the server it tries. [SharedHttpClient] implements it.
 */
fun interface ApiConnector {
    fun api(server: ServerUrl, credentials: ApiCredentials, io: CoroutineContext): TokklApi
}

/**
 * The app's only [OkHttpClient]. Every request goes through it, or through
 * a client [sendingOnce] derives from it, so its connection pool, dispatcher
 * and security settings apply everywhere. SingleHttpClientTest fails when
 * other app code builds an HTTP client.
 *
 * The app makes one, so every request shares one connection pool and one
 * dispatcher.
 *
 * Every request and redirect must stay on [server]'s current server, and a
 * plain `http://` server needs the user's confirmation in [trust]; see
 * [ConfiguredServerGuard]. TLS goes through [PinningTrustManager]: the
 * platform's CAs first, then the certificate the user pinned. OkHttp's own
 * host name check stays on. Later security settings go into [build], never
 * into a second client.
 *
 * A request tagged [StreamingRead] reads its body with no read timeout, for
 * an event stream that waits between events by design. Every other setting
 * stays as it is for it.
 */
class SharedHttpClient(private val server: ConfiguredServer, private val trust: ServerTrust) : ApiConnector {

    private val built = lazy { build() }

    /** The client. Built on first use, which loads the platform's CAs, so first use it off the main thread. */
    internal val client: OkHttpClient by built

    override fun api(server: ServerUrl, credentials: ApiCredentials, io: CoroutineContext): TokklApi =
        api(server, credentials, io, trustListener = null, successListener = null)

    /**
     * The API of [server] with [credentials], sending through this client, whose calls run on
     * [io]. [trustListener] and [successListener] hear how its requests fare with the trust
     * checks and which get an answer. The first call builds the client: make it off the main
     * thread.
     */
    fun api(
        server: ServerUrl,
        credentials: ApiCredentials,
        io: CoroutineContext,
        trustListener: TrustListener?,
        successListener: SuccessListener?,
    ): TokklApi = TokklApi(ApiTransport(server, credentials, client, trustListener, successListener, io))

    /**
     * Cancels every call in flight. Signing out calls this once it has forgotten the server, so no
     * answer for the old account arrives after the phone has cleared its data. Before the client
     * is built no call is in flight, so it does nothing and builds nothing.
     */
    fun cancelAll() {
        if (built.isInitialized()) client.dispatcher.cancelAll()
    }

    /**
     * Closes every idle pooled connection. Call it when the network drops: a request on a
     * connection kept from the old network would fail only after it was written. Before the
     * client is built no connection is pooled, so it does nothing and builds nothing: it never
     * loads the platform's CAs, and the main thread can call it.
     */
    fun dropIdleConnections() {
        if (built.isInitialized()) client.connectionPool.evictAll()
    }

    private fun build(): OkHttpClient {
        val trustManager = PinningTrustManager(platformTrustManager(), trust::pinnedSha256)
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, IDLE_CONNECTION_SECONDS, TimeUnit.SECONDS))
            // The guard follows same-origin redirects itself.
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(tls.socketFactory, trustManager)
            .addInterceptor(StreamingReads)
            .addInterceptor(ConfiguredServerGuard(server, trust))
            .build()
    }

    companion object {
        /**
         * [client] made to send each request once. OkHttp sends a request again on a new connection
         * when one fails, also after the server has answered, so a request whose answer was lost
         * would reach the server twice and its caller would read only the second answer. The derived
         * client fails the call instead, and leaves recovery to the caller, also from a stale pooled
         * connection. It shares the rest with [client]: connection pool, dispatcher, interceptors and
         * trust checks, so the guard, the pinning and [cancelAll] cover it as they cover [client].
         */
        internal fun sendingOnce(client: OkHttpClient): OkHttpClient =
            client.newBuilder().retryOnConnectionFailure(false).build()

        /** OkHttp's default. */
        private const val MAX_IDLE_CONNECTIONS = 5

        /**
         * How long an idle connection stays in the pool. Tokkl runs on Node, which closes a keep-alive
         * connection after 5 idle seconds, and OkHttp reuses one idle for less than 10 seconds without
         * checking it. A request [sendingOnce] sent on such a connection failed, so a timer action a
         * few seconds after the last request said there was no connection. Under 5 seconds the pool
         * drops the connection first, and the request opens a new one.
         */
        private const val IDLE_CONNECTION_SECONDS = 4L
    }

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

/**
 * JSON settings for both APIs. Models declare only the fields the app reads. Code outside this
 * module reads and writes the API's types with it as text, as the timer state's store and stream
 * do.
 */
val ApiJson: StringFormat get() = Wire

/** [ApiJson] as JSON, for the trees this module builds and reads. */
internal val Wire: Json = Json { ignoreUnknownKeys = true }

/**
 * HTTP Basic credentials for both APIs. Tokkl decodes the header as UTF-8 and
 * splits it at the first colon, so a password may contain colons and an email
 * may not. `toString` never prints a secret.
 */
sealed class ApiCredentials {

    /** The value of the `Authorization` header. */
    abstract val authorizationHeader: String

    /** Sends `<api_token>:api_token`. */
    class ApiToken(private val token: String) : ApiCredentials() {
        override val authorizationHeader: String
            get() = Credentials.basic(token, "api_token", Charsets.UTF_8)

        override fun toString(): String = "ApiToken(***)"
    }

    /** Sends `<email>:<password>`. */
    class EmailPassword(private val email: String, private val password: String) : ApiCredentials() {
        init {
            require(':' !in email) { "An email used for Basic auth cannot contain ':'" }
        }

        override val authorizationHeader: String
            get() = Credentials.basic(email, password, Charsets.UTF_8)

        override fun toString(): String = "EmailPassword(***)"
    }
}

/**
 * What the user has agreed to for a server: sending to a plain `http://` URL,
 * and trusting a self-signed certificate by its fingerprint. [SharedHttpClient]
 * checks it on every request and every TLS handshake, so no screen or
 * background job can skip the question.
 */
interface ServerTrust {

    /**
     * The SHA-256 of the certificate the user trusted for the TLS server at [host] and [port], as
     * lowercase hex, or null when there is none. [PinningTrustManager] reads it.
     */
    fun pinnedSha256(host: String, port: Int): String?

    /**
     * Whether the user confirmed that requests to [server], a plain `http://`
     * URL, may carry the token or password unencrypted.
     */
    fun isCleartextConfirmed(server: ServerUrl): Boolean

    /** Records the user's confirmation for [server]. Does disk I/O. */
    @Throws(IOException::class)
    fun confirmCleartext(server: ServerUrl)

    /** Trusts [certificate], and no other, for its host and port. Does disk I/O. */
    @Throws(IOException::class)
    fun pinCertificate(certificate: ServerCertificate)
}
