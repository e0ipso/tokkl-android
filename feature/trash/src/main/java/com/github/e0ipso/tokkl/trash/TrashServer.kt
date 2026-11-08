package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.TrashEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** How many entries a purge removed for good. */
@Serializable
data class Purged(val deleted: Int = 0)

/**
 * Tokkl's trash calls on the configured server, outside the Toggl API (docs/PRD.md section 6).
 * The trash belongs to the user, not to a workspace. Each call suspends while its request runs.
 */
class TrashServer(private val api: TokklApi) {

    /** The deleted entries, the most recently deleted first, at most [LIST_LIMIT] of them. */
    suspend fun list(): ApiResult<List<TrashEntry>> = api.trash.list(ListSerializer(TrashEntry.serializer()))

    /** Puts entry [id] back among the time entries. Tokkl answers with the entry. */
    suspend fun restore(id: Long): ApiResult<Unit> = api.trash.restore(id)

    /** Deletes entry [id] for good. A 404 means it is no longer in the trash. */
    suspend fun purge(id: Long): ApiResult<Purged> = api.trash.purge(id, Purged.serializer())

    /** Deletes every entry in the trash for good. */
    suspend fun purgeAll(): ApiResult<Purged> = api.trash.purgeAll(Purged.serializer())

    companion object {
        /** Tokkl lists no more than this many deleted entries. */
        const val LIST_LIMIT = 1000
    }
}
