package com.github.e0ipso.tokkl.api

import java.io.IOException

/**
 * What the user has agreed to for a server. [SharedHttpClient] checks it on
 * every request, so no screen or background job can skip the question.
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
}

/**
 * Where [SharedHttpClient] reads the user's trust decisions from. The app sets
 * this once at start. Until then nothing is trusted, so every request to a
 * plain `http://` server is refused.
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
    }
}
