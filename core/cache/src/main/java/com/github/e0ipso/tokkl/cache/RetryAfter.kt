package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The wait a 429 asks for (docs/PRD.md O6): no request before its `Retry-After`, or before
 * [DEFAULT] has passed when the header is missing. It lives in memory; the queue itself is on
 * disk. Only [ChangeSender]'s drain touches it, one drain at a time.
 */
internal class RetryAfter(private val clock: Clock) {

    private var until: Instant? = null

    /** The instant the phone still waits for, or null when it may send. Forgets a wait that has passed. */
    fun pending(): Instant? {
        val wait = until ?: return null
        if (clock.instant().isBefore(wait)) return wait
        until = null
        return null
    }

    /** Starts the wait that [error] asks for and returns when it ends. */
    fun start(error: ApiError.RateLimited): Instant =
        clock.instant().plusSeconds(error.retryAfterSeconds ?: DEFAULT.seconds).also { until = it }

    fun forget() {
        until = null
    }

    companion object {
        /** How long to wait after a 429 that names no whole number of seconds. */
        val DEFAULT: Duration = Duration.ofSeconds(60)
    }
}
