package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.session.MePreferences
import java.time.Clock

/**
 * The refresh of the cache from the server: it reads the server's snapshot and writes it over the
 * rows [AccountCache] reads, with the queued changes on top. The account refresh runs it once per
 * answer.
 */
class ServerCacheRefresh(
    private val database: CacheDatabase,
    private val clock: Clock,
    private val capabilitiesSource: CapabilitiesSource,
) {

    private val dao = database.cache()

    /**
     * Reads the server with [api] and replaces the cached rows, as [refreshedWith] describes, with
     * the queued changes applied on top. Entries changed during the read, or pending when it
     * began, keep their current cached state even if their changes have since left the queue.
     * [me] is the refresh's `GET /api/v9/me` with related data, [preferences] its
     * `GET /api/v9/me/preferences`, null when that failed, and [capabilities] the flags they set.
     * A server without favorites gets none cached. A failed request keeps its rows, and a failed
     * write keeps the whole cache as it was. When Tokkl lists an entry without `all_day`, as
     * [readServerSnapshot] describes, the [capabilitiesSource] learns that the server stores no
     * no-time entries. Blocks on the disk, so call it on the IO dispatcher.
     */
    suspend fun refresh(api: TokklApi, me: Me, preferences: MePreferences?, capabilities: Capabilities) {
        val changes = database.changes()
        val before = try {
            database.runInTransaction<EntryRefreshStart> { dao.entryRefreshStart() }
        } catch (_: SQLiteException) {
            // Without the before-read state, a stale answer could overwrite a completed send.
            return
        }
        val snapshot = readServerSnapshot(
            api,
            me,
            preferences,
            clock,
            favorites = capabilities.favorites,
            onAllDayMissing = capabilitiesSource::allDayMissing,
        )
        try {
            database.runInTransaction {
                // A pending local entry may have been created and then removed during the read.
                // Protect its server ID too, even when no cached row remains to compare.
                val mappedIds = before.pendingIds.filter { it < 0 }.mapNotNull(changes::serverId)
                val protectedBefore = before.copy(pendingIds = before.pendingIds + mappedIds)
                dao.refresh { current, pending -> current.refreshedWith(snapshot, pending, protectedBefore) }
            }
        } catch (_: SQLiteException) {
            // Keeps the cache until the next refresh.
        }
    }
}
