package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.model.EntryId

/**
 * Tokkl's trash calls on the configured server, outside the Toggl API (docs/PRD.md section 6).
 * The trash belongs to the user, not to a workspace. Each call suspends while its request runs.
 */
class TrashServer(private val api: TokklApi) {

    /** The deleted entries, the most recently deleted first, at most [LIST_LIMIT] of them. */
    suspend fun list(): ApiResult<List<TrashEntry>> = api.trash.list()

    /** Puts entry [id] back among the time entries. Tokkl answers with the entry. */
    suspend fun restore(id: EntryId): ApiResult<Unit> = api.trash.restore(id)

    /** Deletes entry [id] for good, with the number of entries removed. A 404: it is no longer in the trash. */
    suspend fun purge(id: EntryId): ApiResult<Int> = api.trash.purge(id).map { it.deleted }

    /** Deletes every entry in the trash for good, with the number of entries it removed. */
    suspend fun purgeAll(): ApiResult<Int> = api.trash.purgeAll().map { it.deleted }

    companion object {
        /** Tokkl lists no more than this many deleted entries. */
        const val LIST_LIMIT = 1000
    }
}
