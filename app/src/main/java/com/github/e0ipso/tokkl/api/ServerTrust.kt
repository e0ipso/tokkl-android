package com.github.e0ipso.tokkl.api

import java.io.IOException

/**
 * What the user has agreed to for a server: sending to a plain `http://` URL,
 * and trusting a self-signed certificate by its fingerprint. [SharedHttpClient]
 * checks it on every request and every TLS handshake, so no screen or
 * background job can skip the question.
 */
interface ServerTrust {

    /**
     * Whether the user confirmed that requests to [server], a plain `http://`
     * URL, may carry the token or password unencrypted.
     */
    fun isCleartextConfirmed(server: ServerUrl): Boolean

    /** Records the user's confirmation for [server]. Does disk I/O. */
    @Throws(IOException::class)
    fun confirmCleartext(server: ServerUrl)

    /**
     * The SHA-256 of the certificate the user trusted for the TLS server at
     * [host] and [port], as lowercase hex, or null when there is none.
     */
    fun pinnedSha256(host: String, port: Int): String?

    /** Trusts [certificate], and no other, for its host and port. Does disk I/O. */
    @Throws(IOException::class)
    fun pinCertificate(certificate: ServerCertificate)
}

/**
 * Where [SharedHttpClient] reads the user's trust decisions from. The app sets
 * this once at start. Until then nothing is trusted, so every request to a
 * plain `http://` server is refused, and only certificates that a system or
 * user CA signed pass.
 */
object ConfiguredTrust {

    @Volatile
    var current: ServerTrust = TrustNothing
        private set

    fun readFrom(trust: ServerTrust) {
        current = trust
    }

    private object TrustNothing : ServerTrust {
        override fun isCleartextConfirmed(server: ServerUrl): Boolean = false

        override fun confirmCleartext(server: ServerUrl) {
            throw IOException("No trust store is configured")
        }

        override fun pinnedSha256(host: String, port: Int): String? = null

        override fun pinCertificate(certificate: ServerCertificate) {
            throw IOException("No trust store is configured")
        }
    }
}
