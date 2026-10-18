package com.github.e0ipso.tokkl.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The configured server's base URL, which may carry a path such as
 * `https://example.com/tokkl`. Endpoint URLs keep that path, with the Toggl
 * API v9 under `/api/v9` and the Reports API v3 under `/reports/api/v3`.
 */
class ServerUrl private constructor(private val base: HttpUrl) {

    /** A Toggl API v9 endpoint, such as `api("me")` for `GET /api/v9/me`. */
    fun api(vararg segments: String): HttpUrl = endpoint(API_V9, segments)

    /** A Reports API v3 endpoint, such as `reports("workspace", "1", "search", "time_entries")`. */
    fun reports(vararg segments: String): HttpUrl = endpoint(REPORTS_V3, segments)

    /**
     * A Tokkl-only endpoint outside the Toggl API, such as `app("trash")` for
     * `GET /api/app/trash`. Toggl Track and older Tokkl servers answer 404.
     */
    fun app(vararg segments: String): HttpUrl = endpoint(APP, segments)

    /**
     * Each segment is percent-encoded on its own, so a `/` or `?` inside one
     * cannot change the path or add a query.
     */
    private fun endpoint(prefix: List<String>, segments: Array<out String>): HttpUrl =
        base.newBuilder()
            .apply { (prefix + segments).forEach(::addPathSegment) }
            .build()

    /** `scheme://host:port`, with an IPv6 host in brackets. */
    val origin: String
        get() = base.origin()

    /** Whether requests to this server travel unencrypted, over plain `http://`. */
    val isCleartext: Boolean
        get() = !base.isHttps

    /** Whether [url] has this server's scheme, host and port. */
    fun isSameOrigin(url: HttpUrl): Boolean =
        url.scheme == base.scheme && url.host == base.host && url.port == base.port

    override fun toString(): String = base.toString()

    override fun equals(other: Any?): Boolean = other is ServerUrl && other.base == base

    override fun hashCode(): Int = base.hashCode()

    companion object {
        private val API_V9 = listOf("api", "v9")
        private val REPORTS_V3 = listOf("reports", "api", "v3")
        private val APP = listOf("api", "app")

        /**
         * Returns null unless [url] is an absolute `http` or `https` URL with
         * no user name, password, query or fragment. The app stores the URL in
         * plain text, so it must not carry credentials. Empty path segments are
         * dropped, so `https://example.com/tokkl/` and
         * `https://example.com/tokkl` are the same server.
         */
        fun parse(url: String): ServerUrl? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            if (parsed.encodedUsername.isNotEmpty() || parsed.encodedPassword.isNotEmpty()) return null
            if (parsed.encodedQuery != null || parsed.encodedFragment != null) return null
            val base = parsed.newBuilder()
                .encodedPath("/")
                .apply { parsed.encodedPathSegments.filter { it.isNotEmpty() }.forEach(::addEncodedPathSegment) }
                .build()
            return ServerUrl(base)
        }
    }
}

/** `scheme://host:port`, with an IPv6 host in brackets. Holds no path, query or credential. */
internal fun HttpUrl.origin(): String {
    val host = if (':' in host) "[$host]" else host
    return "$scheme://$host:$port"
}
