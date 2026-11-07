package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.catalog.SignedInApi

/** The account's trash on a Tokkl server, for the trash page. */
interface TrashRepository {

    /** The deleted entries, the most recently deleted first. */
    suspend fun list(): ApiResult<List<TrashEntry>>

    /** Puts entry [id] back among the time entries. */
    suspend fun restore(id: Long): ApiResult<Unit>

    /** Deletes entry [id] for good. */
    suspend fun purge(id: Long): ApiResult<Purged>

    /** Deletes every entry in the trash for good. */
    suspend fun purgeAll(): ApiResult<Purged>
}

/** [TrashRepository] through the signed-in account's [client] and [TrashServer]. */
class ServerTrashRepository(client: ApiClientProvider) : TrashRepository {

    private val api = SignedInApi(client)

    override suspend fun list() = api.call { TrashServer(it).list() }

    override suspend fun restore(id: Long) = api.call { TrashServer(it).restore(id) }

    override suspend fun purge(id: Long) = api.call { TrashServer(it).purge(id) }

    override suspend fun purgeAll() = api.call { TrashServer(it).purgeAll() }
}
