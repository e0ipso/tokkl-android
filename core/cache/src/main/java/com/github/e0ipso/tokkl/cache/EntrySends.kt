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
 * The server calls that send the queue's changes for one signed-in account, and the reads that
 * settle a change whose answer was lost or refused: the entries of `/api/v9`, Tokkl's restore and
 * its trash. [ChangeSender] and [DeleteUndoer] decide what to send; [ServerEntrySends] sends it,
 * and their unit tests answer for it.
 */
interface EntrySends {
    suspend fun create(workspaceId: WorkspaceId, body: TimeEntryBody): ApiResult<ServerTimeEntry>

    suspend fun update(workspaceId: WorkspaceId, id: EntryId, body: TimeEntryBody): ApiResult<ServerTimeEntry>

    suspend fun stop(workspaceId: WorkspaceId, id: EntryId, stop: Instant): ApiResult<ServerTimeEntry>

    suspend fun delete(workspaceId: WorkspaceId, id: EntryId): ApiResult<Unit>

    /**
     * Restores the deleted entry [id]. With [resend] false, a request whose connection drops goes
     * out once, as the queue's changes do.
     */
    suspend fun restore(id: EntryId, resend: Boolean): ApiResult<ServerTimeEntry>

    /** The running entry, or null when none runs. */
    suspend fun current(): ApiResult<ServerTimeEntry?>

    suspend fun get(id: EntryId): ApiResult<ServerTimeEntry>

    /** The entries that start from [startDate] to [endDate]. */
    suspend fun list(startDate: Instant, endDate: Instant): ApiResult<List<ServerTimeEntry>>

    /** Tokkl's deleted entries. */
    suspend fun trash(): ApiResult<List<TrashEntry>>
}

/** [EntrySends] over the signed-in account's [api]. */
class ServerEntrySends(private val api: TokklApi) : EntrySends {
    override suspend fun create(workspaceId: WorkspaceId, body: TimeEntryBody) = api.entries.create(workspaceId, body)

    override suspend fun update(workspaceId: WorkspaceId, id: EntryId, body: TimeEntryBody) =
        api.entries.update(workspaceId, id, body)

    override suspend fun stop(workspaceId: WorkspaceId, id: EntryId, stop: Instant) =
        api.entries.stop(workspaceId, id, stop)

    override suspend fun delete(workspaceId: WorkspaceId, id: EntryId) = api.entries.delete(workspaceId, id)

    override suspend fun restore(id: EntryId, resend: Boolean) = api.entries.restore(id, resend)

    override suspend fun current() = api.entries.current()

    override suspend fun get(id: EntryId) = api.entries.get(id)

    override suspend fun list(startDate: Instant, endDate: Instant) = api.entries.list(startDate, endDate)

    override suspend fun trash() = api.trash.list()
}
