package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.signedIn

/**
 * Tokkl's trash calls on the signed-in account's server, outside the Toggl API (docs/PRD.md
 * section 6). The trash belongs to the user, not to a workspace.
 */
class ServerTrashRepository(private val client: ApiClientProvider) {

    /**
     * The deleted entries, the most recently deleted first, at most [LIST_LIMIT] of them, each row
     * as the page shows it ([deleted]).
     */
    suspend fun list(): ApiResult<List<DeletedEntry>> = client.signedIn { api ->
        api.trash.list().map { rows -> rows.map(TrashEntry::deleted) }
    }

    /** Puts entry [id] back among the time entries. Tokkl answers with the entry, which the page does not need. */
    suspend fun restore(id: EntryId): ApiResult<Unit> = client.signedIn { it.trash.restore(id) }

    /** Deletes entry [id] for good, with the number of entries removed. A 404: the entry is no longer in the trash. */
    suspend fun purge(id: EntryId): ApiResult<Int> = client.signedIn { api -> api.trash.purge(id).map { it.deleted } }

    /** Deletes every entry in the trash for good, with the number of entries removed. */
    suspend fun purgeAll(): ApiResult<Int> = client.signedIn { api -> api.trash.purgeAll().map { it.deleted } }

    companion object {
        /** Tokkl lists no more than this many deleted entries. */
        const val LIST_LIMIT = 1000
    }
}
