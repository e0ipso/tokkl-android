package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.SignedInApi

/**
 * The account's trash on a Tokkl server, for the trash page. [ServerTrashRepository] sends the
 * requests; the view model's unit tests stand in for it without a server.
 */
interface TrashRepository {

    /** The deleted entries, the most recently deleted first. */
    suspend fun list(): ApiResult<List<TrashEntry>>

    /** Puts entry [id] back among the time entries. */
    suspend fun restore(id: EntryId): ApiResult<Unit>

    /** Deletes entry [id] for good, with the number of entries removed. */
    suspend fun purge(id: EntryId): ApiResult<Int>

    /** Deletes every entry in the trash for good, with the number of entries removed. */
    suspend fun purgeAll(): ApiResult<Int>
}

/** The trash on the signed-in account's server, through [TrashServer]. */
class ServerTrashRepository(client: ApiClientProvider) : TrashRepository {

    private val api = SignedInApi(client)

    override suspend fun list() = api.call { TrashServer(it).list() }

    override suspend fun restore(id: EntryId) = api.call { TrashServer(it).restore(id) }

    override suspend fun purge(id: EntryId) = api.call { TrashServer(it).purge(id) }

    override suspend fun purgeAll() = api.call { TrashServer(it).purgeAll() }
}
