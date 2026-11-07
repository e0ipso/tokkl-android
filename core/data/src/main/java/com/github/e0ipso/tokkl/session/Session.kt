package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SuccessListener
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
 * @param successListener hears every 2xx answer to [client]'s requests.
 */
class Session(
    private val servers: ServerConfigStore,
    private val tokens: ApiTokenStore,
    private val trustListener: TrustListener? = null,
    private val successListener: SuccessListener? = null,
    private val clearExports: () -> Unit = {},
) : ApiClientProvider,
    AccountDataOwner {

    override val signOutOrder = SignOutOrder.SESSION

    override suspend fun forgetAccount() = clear()

    /**
     * Signed in means a server is stored and the token decrypts. A Keystore or
     * disk failure counts as signed out, so the user can sign in again.
     */
    fun isSignedIn(): Boolean = try {
        servers.load() != null && tokens.load() != null
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: IOException) {
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
            TogglApiClient(
                server,
                ApiCredentials.ApiToken(token),
                trustListener = trustListener,
                successListener = successListener,
            )
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override fun invoke(): TogglApiClient? = client()

    /**
     * Stores [server] as the configured server and [token] as its API token.
     * The server is cleared first and written last, so a failure part way
     * leaves the app signed out instead of pairing a token with another server.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(server: ServerUrl, token: String) {
        exports.change {
            servers.clear()
            clearExports()
            tokens.save(token)
            servers.save(server)
        }
    }

    /**
     * Forgets the server, then the token and its Keystore key. Without the server the app counts
     * as signed out and sends nothing, so that goes first: a failure after it still signs out.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun clear() {
        exports.change {
            try {
                servers.clear()
                tokens.clear()
            } finally {
                clearExports()
            }
        }
    }

    companion object {
        /** Shared by every Session instance, so a report export never outlives the account it was made for. */
        val exports = ExportAccount()
    }
}
