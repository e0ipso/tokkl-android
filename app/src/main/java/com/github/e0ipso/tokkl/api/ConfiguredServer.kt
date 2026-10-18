package com.github.e0ipso.tokkl.api

/**
 * The one server the app may send requests to (docs/PRD.md principle 1).
 * [SharedHttpClient] refuses every request and every redirect whose scheme,
 * host or port differs from [current].
 */
object ConfiguredServer {

    @Volatile
    private var stored: () -> ServerUrl? = { null }

    @Volatile
    private var signInCandidate: ServerUrl? = null

    private val signInLock = Any()

    /**
     * During sign-in, the URL being tried. Otherwise the stored server. Null
     * when there is neither, which refuses every request.
     */
    val current: ServerUrl?
        get() = signInCandidate ?: stored()

    /** Where [current] reads the stored server from. The app sets this once at start. */
    fun readStoredFrom(source: () -> ServerUrl?) {
        stored = source
    }

    /**
     * Runs [attempt] with [candidate] as the configured server, since sign-in
     * talks to a server before the app has stored it. One attempt runs at a
     * time, and the stored server applies again when it returns.
     */
    fun <T> signingIn(candidate: ServerUrl, attempt: () -> T): T =
        synchronized(signInLock) {
            signInCandidate = candidate
            try {
                attempt()
            } finally {
                signInCandidate = null
            }
        }
}
