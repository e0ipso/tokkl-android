package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.session.Me
import java.time.Clock
import kotlinx.coroutines.flow.Flow

/**
 * What the app last loaded from the server, readable offline (docs/PRD.md O2): projects, clients,
 * tags, tasks, favorites and about two weeks of history. Screens read the flows, which come from
 * the phone and never wait for the network. [refresh] replaces the rows with the server's values.
 *
 * The account settings are not cached here. AccountSettingsSource keeps them and stays their one
 * source of truth.
 */
class AccountCache(private val database: CacheDatabase, private val clock: Clock = Clock.systemUTC()) {

    private val dao = database.cache()

    /** The cached history, newest entry first. */
    val timeEntries: Flow<List<CachedTimeEntry>> = dao.timeEntries()
    val projects: Flow<List<CachedProject>> = dao.projects()
    val clients: Flow<List<CachedClient>> = dao.clients()
    val tags: Flow<List<CachedTag>> = dao.tags()
    val tasks: Flow<List<CachedTask>> = dao.tasks()

    /** In the user's order. */
    val favorites: Flow<List<CachedFavorite>> = dao.favorites()

    /**
     * Reads the server with [api] and replaces the cached rows, as [refreshedWith] describes.
     * [me] is the refresh's `GET /api/v9/me` with related data, and [capabilities] the flags it
     * set. A server without favorites gets none cached. A failed request keeps its rows, and a
     * failed write keeps the whole cache as it was. Blocks, so call it off the main thread.
     */
    fun refresh(api: TogglApiClient, me: Me, capabilities: Capabilities) {
        val snapshot = readServerSnapshot(api, me, clock, favorites = capabilities.favorites)
        try {
            dao.refresh { it.refreshedWith(snapshot) }
        } catch (e: SQLiteException) {
            // Keeps the cache until the next refresh.
        }
    }

    /**
     * Deletes every cached row. Signing out calls this, together with clearing the stored
     * session and settings. Blocks, so call it off the main thread.
     */
    fun clear() {
        database.clearAllTables()
    }
}
