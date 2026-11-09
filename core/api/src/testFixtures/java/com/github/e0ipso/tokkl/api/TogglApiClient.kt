package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import kotlin.coroutines.CoroutineContext

/**
 * The transport under [TokklApi], for tests that send a request the app does not send itself or
 * read an answer the app reads otherwise: the same calls, over [httpClient], to [server] with
 * [credentials]. The app's code never sees it.
 */
class TogglApiClient(
    val server: ServerUrl,
    credentials: ApiCredentials,
    httpClient: OkHttpClient,
    trustListener: TrustListener? = null,
    successListener: SuccessListener? = null,
    io: CoroutineContext,
) {
    internal val transport = ApiTransport(server, credentials, httpClient, trustListener, successListener, io)

    suspend fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        transport.get(url, deserializer)

    suspend fun <T> send(
        method: HttpMethod,
        url: HttpUrl,
        body: JsonElement?,
        deserializer: DeserializationStrategy<T>,
        resend: Boolean = true,
    ): ApiResult<T> = transport.send(method, url, body, deserializer, resend)

    suspend fun <T> sendRefusable(
        method: HttpMethod,
        url: HttpUrl,
        body: JsonElement,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<Refusable<T>> = transport.sendRefusable(method, url, body, deserializer)

    suspend fun sendNoContent(
        method: HttpMethod,
        url: HttpUrl,
        body: JsonElement? = null,
        resend: Boolean = true,
    ): ApiResult<Unit> = transport.sendNoContent(method, url, body, resend)
}

/** The app's [TokklApi] over [client], as the signed-in session builds it. */
@Suppress("FunctionName")
fun TokklApi(client: TogglApiClient): TokklApi = TokklApi(client.transport)

/** Opens each API over [httpClient], as sign-in opens the server it tries. */
fun apiConnector(httpClient: OkHttpClient): ApiConnector =
    ApiConnector { server, credentials, io -> TokklApi(TogglApiClient(server, credentials, httpClient, io = io)) }

/** The shared client's OkHttp client, for tests that derive a client from it or watch its calls. */
val SharedHttpClient.instance: OkHttpClient get() = client

/** A Toggl API v9 endpoint of this server, such as `api("me")` for `GET /api/v9/me`. */
fun ServerUrl.api(vararg segments: String): HttpUrl = apiUrl(*segments)

/** A Reports API v3 endpoint of this server. */
fun ServerUrl.reports(vararg segments: String): HttpUrl = reportsUrl(*segments)

/** A Tokkl endpoint of this server outside the Toggl API, such as `app("trash")`. */
fun ServerUrl.app(vararg segments: String): HttpUrl = appUrl(*segments)

/** The preferences answer [json] holds, as `GET /api/v9/me/preferences` would answer it. */
fun mePreferences(json: String): MePreferences = MePreferences(Json.parseToJsonElement(json) as JsonObject)

/** The app's JSON settings, for tests that build or read the API's types as JSON trees. */
val WireJson: Json get() = Wire
