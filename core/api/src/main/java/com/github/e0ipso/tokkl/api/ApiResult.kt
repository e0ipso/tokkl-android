package com.github.e0ipso.tokkl.api

import java.io.IOException

/** The outcome of one API call. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Why an API call failed. */
sealed interface ApiError {

    /** The server answered with a status outside 2xx. */
    sealed interface Http : ApiError {
        val status: Int
    }

    /** 403: the server rejected the API token or the email and password. */
    data object AuthFailed : Http {
        override val status: Int get() = 403
    }

    /**
     * 429: too many requests, such as too many failed password attempts on
     * Tokkl. [retryAfterSeconds] is null when `Retry-After` is missing or not
     * a whole number of seconds.
     */
    data class RateLimited(val retryAfterSeconds: Long?) : Http {
        override val status: Int get() = 429
    }

    /**
     * Any other status outside 2xx. [message] is the server's reason when the body is a JSON
     * string, as Tokkl sends for a change it refuses, such as "Name has already been taken", and
     * null for any other body.
     */
    data class UnexpectedStatus(override val status: Int, val message: String? = null) : Http

    /**
     * No HTTP response arrived: no route, DNS failure, refused connection,
     * TLS failure or timeout. Not an [Http] error.
     */
    data class Network(val cause: IOException) : ApiError

    /** A 2xx response whose body is not the expected JSON. */
    data class InvalidResponse(val cause: IllegalArgumentException) : ApiError

    /**
     * The request, or a redirect, pointed at [origin] (`scheme://host:port`),
     * which is not the configured server, so nothing was sent there. Carries
     * no credential.
     */
    data class HostNotAllowed(val origin: String) : ApiError

    /**
     * The request was refused before any credential left the phone, because
     * the server failed a trust check. A signed-in session shows it on every
     * screen.
     */
    sealed interface TrustRefused : ApiError

    /**
     * A [TrustRefused] the user can answer: the user has not agreed to trust
     * the server, and the answer to the matching prompt lets the next request
     * through.
     */
    sealed interface TrustQuestion : TrustRefused

    /**
     * [server] is a plain `http://` URL the user has not confirmed, so the
     * request, which would have carried the credentials unencrypted, was not
     * sent.
     */
    data class CleartextNotConfirmed(val server: ServerUrl) : TrustQuestion

    /**
     * No system or user CA trusts the server's [certificate], and the user
     * has not pinned it. [changed] is true when the user pinned another
     * certificate for that server. The TLS handshake failed, so nothing was
     * sent.
     */
    data class UntrustedCertificate(val certificate: ServerCertificate, val changed: Boolean) : TrustQuestion

    /**
     * The server's certificate does not name [host], the host of the URL,
     * in its subjectAltName, so OkHttp's host name check refuses it, also
     * when the user pinned it. Nothing was sent. No answer from the user
     * lets a request through: the certificate has to name the host, or the
     * URL has to use a host it names.
     */
    data class CertificateHostMismatch(val host: String) : TrustRefused
}
