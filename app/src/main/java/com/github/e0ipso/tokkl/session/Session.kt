package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * The account the app is signed in to: the configured server and its API token.
 * The token goes only through [ApiTokenStore]; no password is ever stored.
 *
 * Calls do disk and Keystore I/O, so make them off the main thread.
 *
 * @param trustListener hears every request of [client] that the trust checks
 *   refuse, so the app can prompt from any screen.
 */
class Session(
    private val servers: ServerConfigStore,
    private val tokens: ApiTokenStore,
    private val trustListener: TrustListener? = null,
) {

    /** Reports trust refusals to the app's [TokklApplication.sessionTrust]. */
    constructor(context: Context) : this(
        ServerConfigStore(context),
        ApiTokenStore(context),
        (context.applicationContext as? TokklApplication)?.sessionTrust,
    )

    /**
     * Signed in means a server is stored and the token decrypts. A Keystore or
     * disk failure counts as signed out, so the user can sign in again.
     */
    fun isSignedIn(): Boolean =
        try {
            servers.load() != null && tokens.load() != null
        } catch (e: GeneralSecurityException) {
            false
        } catch (e: IOException) {
            false
        }

    /**
     * The API client of the signed-in account, or null when signed out. A Keystore or disk
     * failure counts as signed out, as in [isSignedIn].
     */
    fun client(): TogglApiClient? {
        return try {
            val server = servers.load() ?: return null
            val token = tokens.load() ?: return null
            TogglApiClient(server, ApiCredentials.ApiToken(token), trustListener = trustListener)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Stores [server] as the configured server and [token] as its API token.
     * The server is cleared first and written last, so a failure part way
     * leaves the app signed out instead of pairing a token with another server.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(server: ServerUrl, token: String) {
        servers.clear()
        tokens.save(token)
        servers.save(server)
    }

    /**
     * Forgets the server, then the token and its Keystore key. Without the server the app counts
     * as signed out and sends nothing, so that goes first: a failure after it still signs out.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun clear() {
        servers.clear()
        tokens.clear()
    }
}
