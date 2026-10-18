package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

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

    /** The server and the token are stored. */
    data object SignedIn : SignInResult

    /** 403: wrong API token, or wrong email or password. */
    data object BadCredentials : SignInResult

    /** 429: too many failed attempts. [retryAfterSeconds] is null without a usable `Retry-After`. */
    data class RateLimited(val retryAfterSeconds: Long?) : SignInResult

    /**
     * The TLS handshake failed because no system or user CA trusts the
     * server's certificate. No request was sent, so no credential left the
     * phone. TKA-0021 turns this into the prompt to trust a self-signed
     * certificate by its fingerprint.
     */
    data object UntrustedCertificate : SignInResult

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
}

/** Stores the configured server and its API token once the server has accepted them. */
fun interface SessionWriter {
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(server: ServerUrl, token: String)
}

/**
 * One sign-in attempt: `GET /api/v9/me` with HTTP Basic auth, then the server
 * and token go to [session]. With email and password, the token kept is the
 * `api_token` from that response; the password is used for this one request
 * and never written anywhere.
 *
 * The call blocks, so make it off the main thread.
 */
class SignIn(
    private val session: SessionWriter,
    private val httpClient: OkHttpClient = SharedHttpClient.instance,
) {

    fun attempt(server: ServerUrl, credentials: SignInCredentials): SignInResult {
        val client = TogglApiClient(server, credentials.toApiCredentials(), httpClient)
        val me = when (val result = client.get(server.api("me"), Me.serializer())) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result.error.toSignInResult()
        }
        val token = when (credentials) {
            is SignInCredentials.ApiToken -> credentials.token
            is SignInCredentials.EmailPassword -> me.apiToken?.takeIf { it.isNotEmpty() } ?: return SignInResult.NoApiToken
        }
        return try {
            session.save(server, token)
            SignInResult.SignedIn
        } catch (e: GeneralSecurityException) {
            SignInResult.StorageFailed
        } catch (e: IOException) {
            SignInResult.StorageFailed
        }
    }

    /** The fields sign-in reads from `GET /api/v9/me`. `toString` hides the token. */
    @Serializable
    private class Me(
        val id: Long,
        @SerialName("api_token") val apiToken: String? = null,
    ) {
        override fun toString(): String = "Me(id=$id)"
    }
}

private fun SignInCredentials.toApiCredentials(): ApiCredentials =
    when (this) {
        is SignInCredentials.ApiToken -> ApiCredentials.ApiToken(token)
        is SignInCredentials.EmailPassword -> ApiCredentials.EmailPassword(email, password)
    }

private fun ApiError.toSignInResult(): SignInResult =
    when (this) {
        ApiError.AuthFailed -> SignInResult.BadCredentials
        is ApiError.RateLimited -> SignInResult.RateLimited(retryAfterSeconds)
        is ApiError.UnexpectedStatus -> SignInResult.UnexpectedStatus(status)
        is ApiError.InvalidResponse -> SignInResult.NotTokkl
        is ApiError.Network -> if (cause.isUntrustedCertificate()) SignInResult.UntrustedCertificate else SignInResult.Unreachable
    }

/**
 * Android reports a chain no trusted CA signed as an [SSLHandshakeException]
 * caused by a [CertificateException] or [CertPathValidatorException]
 * ("Trust anchor for certification path not found").
 */
internal fun IOException.isUntrustedCertificate(): Boolean =
    this is SSLHandshakeException &&
        generateSequence(cause) { it.cause }.take(MAX_CAUSES).any { it is CertificateException || it is CertPathValidatorException }

/** Enough for Android's TLS errors, and an end to a cycle of causes. */
private const val MAX_CAUSES = 8
