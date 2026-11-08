package com.github.e0ipso.tokkl.api

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Every request the app sends to Tokkl or Toggl Track, one method per request, grouped by
 * resource: the Toggl API v9, the Reports API v3 and Tokkl's own `/api/app` routes that section 6
 * of Tokkl's docs/PRD.md lists. Each method fixes its request's method, path, query, body and
 * whether OkHttp may send it twice; callers pass typed values and never build a URL or a JSON
 * body. Where the app reads one answer in more than one shape, or into a type its feature owns,
 * the method takes the caller's deserializer.
 *
 * [HttpTokklApi] sends the requests through a [TogglApiClient]. A test can stand in for a group
 * without a server.
 */
interface TokklApi {
    /** The signed-in user, their preferences and their workspaces. */
    val account: AccountApi

    /** The user's time entries. */
    val entries: TimeEntriesApi

    /** Tokkl's trash of deleted entries. */
    val trash: TrashApi

    /** A workspace's projects and their tasks. */
    val projects: ProjectsApi

    /** A workspace's clients. */
    val clients: ClientsApi

    /** A workspace's tags. */
    val tags: TagsApi

    /** The Reports API v3 and Tokkl's own report routes. */
    val reports: ReportsApi

    /** Tokkl's server-owned timer: the pause chain and the Pomodoro session. */
    val timer: TimerApi
}

/**
 * [TokklApi] over [client], which holds the server, the credentials and the shared HTTP client:
 * the signed-in account's, or the one sign-in tries.
 */
class HttpTokklApi(val client: TogglApiClient) : TokklApi {
    override val account: AccountApi = HttpAccountApi(client)
    override val entries: TimeEntriesApi = HttpTimeEntriesApi(client)
    override val trash: TrashApi = HttpTrashApi(client)
    override val projects: ProjectsApi = HttpProjectsApi(client)
    override val clients: ClientsApi = HttpClientsApi(client)
    override val tags: TagsApi = HttpTagsApi(client)
    override val reports: ReportsApi = HttpReportsApi(client)
    override val timer: TimerApi = HttpTimerApi(client)
}

/** [body] as the JSON tree [TogglApiClient] sends, encoded with [ApiJson]. */
internal fun <T> json(serializer: SerializationStrategy<T>, body: T): JsonElement =
    ApiJson.encodeToJsonElement(serializer, body)

/** The body of a POST that Tokkl reads nothing from. OkHttp sends no POST without one. */
internal val EMPTY_BODY = JsonObject(emptyMap())
