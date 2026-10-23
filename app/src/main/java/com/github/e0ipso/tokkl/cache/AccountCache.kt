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
class AccountCache(private val database: CacheDatabase, private val clock: Clock = Clock.systemUTC()) {

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
     * the queued changes applied on top.
     * [me] is the refresh's `GET /api/v9/me` with related data, [preferences] its
     * `GET /api/v9/me/preferences`, null when that failed, and [capabilities] the flags they set.
     * A server without favorites gets none cached. A failed request keeps its rows, and a failed
     * write keeps the whole cache as it was. Read history ends a pause chain whose timer changed
     * elsewhere, as [ChangeDao.reconcilePauseChain] describes. Blocks, so call it off the main
     * thread.
     */
    fun refresh(api: TogglApiClient, me: Me, preferences: MePreferences?, capabilities: Capabilities) {
        val changes = database.changes()
        val mark = try {
            changes.chainMark()
        } catch (e: SQLiteException) {
            null
        }
        val snapshot = readServerSnapshot(api, me, preferences, clock, favorites = capabilities.favorites)
        try {
            database.runInTransaction {
                dao.refresh { current, pending -> current.refreshedWith(snapshot, pending) }
                if (snapshot.timeEntries != null) changes.reconcilePauseChain(mark)
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
     * The cached running entries and the pause chain, as a running entry check reads them before
     * its request. Blocks on disk I/O.
     */
    fun runningCheckStart(): RunningCheckStart {
        val changes = database.changes()
        return database.runInTransaction<RunningCheckStart> { RunningCheckStart(changes.runningEntries(), changes.chainMark()) }
    }

    /**
     * Writes a running entry check's answer, [server], the entry the server runs or null when none
     * runs, read after the cache held what [before] holds, in one transaction, as
     * [runningCheckWrites] describes: entries with unsent changes, and rows written since
     * [before], stay as they are. A pause chain whose timer changed elsewhere ends, as
     * [ChangeDao.reconcilePauseChain] describes. A failed write keeps the cache for the next
     * check. Blocks on disk I/O.
     */
    fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?) {
        val changes = database.changes()
        try {
            database.runInTransaction {
                val writes = runningCheckWrites(
                    before = before.running,
                    now = changes.runningEntries(),
                    server = server,
                    unsent = { changes.countFor(it) > 0 },
                    cached = changes::entry,
                )
                writes.delete.forEach(changes::deleteEntry)
                writes.upsert?.let(changes::upsertEntry)
                changes.reconcilePauseChain(before.chain)
            }
        } catch (e: SQLiteException) {
            // Keeps the cache until the next check.
        }
    }

    /**
     * Deletes every cached row, the queue of unsent changes and the pause chain. Signing out calls
     * this, together with clearing the stored session and settings. A new timer session starts,
     * so a timer action shown before cannot act after it. Blocks, so call it off the main thread.
     */
    fun clear() {
        database.clearAllTables()
        database.changes().ensureTimerSession(TimerSession(id = newTimerId()))
    }
}

/** What a running entry check read before its request: the [running] entries and the [chain]. */
class RunningCheckStart(val running: List<CachedTimeEntry>, val chain: ChainMark?)
