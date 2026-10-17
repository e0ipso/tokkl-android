package com.github.e0ipso.tokkl.api

import java.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Client for the Toggl Track API v9 and Reports API v3, as served by Tokkl or
 * by Toggl Track. Build endpoint URLs with [server], then call [get] or
 * [send]. Calls block the calling thread, so make them off the main thread.
 */
class TogglApiClient(
    val server: ServerUrl,
    private val credentials: ApiCredentials,
    private val httpClient: OkHttpClient = SharedHttpClient.instance,
) {

    fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        send("GET", url, body = null, deserializer)

    /**
     * Sends [body] as JSON, or no body when it is null. OkHttp refuses a null
     * body for POST, PUT and PATCH.
     */
    fun <T> send(
        method: String,
        url: HttpUrl,
        body: JsonElement?,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials.authorizationHeader)
            .method(method, body?.let { ApiJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON) })
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.toResult(deserializer) }
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.Network(e))
        }
    }

    private fun <T> Response.toResult(deserializer: DeserializationStrategy<T>): ApiResult<T> =
        when {
            isSuccessful -> decode(body.string(), deserializer)
            code == 403 -> ApiResult.Failure(ApiError.AuthFailed)
            code == 429 -> ApiResult.Failure(ApiError.RateLimited(retryAfterSeconds(header("Retry-After"))))
            else -> ApiResult.Failure(ApiError.UnexpectedStatus(code))
        }

    private fun <T> decode(text: String, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        try {
            ApiResult.Success(ApiJson.decodeFromString(deserializer, text))
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization's SerializationException is an IllegalArgumentException.
            ApiResult.Failure(ApiError.InvalidResponse(e))
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** Tokkl sends whole seconds. An HTTP date or a negative number counts as missing. */
        fun retryAfterSeconds(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    }
}
