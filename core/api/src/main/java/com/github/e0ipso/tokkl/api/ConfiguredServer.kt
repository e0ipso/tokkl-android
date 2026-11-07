package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where [ConfiguredServer] reads the stored server from, on every request. */
fun interface StoredServer {
    /** The stored server, or null when there is none. May read the disk. */
    fun load(): ServerUrl?
}

/**
 * The one server the app may send requests to (docs/PRD.md principle 1).
 * [SharedHttpClient] refuses every request and every redirect whose scheme,
 * host or port differs from [current]. The app makes one, with the stored
 * server as [stored], so every process sends requests only to the stored
 * server from its first request on.
 */
class ConfiguredServer(private val stored: StoredServer) {

    @Volatile
    private var signInCandidate: ServerUrl? = null

    private val signIn = Mutex()

    /**
     * During sign-in, the URL being tried. Otherwise the stored server. Null
     * when there is neither, which refuses every request.
     */
    val current: ServerUrl?
        get() = signInCandidate ?: stored.load()

    /**
     * Runs [attempt] with [candidate] as the configured server, since sign-in
     * talks to a server before the app has stored it. One attempt runs at a
     * time, and the stored server applies again when it returns.
     */
    suspend fun <T> signingIn(candidate: ServerUrl, attempt: suspend () -> T): T = signIn.withLock {
        signInCandidate = candidate
        try {
            attempt()
        } finally {
            signInCandidate = null
        }
    }
}
