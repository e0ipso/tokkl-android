package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError

/**
 * Why a page that changes the account's projects, clients or tags could not load them from the
 * server or send a change. These pages change things on the server at once and queue nothing, so
 * every problem leaves the server as it was.
 */
sealed interface ServerProblem {

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : ServerProblem

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : ServerProblem

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : ServerProblem

    /** The server refused the change and said why, such as "Name has already been taken". */
    data class Refused(val reason: String) : ServerProblem

    /** Any other answer. [status] is the HTTP status, or null when the body was not what the app reads. */
    data class Unexpected(val status: Int?) : ServerProblem
}

/** The [ServerProblem] behind this error. */
fun ApiError.toServerProblem(): ServerProblem = when (this) {
    is ApiError.Network -> ServerProblem.Unreachable
    is ApiError.UnexpectedStatus -> when {
        // A reverse proxy answers these while the Tokkl server behind it is down.
        status in PROXY_UNREACHABLE -> ServerProblem.Unreachable
        message != null && status in 400..499 -> ServerProblem.Refused(message)
        else -> ServerProblem.Unexpected(status)
    }
    ApiError.AuthFailed -> ServerProblem.AuthFailed
    is ApiError.TrustRefused -> ServerProblem.TrustRefused
    is ApiError.RateLimited -> ServerProblem.Unexpected(status)
    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> ServerProblem.Unexpected(null)
}

/** Bad gateway, service unavailable and gateway timeout. */
private val PROXY_UNREACHABLE = setOf(502, 503, 504)
