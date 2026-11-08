package com.github.e0ipso.tokkl.session

import android.content.res.Resources
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.core.session.R

/**
 * Why the server failed a request, as the user reads it: the catalog pages, which change the
 * account's projects, clients and tags on the server at once and queue nothing, say it for a load
 * or a change, and the timer's surfaces for a timer action. Every problem leaves the server as it
 * was.
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

        status in HttpStatus.CLIENT_ERRORS -> message?.let(ServerProblem::Refused) ?: ServerProblem.Unexpected(status)

        else -> ServerProblem.Unexpected(status)
    }

    ApiError.AuthFailed -> ServerProblem.AuthFailed

    is ApiError.TrustRefused -> ServerProblem.TrustRefused

    is ApiError.RateLimited -> ServerProblem.Unexpected(status)

    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> ServerProblem.Unexpected(null)
}

/** Bad gateway, service unavailable and gateway timeout. */
private val PROXY_UNREACHABLE = setOf(502, 503, 504)

/** What [problem] means for the user, in a sentence, from [resources]. */
fun problemText(resources: Resources, problem: ServerProblem): String = when (problem) {
    ServerProblem.Unreachable -> resources.getString(R.string.server_problem_unreachable)

    ServerProblem.AuthFailed -> resources.getString(R.string.server_problem_auth)

    ServerProblem.TrustRefused -> resources.getString(R.string.server_problem_trust)

    is ServerProblem.Refused -> problem.reason

    is ServerProblem.Unexpected -> problem.status?.let { resources.getString(R.string.server_problem_status, it) }
        ?: resources.getString(R.string.server_problem_unreadable)
}
