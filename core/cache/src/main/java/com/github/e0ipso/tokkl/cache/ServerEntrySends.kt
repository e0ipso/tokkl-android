package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TimeEntryBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.WorkspaceId
import java.time.Instant

/**
 * The server calls that send the queue's changes for one signed-in account over its [api], and the
 * reads that settle a change whose answer was lost or refused: the entries of `/api/v9`, Tokkl's
 * restore and its trash. [ChangeSender] and [DeleteUndoer] decide what to send.
 */
class ServerEntrySends(private val api: TokklApi) {
    suspend fun create(workspaceId: WorkspaceId, body: TimeEntryBody): ApiResult<ServerTimeEntry> =
        api.entries.create(workspaceId, body)

    suspend fun update(workspaceId: WorkspaceId, id: EntryId, body: TimeEntryBody): ApiResult<ServerTimeEntry> =
        api.entries.update(workspaceId, id, body)

    suspend fun stop(workspaceId: WorkspaceId, id: EntryId, stop: Instant): ApiResult<ServerTimeEntry> =
        api.entries.stop(workspaceId, id, stop)

    suspend fun delete(workspaceId: WorkspaceId, id: EntryId): ApiResult<Unit> = api.entries.delete(workspaceId, id)

    /**
     * Restores the deleted entry [id]. With [resend] false, a request whose connection drops goes
     * out once, as the queue's changes do.
     */
    suspend fun restore(id: EntryId, resend: Boolean): ApiResult<ServerTimeEntry> = api.entries.restore(id, resend)

    /** The running entry, or null when none runs. */
    suspend fun current(): ApiResult<ServerTimeEntry?> = api.entries.current()

    suspend fun get(id: EntryId): ApiResult<ServerTimeEntry> = api.entries.get(id)

    /** The entries that start from [startDate] to [endDate]. */
    suspend fun list(startDate: Instant, endDate: Instant): ApiResult<List<ServerTimeEntry>> =
        api.entries.list(startDate, endDate)

    /** Tokkl's deleted entries. */
    suspend fun trash(): ApiResult<List<TrashEntry>> = api.trash.list()
}
