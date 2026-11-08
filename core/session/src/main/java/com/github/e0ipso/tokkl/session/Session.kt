package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.storage.ClearableStore
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.coroutines.CoroutineContext

/**
 * The account the app is signed in to: the configured server and its API token.
 * The token goes only through [ApiTokenStore]; no password is ever stored.
 *
 * The first [api] or [isSignedIn] reads the stores, with a Keystore decrypt, and keeps the
 * signed-in API in memory, so later calls touch neither the disk nor the Keystore. [save] and
 * [clear] drop what was kept, so the next call reads the stores again. A read that fails on the
 * disk or the Keystore keeps nothing, and the next call tries again. Make the first call off the
 * main thread.
 *
 * @param servers the configured server, a [ServerConfigStore] in the app.
 * @param tokens the API token, an [ApiTokenStore] in the app.
 * @param http the shared HTTP client, which every [api] sends through.
 * @param io where every [api] runs its calls.
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

        val api: TokklApi? by lazy {
            if (server == null || token == null) return@lazy null
            TokklApi(
                TogglApiClient(
                    server,
                    ApiCredentials.ApiToken(token),
                    http.instance,
                    trustListener = trustListener,
                    successListener = successListener,
                    io = io,
                ),
            )
        }
    }

    /**
     * Guards [generation]. A JVM lock, not a Mutex, because api() is read from any thread
     * without suspending.
     */
    private val lock = Any()

    /** Counts the changes of account, so a read that overlaps one keeps nothing. */
    private var generation = 0L

    /** The last read, or null before the first and after each change of account. */
    @Volatile
    private var kept: Read? = null

    /**
     * Signed in means a server is stored and the token decrypts. A Keystore or
     * disk failure counts as signed out, so the user can sign in again.
     */
    fun isSignedIn(): Boolean = read()?.signedIn == true

    /**
     * The API of the signed-in account, or null when signed out. A Keystore or disk failure
     * counts as signed out, as in [isSignedIn].
     */
    fun api(): TokklApi? = read()?.api

    override fun invoke(): TokklApi? = api()

    /** The kept read, or a new one, which is kept unless the account changed meanwhile. Null on a failure. */
    private fun read(): Read? {
        kept?.let { return it }
        val before = synchronized(lock) { generation }
        val read = try {
            val server = servers.load()
            Read(server, server?.let { tokens.load() })
        } catch (_: GeneralSecurityException) {
            return null
        } catch (_: IOException) {
            return null
        }
        synchronized(lock) { if (generation == before) kept = read }
        return read
    }

    /** Drops the kept API, and any read that started before this call. */
    private fun forgetKept() {
        synchronized(lock) {
            generation++
            kept = null
        }
    }

    /**
     * Stores [server] as the configured server and [token] as its API token.
     * The server is cleared first and written last, so a failure part way
     * leaves the app signed out instead of pairing a token with another server.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(server: ServerUrl, token: String) {
        forgetKept()
        try {
            servers.clear()
            tokens.save(token)
            servers.save(server)
        } finally {
            forgetKept()
        }
    }

    /**
     * Forgets the server, then the token and its Keystore key. Without the server the app counts
     * as signed out and sends nothing, so that goes first: a failure after it still signs out.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun clear() {
        forgetKept()
        try {
            servers.clear()
            tokens.clear()
        } finally {
            forgetKept()
        }
    }
}
