package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.trash.TrashEntry
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.serialization.builtins.ListSerializer

/**
 * Whether the server stopped the entry of the captured Stop [change] as the phone asked. True only
 * after reading the exact stopped row, including a short row hidden in Trash.
 */
internal fun stoppedOnServer(api: TogglApiClient, change: PendingChange): ApiResult<Boolean> {
    val url = api.server.api("me", "time_entries", change.entryId.toString())
    return when (val result = api.get(url, ServerTimeEntry.serializer())) {
        is ApiResult.Success -> when {
            result.value.stop == null -> ApiResult.Success(false)
            result.value.matchesStop(change) -> ApiResult.Success(true)
            else -> ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The server stopped this entry differently"))
        }
        is ApiResult.Failure -> if (change.shortStop && (result.error as? ApiError.Http)?.status == NOT_FOUND) {
            when (val trash = api.get(api.server.app("trash"), ListSerializer(TrashEntry.serializer()))) {
                is ApiResult.Success -> if (trash.value.any { it.matchesStop(change) }) ApiResult.Success(true)
                    else ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The short Stop is not in Trash"))
                is ApiResult.Failure -> trash
            }
        } else result
    }
}

// Android 12's Instant.parse rejects Tokkl's +00:00 timestamps; OffsetDateTime accepts them.
internal fun ServerTimeEntry.matchesStop(change: PendingChange): Boolean =
    id == change.entryId && workspaceId == change.workspaceId &&
        runCatching { OffsetDateTime.parse(start).toInstant() }.getOrNull() == change.start.truncatedTo(ChronoUnit.SECONDS) &&
        stop?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } == change.stop?.truncatedTo(ChronoUnit.SECONDS) &&
        (serverDeletedAt != null) == change.shortStop

private fun TrashEntry.matchesStop(change: PendingChange): Boolean =
    id == change.entryId && deletedAt != null &&
        runCatching { OffsetDateTime.parse(start).toInstant() }.getOrNull() == change.start.truncatedTo(ChronoUnit.SECONDS) &&
        stop?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } == change.stop?.truncatedTo(ChronoUnit.SECONDS)
