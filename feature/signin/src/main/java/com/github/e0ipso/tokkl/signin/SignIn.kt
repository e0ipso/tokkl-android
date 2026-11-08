package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.navigation.SignInAgain
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.accountSettings
import okhttp3.OkHttpClient
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.coroutines.CoroutineContext

/** What the user entered to sign in. `toString` never prints a secret. */
sealed interface SignInCredentials {

    class ApiToken(val token: String) : SignInCredentials {
        override fun toString(): String = "ApiToken(***)"
    }

    /** Used for one request only. The password is never stored. */
    class EmailPassword(val email: String, val password: String) : SignInCredentials {
        override fun toString(): String = "EmailPassword(***)"
    }
}

/** How a sign-in attempt ended. None of these carries a credential. */
sealed interface SignInResult {

    /** The server, the token and the account settings are stored. */
    data object SignedIn : SignInResult

    /** 403: wrong API token, or wrong email or password. */
    data object BadCredentials : SignInResult

    /** 429: too many failed attempts. [retryAfterSeconds] is null without a usable `Retry-After`. */
    data class RateLimited(val retryAfterSeconds: Long?) : SignInResult

    /**
     * The TLS handshake failed because no system or user CA trusts the
     * server's [certificate], and the user has not pinned it. [changed] is
     * true when the user pinned another certificate for that server. No
     * request was sent, so no credential left the phone. The screen asks
     * whether to trust the certificate by its fingerprint.
     */
    data class UntrustedCertificate(val certificate: ServerCertificate, val changed: Boolean) : SignInResult

    /**
     * The server's certificate does not name [host], the host of the URL the
     * user entered, so the host name check refused it and nothing was sent.
     * The screen says how to fix the certificate and never offers to trust it.
     */
    data class CertificateHostMismatch(val host: String) : SignInResult

    /**
     * The server redirected to [origin] (`scheme://host:port`), and the app
     * talks only to the server the user entered.
     */
    data class HostNotAllowed(val origin: String) : SignInResult

    /**
     * [server] is a plain `http://` URL the user has not confirmed, so nothing
     * was sent. The screen warns that the token or password would travel
     * unencrypted, and the attempt goes ahead only once the user confirms.
     */
    data class CleartextNotConfirmed(val server: ServerUrl) : SignInResult

    /** The phone could not store the user's answer to a warning, so nothing was sent. */
    data object AnswerNotStored : SignInResult

    /** No response: wrong host, no route, refused connection, other TLS failure or timeout. */
    data object Unreachable : SignInResult

    /** Any other status outside 2xx, such as 404 for a URL that is not a Tokkl server. */
    data class UnexpectedStatus(val status: Int) : SignInResult

    /** A 2xx answer that is not a `GET /api/v9/me` user. */
    data object NotTokkl : SignInResult

    /** Email and password worked, but `GET /api/v9/me` carried no `api_token` to keep. */
    data object NoApiToken : SignInResult

    /** The server accepted the credentials, but the phone could not store the token. */
    data object StorageFailed : SignInResult

    /**
     * Signing in again ([SignInAgain]) went to another server or account than the signed-in one,
     * so nothing was stored. The changes queued for that account would go to the wrong one.
     */
    data object OtherAccount : SignInResult
}

/**
 * Stores the configured server, its API token and the account settings once the server has
 * accepted them.
 */
fun interface SessionWriter {
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(server: ServerUrl, token: String, settings: AccountSettings)
}

/**
 * One sign-in attempt: `GET /api/v9/me` with HTTP Basic auth, then the server,
 * the token and the account settings from that response go to [session]. With
 * email and password, the token kept is the `api_token` from that response;
 * the password is used for this one request and never written anywhere.
 *
 * The call blocks on the disk, so make it on the IO dispatcher.
 *
 * @param httpClient the shared HTTP client, whose guard lets the request through to [configured]'s
 *   sign-in candidate.
 * @param io where the request runs.
 */
class SignIn(
    private val session: SessionWriter,
    private val httpClient: OkHttpClient,
    private val configured: ConfiguredServer,
    private val io: CoroutineContext,
    private val signedInAccount: () -> SignInAgain?,
) {

    /**
     * With [again], a server other than the signed-in one gets no request, and an account other
     * than the signed-in one is not stored: both end in [SignInResult.OtherAccount].
     */
    suspend fun attempt(server: ServerUrl, credentials: SignInCredentials, again: SignInAgain? = null): SignInResult {
        val account = signedInAccount() ?: again
        if (account != null && server != account.server) return SignInResult.OtherAccount
        val api = TokklApi(TogglApiClient(server, credentials.toApiCredentials(), httpClient, io = io))
        // Until the server is stored, the URL being tried is the configured server.
        val result = configured.signingIn(server) { api.account.me(Me.serializer()) }
        val me = when (result) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result.error.toSignInResult()
        }
        val settings = me.accountSettings() ?: return SignInResult.NotTokkl
        if (account?.userId != null && me.id != account.userId) return SignInResult.OtherAccount
        val token = when (credentials) {
            is SignInCredentials.ApiToken -> credentials.token

            is SignInCredentials.EmailPassword -> me.apiToken?.takeIf { it.isNotEmpty() }
                ?: return SignInResult.NoApiToken
        }
        return try {
            session.save(server, token, settings)
            SignInResult.SignedIn
        } catch (_: GeneralSecurityException) {
            SignInResult.StorageFailed
        } catch (_: IOException) {
            SignInResult.StorageFailed
        }
    }
}

private fun SignInCredentials.toApiCredentials(): ApiCredentials = when (this) {
    is SignInCredentials.ApiToken -> ApiCredentials.ApiToken(token)
    is SignInCredentials.EmailPassword -> ApiCredentials.EmailPassword(email, password)
}

private fun ApiError.toSignInResult(): SignInResult = when (this) {
    ApiError.AuthFailed -> SignInResult.BadCredentials
    is ApiError.RateLimited -> SignInResult.RateLimited(retryAfterSeconds)
    is ApiError.UnexpectedStatus -> SignInResult.UnexpectedStatus(status)
    is ApiError.InvalidResponse -> SignInResult.NotTokkl
    is ApiError.HostNotAllowed -> SignInResult.HostNotAllowed(origin)
    is ApiError.CleartextNotConfirmed -> SignInResult.CleartextNotConfirmed(server)
    is ApiError.UntrustedCertificate -> SignInResult.UntrustedCertificate(certificate, changed)
    is ApiError.CertificateHostMismatch -> SignInResult.CertificateHostMismatch(host)
    is ApiError.Network -> SignInResult.Unreachable
}

/**
 * The account a sign-in would replace, read from the stores on each call: the stored server and
 * the stored settings' user. Sign-in rechecks it on every submit, including a form restored after
 * the process died, whose in-memory reauthentication marker may be gone. Reads the disk.
 */
internal class StoredSignInAccount(
    private val servers: ServerConfigStore,
    private val accountSettings: AccountSettingsStore,
) {
    fun current(): SignInAgain? = servers.load()?.let { server ->
        SignInAgain(
            server,
            accountSettings.load()?.userId,
        )
    }
}
