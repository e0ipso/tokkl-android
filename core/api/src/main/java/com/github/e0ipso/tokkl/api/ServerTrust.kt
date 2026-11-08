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
