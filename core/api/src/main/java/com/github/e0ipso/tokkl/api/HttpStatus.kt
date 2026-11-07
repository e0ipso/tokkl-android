package com.github.e0ipso.tokkl.api

/** The HTTP status codes the app tells apart. */
object HttpStatus {
    const val OK = 200
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val GONE = 410
    const val TOO_MANY_REQUESTS = 429
    const val BAD_GATEWAY = 502
    const val SERVICE_UNAVAILABLE = 503
    const val GATEWAY_TIMEOUT = 504

    /** The 4xx codes: the server refused the request as it was sent. */
    val CLIENT_ERRORS = 400..499

    /** The first 5xx code: the server failed. */
    const val FIRST_SERVER_ERROR = 500
}
