package com.github.e0ipso.tokkl.api

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Every request the app sends to Tokkl or Toggl Track, one method per request, grouped by
 * resource: the Toggl API v9, the Reports API v3 and Tokkl's own `/api/app` routes that section 6
 * of Tokkl's docs/PRD.md lists. Each method fixes its request's method, path, query, body and
 * whether OkHttp may send it twice, and the type its answer reads as; callers pass typed values,
 * ids among them, and never build a URL or a JSON body.
 *
 * [SharedHttpClient.api] makes one, for the signed-in account or for the server sign-in tries.
 */
class TokklApi internal constructor(client: ApiTransport) {
    val account = AccountApi(client)

    val entries = TimeEntriesApi(client)

    val trash = TrashApi(client)

    val projects = ProjectsApi(client)

    val clients = ClientsApi(client)

    val tags = TagsApi(client)

    val reports = ReportsApi(client)

    val timer = TimerApi(client)
}

internal fun <T> json(serializer: SerializationStrategy<T>, body: T): JsonElement =
    Wire.encodeToJsonElement(serializer, body)

/** The body of a POST that Tokkl reads nothing from. OkHttp sends no POST without one. */
internal val EMPTY_BODY = JsonObject(emptyMap())

/**
 * The API of the signed-in account, or null when signed out. The first call after process start,
 * sign-in or sign-out reads the stored session from the disk and the Keystore, on the IO
 * dispatcher, so any thread may call it.
 */
fun interface ApiClientProvider {
    suspend operator fun invoke(): TokklApi?
}
