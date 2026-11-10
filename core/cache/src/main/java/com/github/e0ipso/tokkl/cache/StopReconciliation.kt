package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.api.flatMap
import com.github.e0ipso.tokkl.model.EntryId
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Whether the server stopped the entry of the captured Stop [change] as the phone asked. True only
 * after reading the exact stopped row, including a short row hidden in Trash.
 */
internal suspend fun stoppedOnServer(server: TokklApi, change: PendingChange): ApiResult<Boolean> =
    when (val result = server.entries.get(EntryId(change.entryId))) {
        is ApiResult.Success -> when {
            result.value.stop == null -> ApiResult.Success(false)
            result.value.matchesStop(change) -> ApiResult.Success(true)
            else -> ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The server stopped this entry differently"))
        }

        is ApiResult.Failure -> if (change.shortStop && (result.error as? ApiError.Http)?.status == NOT_FOUND) {
            server.trash.list().flatMap { trash ->
                if (trash.any { it.matchesStop(change) }) {
                    ApiResult.Success(true)
                } else {
                    ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The short Stop is not in Trash"))
                }
            }
        } else {
            result
        }
    }

// Android 12's Instant.parse rejects Tokkl's +00:00 timestamps; OffsetDateTime accepts them.
internal fun ServerTimeEntry.matchesStop(change: PendingChange): Boolean =
    id == change.entryId && workspaceId == change.workspaceId &&
        runCatching {
            OffsetDateTime.parse(
                start,
            ).toInstant()
        }.getOrNull() == change.start.truncatedTo(ChronoUnit.SECONDS) &&
        stop?.let {
            runCatching {
                OffsetDateTime.parse(
                    it,
                ).toInstant()
            }.getOrNull()
        } == change.stop?.truncatedTo(ChronoUnit.SECONDS) &&
        (serverDeletedAt != null) == change.shortStop

private fun TrashEntry.matchesStop(change: PendingChange): Boolean = id.value == change.entryId && deletedAt != null &&
    runCatching {
        OffsetDateTime.parse(
            start,
        ).toInstant()
    }.getOrNull() == change.start.truncatedTo(ChronoUnit.SECONDS) &&
    stop?.let {
        runCatching {
            OffsetDateTime.parse(
                it,
            ).toInstant()
        }.getOrNull()
    } == change.stop?.truncatedTo(ChronoUnit.SECONDS)
