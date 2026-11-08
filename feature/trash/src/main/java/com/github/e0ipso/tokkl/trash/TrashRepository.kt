package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.catalog.SignedInApi
import com.github.e0ipso.tokkl.model.EntryId

/** The account's trash on a Tokkl server, for the trash page. */
class TrashRepository(client: ApiClientProvider) {

    private val api = SignedInApi(client)

    /** The deleted entries, the most recently deleted first. */
    suspend fun list() = api.call { TrashServer(it).list() }

    /** Puts entry [id] back among the time entries. */
    suspend fun restore(id: EntryId) = api.call { TrashServer(it).restore(id) }

    /** Deletes entry [id] for good. */
    suspend fun purge(id: EntryId) = api.call { TrashServer(it).purge(id) }

    /** Deletes every entry in the trash for good. */
    suspend fun purgeAll() = api.call { TrashServer(it).purgeAll() }
}
