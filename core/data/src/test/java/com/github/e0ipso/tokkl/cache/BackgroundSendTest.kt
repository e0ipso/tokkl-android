package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the background send does after each way a drain can end (docs/PRD.md O4 and O6). */
class BackgroundSendTest {

    @Test
    fun anEmptyQueueOrASignedOutPhoneEndsTheWork() {
        assertEquals(AfterBackgroundSend.DONE, ChangeSender.Outcome.Empty(sent = 2).afterBackgroundSend())
        assertEquals(AfterBackgroundSend.DONE, ChangeSender.Outcome.Empty(sent = 0).afterBackgroundSend())
        assertEquals(AfterBackgroundSend.DONE, ChangeSender.Outcome.SignedOut(sent = 0).afterBackgroundSend())
    }

    @Test
    fun noNetworkA5xxA429AndTheDiskRetry() {
        val transient = listOf(
            ApiError.Network(ConnectException("no route")),
            ApiError.Network(SocketTimeoutException("timeout")),
            ApiError.Network(IOException("connection reset")),
            ApiError.UnexpectedStatus(500),
            ApiError.UnexpectedStatus(503),
            ApiError.RateLimited(retryAfterSeconds = null),
        )
        for (error in transient) {
            assertEquals("$error", AfterBackgroundSend.RETRY, ChangeSender.Outcome.Stopped(sent = 1, error).afterBackgroundSend())
        }
        assertEquals(
            "a 429's wait",
            AfterBackgroundSend.RETRY,
            ChangeSender.Outcome.Waiting(sent = 0, until = Instant.parse("2026-10-19T08:01:00Z")).afterBackgroundSend(),
        )
        assertEquals("a drain that failed on the disk", AfterBackgroundSend.RETRY, null.afterBackgroundSend())
    }

    @Test
    fun aRefusalWaitsForTheUserInsteadOfRetrying() {
        val server = checkNotNull(ServerUrl.parse("http://10.0.2.2:8080"))
        val refusals = listOf(
            ApiError.UnexpectedStatus(400, "Description is too long"),
            ApiError.AuthFailed,
            ApiError.UnexpectedStatus(404),
            ApiError.UnexpectedStatus(409),
            ApiError.InvalidResponse(IllegalArgumentException("not JSON")),
            ApiError.HostNotAllowed("http://example.com:80"),
            ApiError.CleartextNotConfirmed(server),
        )
        for (error in refusals) {
            assertEquals("$error", AfterBackgroundSend.GIVE_UP, ChangeSender.Outcome.Stopped(sent = 0, error).afterBackgroundSend())
        }
    }
}
