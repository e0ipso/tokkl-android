package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.coroutines.CoroutineContext

/**
 * The account the app is signed in to: the configured server and its API token.
 * The token goes only through [ApiTokenStore]; no password is ever stored.
 *
 * The first [api] or [isSignedIn] reads the stores on [io], with a Keystore decrypt, and keeps the
 * signed-in API in memory, so later calls touch neither the disk nor the Keystore. The first [api]
 * also builds the API's client on [io], so every call is safe on the main thread. [save] and
 * [clear] drop what was kept, so the next call reads the stores again. A read that fails on the
 * disk or the Keystore keeps nothing, and the next call tries again. Reads, saves and clears take
 * turns, so a read never keeps what a change of account replaced.
 *
 * @param servers the configured server, a [ServerConfigStore] in the app.
 * @param tokens the API token, an [ApiTokenStore] in the app.
 * @param http the shared HTTP client, which every [api] sends through.
 * @param io where the stores are read and written, and where every [api] runs its calls.
 * @param trustListener hears every request of [api] that the trust checks
 *   refuse, so the app can prompt from any screen.
 * @param successListener hears every 2xx answer to [api]'s requests.
 */
class Session(
    private val servers: ClearableStore<ServerUrl>,
    private val tokens: ClearableStore<String>,
    private val http: SharedHttpClient,
    private val io: CoroutineContext,
    private val trustListener: TrustListener?,
    private val successListener: SuccessListener?,
) : ApiClientProvider,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.FORGET_SESSION

    override suspend fun forgetAccount() = clear()

    /**
     * What the last read of the stores found: the stored server and token, or null when signed
     * out. The API comes on first use, so [isSignedIn] does not build the HTTP client.
     */
    private inner class Read(private val server: ServerUrl?, private val token: String?) {
        val signedIn: Boolean get() = server != null && token != null

        private val client = lazy {
            if (server == null || token == null) return@lazy null
            http.api(server, ApiCredentials.ApiToken(token), io, trustListener, successListener)
        }

        /**
         * The signed-in API, built on [io] on first use, since the shared HTTP client may load the
         * platform's certificates then.
         */
        suspend fun api(): TokklApi? = if (client.isInitialized()) client.value else withContext(io) { client.value }
    }

    /** The last read, or null before the first and after each change of account. */
    private val kept = MutableStateFlow<Read?>(null)

    /** Lets one read or change of the stores run at a time. */
    private val stores = Mutex()

    /**
     * Signed in means a server is stored and the token decrypts. A Keystore or
     * disk failure counts as signed out, so the user can sign in again.
     */
    suspend fun isSignedIn(): Boolean = read()?.signedIn == true

    /**
     * The API of the signed-in account, or null when signed out. A Keystore or disk failure
     * counts as signed out, as in [isSignedIn].
     */
    suspend fun api(): TokklApi? = read()?.api()

    override suspend fun invoke(): TokklApi? = api()

    /** The kept read, or a new one, which is kept. Null on a failure. */
    private suspend fun read(): Read? = kept.value ?: stores.withLock {
        kept.value ?: withContext(io) {
            try {
                val server = servers.load()
                Read(server, server?.let { tokens.load() })
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IOException) {
                null
            }
        }?.also { kept.value = it }
    }

    /**
     * Stores [server] as the configured server and [token] as its API token.
     * The server is cleared first and written last, so a failure part way
     * leaves the app signed out instead of pairing a token with another server.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    suspend fun save(server: ServerUrl, token: String) = change {
        servers.clear()
        tokens.save(token)
        servers.save(server)
    }

    /**
     * Forgets the server, then the token and its Keystore key. Without the server the app counts
     * as signed out and sends nothing, so that goes first: a failure after it still signs out.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    suspend fun clear() = change {
        servers.clear()
        tokens.clear()
    }

    /** Runs [write] on [io] with the kept read dropped before and after it. */
    private suspend fun change(write: suspend () -> Unit) = stores.withLock {
        kept.value = null
        try {
            withContext(io) { write() }
        } finally {
            kept.value = null
        }
    }
}

/**
 * [call] with the signed-in account's API, as the repositories over the server use it, or
 * [ApiError.AuthFailed] while signed out, before any request.
 */
suspend inline fun <R> ApiClientProvider.signedIn(call: (TokklApi) -> ApiResult<R>): ApiResult<R> {
    val api = this() ?: return ApiResult.Failure(ApiError.AuthFailed)
    return call(api)
}
