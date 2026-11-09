package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.signedIn

/**
 * The account's trash on a Tokkl server, for the trash page. [ServerTrashRepository] sends the
 * requests; the view model's unit tests stand in for it without a server.
 */
interface TrashRepository {

    /** The deleted entries, the most recently deleted first, at most [LIST_LIMIT] of them. */
    suspend fun list(): ApiResult<List<TrashEntry>>

    /** Puts entry [id] back among the time entries. */
    suspend fun restore(id: EntryId): ApiResult<Unit>

    /** Deletes entry [id] for good, with the number of entries removed. */
    suspend fun purge(id: EntryId): ApiResult<Int>

    /** Deletes every entry in the trash for good, with the number of entries removed. */
    suspend fun purgeAll(): ApiResult<Int>

    companion object {
        /** Tokkl lists no more than this many deleted entries. */
        const val LIST_LIMIT = 1000
    }
}

/**
 * Tokkl's trash calls on the signed-in account's server, outside the Toggl API (docs/PRD.md
 * section 6). The trash belongs to the user, not to a workspace. Each call suspends while its
 * request runs.
 */
class ServerTrashRepository(private val client: ApiClientProvider) : TrashRepository {

    override suspend fun list() = client.signedIn { it.trash.list() }

    /** Tokkl answers with the entry, which the page does not need. */
    override suspend fun restore(id: EntryId) = client.signedIn { it.trash.restore(id) }

    /** A 404: the entry is no longer in the trash. */
    override suspend fun purge(id: EntryId) = client.signedIn { api -> api.trash.purge(id).map { it.deleted } }

    override suspend fun purgeAll() = client.signedIn { api -> api.trash.purgeAll().map { it.deleted } }
}
