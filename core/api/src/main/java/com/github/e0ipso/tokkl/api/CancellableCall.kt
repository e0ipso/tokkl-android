package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Response

/**
 * Runs this call on the calling thread, as [Call.execute] does, and hands the response to [read]
 * before it closes. Cancelling the coroutine cancels the call, which then fails at once on that
 * thread, and this function throws the cancellation. The thread blocks until the call ends, so run
 * it on the IO dispatcher.
 *
 * The call runs on the calling thread rather than OkHttp's dispatcher, so the dispatcher's limit
 * of five calls to one host never holds a request back behind an open event stream, and OkHttp's
 * retry and failure rules stay those of [Call.execute].
 */
internal suspend fun <T> Call.executeCancellable(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        // After a cancellation the continuation ignores this result and throws the cancellation.
        continuation.resumeWith(runCatching { execute().use(read) })
    }
