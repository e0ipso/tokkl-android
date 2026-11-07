package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/** What Tokkl answers to a delete of an entry it does not have, or has in the trash. */
internal const val NOT_FOUND = 404

/** What a server answers to a stop of an entry stopped already, as Tokkl's stop endpoint does. */
internal const val CONFLICT = 409

/**
 * True when the request that failed with this error cannot have changed anything on the server:
 * the server refused it with a 4xx, or it never left the phone, as with no route or a trust
 * refusal. A 5xx, a timeout, a dropped connection or a 2xx the phone could not read may come after
 * the server stored it.
 */
internal fun ApiError.leftNothingOnTheServer(): Boolean = when (this) {
    is ApiError.Http -> status in 400..499

    is ApiError.Network ->
        cause is ConnectException || cause is UnknownHostException ||
            cause is NoRouteToHostException

    is ApiError.InvalidResponse -> false

    is ApiError.HostNotAllowed, is ApiError.TrustRefused -> true
}

/**
 * True when a request that failed with this error is worth sending again at once: its connection
 * failed after the request left the phone, as a pooled connection does when the server closed it
 * while it sat idle. Tokkl closes one after Node's keep-alive timeout of 5 seconds, and OkHttp
 * checks a pooled connection only after 10. A timeout would wait as long again, and a server the
 * phone cannot reach at all stays unreachable a moment later.
 */
internal fun ApiError.worthAnotherTryAtOnce(): Boolean =
    this is ApiError.Network && cause !is InterruptedIOException && !leftNothingOnTheServer()
