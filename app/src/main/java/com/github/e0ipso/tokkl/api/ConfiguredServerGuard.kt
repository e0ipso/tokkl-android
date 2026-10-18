package com.github.e0ipso.tokkl.api

import java.io.IOException
import java.net.ProtocolException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * The guard refused a request, or a redirect, to [origin] (`scheme://host:port`),
 * which is not the configured server. Nothing was sent there. The message
 * names the origin only: no path, query or credential.
 */
class HostNotAllowedException(val origin: String) :
    IOException("Refused a request to $origin, which is not the configured server")

/**
 * Lets requests reach the configured server only. It checks the request
 * before OkHttp connects, and it follows redirects itself: OkHttp would
 * follow them inside the call, past any application interceptor, so
 * [SharedHttpClient] turns automatic redirects off. A redirect to the same
 * origin is followed, with OkHttp's rules for the method and body. A redirect
 * anywhere else fails with [HostNotAllowedException].
 */
internal class ConfiguredServerGuard(private val configured: () -> ServerUrl?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // One server for the whole call, redirects included.
        val server = configured()
        var request = chain.request()
        requireAllowed(server, request.url)
        var followUps = 0
        while (true) {
            val response = chain.proceed(request)
            val next = redirect(request, response) ?: return response
            response.close()
            requireAllowed(server, next.url)
            if (++followUps > MAX_FOLLOW_UPS) throw ProtocolException("Too many redirects: $followUps")
            request = next
        }
    }

    private fun requireAllowed(server: ServerUrl?, url: HttpUrl) {
        if (server == null || !server.isSameOrigin(url)) throw HostNotAllowedException(origin(url))
    }

    /** The request OkHttp would send for a redirect, or null when [response] is not one it follows. */
    private fun redirect(request: Request, response: Response): Request? {
        if (response.code !in REDIRECT_CODES) return null
        val location = response.header("Location") ?: return null
        val url = request.url.resolve(location) ?: return null
        val builder = request.newBuilder().url(url)
        val keepsMethod = response.code == HTTP_TEMPORARY_REDIRECT || response.code == HTTP_PERMANENT_REDIRECT
        if (request.method != "GET" && request.method != "HEAD" && !keepsMethod) {
            builder.method("GET", null)
                .removeHeader("Transfer-Encoding")
                .removeHeader("Content-Length")
                .removeHeader("Content-Type")
        }
        return builder.build()
    }

    private companion object {
        const val HTTP_TEMPORARY_REDIRECT = 307
        const val HTTP_PERMANENT_REDIRECT = 308
        val REDIRECT_CODES = setOf(300, 301, 302, 303, HTTP_TEMPORARY_REDIRECT, HTTP_PERMANENT_REDIRECT)

        /** OkHttp's own limit. */
        const val MAX_FOLLOW_UPS = 20

        fun origin(url: HttpUrl): String {
            val host = if (':' in url.host) "[${url.host}]" else url.host
            return "${url.scheme}://$host:${url.port}"
        }
    }
}
