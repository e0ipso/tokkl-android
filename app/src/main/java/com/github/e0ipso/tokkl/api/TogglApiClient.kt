package com.github.e0ipso.tokkl.api

import java.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * Client for the Toggl Track API v9 and Reports API v3, as served by Tokkl or
 * by Toggl Track. Build endpoint URLs with [server], then call [get], [send],
 * [sendWithHeaders] or [sendNoContent]. Calls block the calling thread, so
 * make them off the main thread.
 */
class TogglApiClient(
    val server: ServerUrl,
    private val credentials: ApiCredentials,
    private val httpClient: OkHttpClient = SharedHttpClient.instance,
    private val trustListener: TrustListener? = null,
) {

    fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        send("GET", url, body = null, deserializer)

    /**
     * Sends [body] as JSON, or no body when it is null, and decodes the 2xx
     * response body. An empty body does not decode, so use [sendNoContent]
     * for calls that answer without one. OkHttp refuses a null body for POST,
     * PUT and PATCH.
     *
     * When a connection that OkHttp reused fails after the request went out,
     * OkHttp sends the request again on a new one, so a POST whose answer was
     * lost can create its object twice. With [resend] false OkHttp sends
     * [body] once and the caller gets the failure.
     */
    fun <T> send(
        method: String,
        url: HttpUrl,
        body: JsonElement?,
        deserializer: DeserializationStrategy<T>,
        resend: Boolean = true,
    ): ApiResult<T> = execute(method, url, body, resend) { decode(it.body.string(), deserializer) }

    /**
     * Like [send], and keeps the response's headers too, for calls that put
     * values in them, such as the Reports API's `X-Next-Row-Number`, the row
     * the next page of a detailed report starts at.
     */
    fun <T> sendWithHeaders(
        method: String,
        url: HttpUrl,
        body: JsonElement?,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<WithHeaders<T>> = execute(method, url, body, resend = true) { response ->
        when (val decoded = decode(response.body.string(), deserializer)) {
            is ApiResult.Success -> ApiResult.Success(WithHeaders(decoded.value, response.headers))
            is ApiResult.Failure -> decoded
        }
    }

    /**
     * Like [send], for calls whose response body the caller does not need,
     * such as Tokkl's DELETE of a time entry, task, client or tag, which
     * answer 200 with an empty body. Tokkl's project DELETE answers with the
     * project id as JSON, which this method ignores too. Any 2xx is a success.
     */
    fun sendNoContent(method: String, url: HttpUrl, body: JsonElement? = null): ApiResult<Unit> =
        execute(method, url, body, resend = true) { ApiResult.Success(Unit) }

    private fun <T> execute(
        method: String,
        url: HttpUrl,
        body: JsonElement?,
        resend: Boolean,
        onSuccess: (Response) -> ApiResult<T>,
    ): ApiResult<T> {
        val json = body?.let { ApiJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON) }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials.authorizationHeader)
            .method(method, if (resend || json == null) json else SentOnce(json))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                // The server answered, so it passed the trust checks.
                trustListener?.answered()
                if (response.isSuccessful) onSuccess(response) else ApiResult.Failure(httpError(response))
            }
        } catch (e: HostNotAllowedException) {
            ApiResult.Failure(ApiError.HostNotAllowed(e.origin))
        } catch (e: CleartextNotConfirmedException) {
            refused(ApiError.CleartextNotConfirmed(e.server))
        } catch (e: IOException) {
            val untrusted = e.untrustedCertificate()
            when {
                untrusted != null -> refused(ApiError.UntrustedCertificate(untrusted.certificate, changed = untrusted.pinChanged))
                e.certificateHostMismatch() -> refused(ApiError.CertificateHostMismatch(url.host))
                else -> ApiResult.Failure(ApiError.Network(e))
            }
        }
    }

    private fun refused(refusal: ApiError.TrustRefused): ApiResult.Failure {
        trustListener?.refused(refusal)
        return ApiResult.Failure(refusal)
    }

    private fun httpError(response: Response): ApiError.Http =
        when (response.code) {
            403 -> ApiError.AuthFailed
            429 -> ApiError.RateLimited(retryAfterSeconds(response.header("Retry-After")))
            else -> ApiError.UnexpectedStatus(response.code, reason(response))
        }

    /**
     * The error body when it is one JSON string, as Tokkl's errors are, read up to
     * [MAX_REASON_BYTES]. Null for any other body, such as a proxy's HTML page, which is cut
     * there and so never parses.
     */
    private fun reason(response: Response): String? {
        val text = try {
            response.peekBody(MAX_REASON_BYTES).string()
        } catch (e: IOException) {
            return null
        }
        val element = try {
            ApiJson.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization's SerializationException is an IllegalArgumentException.
            return null
        }
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun <T> decode(text: String, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        try {
            ApiResult.Success(ApiJson.decodeFromString(deserializer, text))
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization's SerializationException is an IllegalArgumentException.
            ApiResult.Failure(ApiError.InvalidResponse(e))
        }

    /**
     * [body], marked one-shot: OkHttp sends a one-shot body again only when
     * the request never left the phone, such as when a connection fails to
     * open.
     */
    private class SentOnce(private val body: RequestBody) : RequestBody() {
        override fun contentType(): MediaType? = body.contentType()

        override fun contentLength(): Long = body.contentLength()

        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)

        override fun isOneShot(): Boolean = true
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** Tokkl's reasons are one short sentence. */
        const val MAX_REASON_BYTES = 2048L

        /** Tokkl sends whole seconds. An HTTP date or a negative number counts as missing. */
        fun retryAfterSeconds(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    }
}

/** A decoded response body and the headers it came with. */
class WithHeaders<out T>(val value: T, val headers: Headers)

/**
 * Hears how the signed-in session's requests fare with the trust checks, so
 * the app can show the matching prompt from any screen. Called on the thread
 * that made the request.
 */
interface TrustListener {

    /** A request was refused for [refusal] before anything was sent. */
    fun refused(refusal: ApiError.TrustRefused)

    /** The server answered a request, so it passes the trust checks again. */
    fun answered()
}
