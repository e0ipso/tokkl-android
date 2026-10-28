package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.session.Me
import com.github.e0ipso.tokkl.session.MePreferences
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
class AccountCache(
    private val database: CacheDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : CurrentEntryCache {

    private val dao = database.cache()

    /** The cached history, newest entry first. */
    val timeEntries: Flow<List<CachedTimeEntry>> = dao.timeEntries()

    /** The cached history, newest entry first, each marked unsent while a change to it is queued. */
    val entries: Flow<List<EntryWithSendState>> = dao.timeEntriesWithSendState()

    /**
     * The running entry, or null when none runs. The notification, widget and tile read it, so a
     * queued Start or Stop shows at once.
     */
    val running: Flow<EntryWithSendState?> = dao.runningEntry()
    val projects: Flow<List<CachedProject>> = dao.projects()
    val clients: Flow<List<CachedClient>> = dao.clients()
    val tags: Flow<List<CachedTag>> = dao.tags()
    val tasks: Flow<List<CachedTask>> = dao.tasks()

    /** In the user's order. */
    val favorites: Flow<List<CachedFavorite>> = dao.favorites()

    /**
     * Reads the server with [api] and replaces the cached rows, as [refreshedWith] describes, with
     * the queued changes applied on top. Entries changed during the read, or pending when it
     * began, keep their current cached state even if their changes have since left the queue.
     * [me] is the refresh's `GET /api/v9/me` with related data, [preferences] its
     * `GET /api/v9/me/preferences`, null when that failed, and [capabilities] the flags they set.
     * A server without favorites gets none cached. A failed request keeps its rows, and a failed
     * write keeps the whole cache as it was. Blocks, so call it off the main thread.
     */
    fun refresh(api: TogglApiClient, me: Me, preferences: MePreferences?, capabilities: Capabilities) {
        val changes = database.changes()
        val before = try {
            database.runInTransaction<EntryRefreshStart> { dao.entryRefreshStart() }
        } catch (e: SQLiteException) {
            // Without the before-read state, a stale answer could overwrite a completed send.
            return
        }
        val snapshot = readServerSnapshot(api, me, preferences, clock, favorites = capabilities.favorites)
        try {
            database.runInTransaction {
                // A pending local entry may have been created and then removed during the read.
                // Protect its server ID too, even when no cached row remains to compare.
                val mappedIds = before.pendingIds.filter { it < 0 }.mapNotNull(changes::serverId)
                val protectedBefore = before.copy(pendingIds = before.pendingIds + mappedIds)
                dao.refresh { current, pending -> current.refreshedWith(snapshot, pending, protectedBefore) }
            }
        } catch (e: SQLiteException) {
            // Keeps the cache until the next refresh.
        }
    }

    /**
     * Replaces the cached favorites with [favorites], in the user's order, once the server has
     * stored them. Blocks, so call it off the main thread.
     */
    fun replaceFavorites(favorites: List<CachedFavorite>) {
        dao.replaceFavorites(favorites.mapIndexed { position, favorite -> favorite.copy(position = position) })
    }

    /**
     * The cached running entries and the ids with queued changes, read together, as a running
     * entry check reads them before its request. Blocks on disk I/O.
     */
    override fun runningCheckStart(): RunningCheckStart {
        val changes = database.changes()
        return database.runInTransaction<RunningCheckStart> {
            RunningCheckStart(changes.runningEntries(), changes.pendingEntryIds().toSet())
        }
    }

    /**
     * Writes a running entry check's answer, [server], the entry the server runs or null when none
     * runs, read after the cache held what [before] holds, in one transaction, as
     * [runningCheckWrites] describes: entries with unsent changes, entries with changes queued at
     * [before] that the sender may have sent since, and rows written since [before], stay as they
     * are. A pending local id that the server has answered since protects its server id too.
     * Returns what it wrote, or null when the write failed and the cache stays for the next check.
     * Blocks on disk I/O.
     */
    override fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?): RunningCheckWrites? {
        val changes = database.changes()
        return try {
            database.runInTransaction<RunningCheckWrites> {
                // A pending local start may have been answered and remapped during the read.
                val mappedIds = before.pendingIds.filter { it < 0 }.mapNotNull(changes::serverId)
                val writes = runningCheckWrites(
                    before = before.running,
                    now = changes.runningEntries(),
                    server = server,
                    unsent = { changes.countFor(it) > 0 },
                    cached = changes::entry,
                    pendingAtStart = before.pendingIds + mappedIds,
                )
                writes.delete.forEach(changes::deleteEntry)
                writes.upsert?.let(changes::upsertEntry)
                writes
            }
        } catch (e: SQLiteException) {
            // Keeps the cache until the next check.
            null
        }
    }

    /**
     * Deletes every cached row and the queue of unsent changes. Signing out calls this, together
     * with clearing the stored session and settings. Blocks, so call it off the main thread.
     */
    fun clear() {
        database.clearAllTables()
    }
}

/**
 * What a running entry check read before its request: the [running] entries and the
 * [pendingIds] of the entries the queue held changes to.
 */
class RunningCheckStart(
    val running: List<CachedTimeEntry>,
    val pendingIds: Set<Long> = emptySet(),
)

/**
 * The cache's current-entry reconciliation, for every server answer that names the running entry:
 * the running entry check, the tile's refresh and the timer state. Read [runningCheckStart] before
 * the request, then hand its answer to [checkedRunning]. [AccountCache] implements it.
 */
interface CurrentEntryCache {
    fun runningCheckStart(): RunningCheckStart

    fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?): RunningCheckWrites?
}
