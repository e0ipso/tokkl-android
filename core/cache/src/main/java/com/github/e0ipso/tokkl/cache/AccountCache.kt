package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.LocalEntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the app last loaded from the server, readable offline (docs/PRD.md O2): projects, clients,
 * tags, tasks, favorites and about two weeks of history. Screens read the flows, which come from
 * the phone and never wait for the network. [ServerCacheRefresh] replaces the rows with the server's
 * values.
 *
 * The account settings are not cached here. AccountSettingsSource keeps them and stays their one
 * source of truth.
 */
class AccountCache(private val database: CacheDatabase) :
    CurrentEntryCache,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_CACHE

    override suspend fun forgetAccount() = clear()

    private val dao = database.cache()

    /** The cached history, newest entry first. */
    val timeEntries: Flow<List<TimeEntry>> = dao.timeEntries().map { rows -> rows.map { it.toModel() } }

    /** The cached history, newest entry first, each marked unsent while a change to it is queued. */
    val entries: Flow<List<EntryWithSendState>> = dao.timeEntriesWithSendState().map { rows ->
        rows.map { it.toModel() }
    }

    /**
     * The running entry, or null when none runs. The notification, widget and tile read it, so a
     * queued Start or Stop shows at once.
     */
    val running: Flow<EntryWithSendState?> = dao.runningEntry().map { it?.toModel() }
    val projects: Flow<List<Project>> = dao.projects().map { rows -> rows.map { it.toModel() } }
    val clients: Flow<List<Client>> = dao.clients().map { rows -> rows.map { it.toModel() } }
    val tags: Flow<List<Tag>> = dao.tags().map { rows -> rows.map { it.toModel() } }
    val tasks: Flow<List<Task>> = dao.tasks().map { rows -> rows.map { it.toModel() } }

    /** In the user's order. */
    val favorites: Flow<List<Favorite>> = dao.favorites().map { rows -> rows.map { it.toModel() } }

    /** One item at once, and one after each change to the cached entries or to the queue. */
    val entriesOrQueueChanged: Flow<Unit> =
        database.invalidationTracker.createFlow("time_entries", "pending_changes").map { }

    /** One item at once, and one after each change to which server entry each phone entry is ([serverIdsOf]). */
    val entryIdsChanged: Flow<Unit> = database.invalidationTracker.createFlow("entry_ids").map { }

    /**
     * Replaces the cached favorites with [favorites], in the user's order, once the server has
     * stored them. Blocks, so call it off the main thread.
     */
    fun replaceFavorites(favorites: List<Favorite>) {
        dao.replaceFavorites(
            favorites.mapIndexed { position, favorite -> favorite.copy(position = position).toCached() },
        )
    }

    /**
     * The server ids that replaced the local ids of [entries], for the entries the phone created
     * whose creates the server has answered. Blocks on disk I/O.
     */
    fun serverIdsOf(entries: List<TimeEntry>): Map<LocalEntryId, EntryId> {
        val changes = database.changes()
        return entries.mapNotNull { it.id.local }.mapNotNull { local ->
            changes.serverId(local.value)?.let { local to EntryId(it) }
        }.toMap()
    }

    /** The running entry that started last, as [running] shows it, read now. Blocks on disk I/O. */
    fun runningNow(): EntryWithSendState? = database.changes().latestRunning()?.toModel()

    /**
     * The cached running entries and the ids with queued changes, read together, as a running
     * entry check reads them before its request. Blocks on disk I/O.
     */
    override fun runningCheckStart(): RunningCheckStart {
        val changes = database.changes()
        return database.runInTransaction<RunningCheckStart> {
            RunningCheckStart(
                changes.runningEntries().map { it.toModel() },
                changes.pendingEntryIds().mapTo(HashSet(), ::EntryId),
            )
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
    override fun checkedRunning(before: RunningCheckStart, server: TimeEntry?): RunningCheckWrites? {
        val changes = database.changes()
        return try {
            database.runInTransaction<RunningCheckWrites> {
                // A pending local start may have been answered and remapped during the read.
                val mappedIds = before.pendingIds.mapNotNull { it.local }
                    .mapNotNull { changes.serverId(it.value) }
                    .map(::EntryId)
                val writes = runningCheckWrites(
                    before = before.running,
                    now = changes.runningEntries().map { it.toModel() },
                    server = server,
                    unsent = { changes.countFor(it.value) > 0 },
                    cached = { changes.entry(it.value)?.toModel() },
                    pendingAtStart = before.pendingIds + mappedIds,
                )
                writes.delete.forEach { changes.deleteEntry(it.value) }
                writes.upsert?.let { changes.upsertEntry(it.toCached()) }
                writes
            }
        } catch (_: SQLiteException) {
            // Keeps the cache until the next check.
            null
        }
    }

    /**
     * Deletes every cached row and the queue of unsent changes. Signing out calls this, before it
     * clears what follows the cache. Blocks, so call it off the main thread.
     */
    fun clear() {
        database.clearAllTables()
    }
}

/**
 * What a running entry check read before its request: the [running] entries and the
 * [pendingIds] of the entries the queue held changes to.
 */
class RunningCheckStart(val running: List<TimeEntry>, val pendingIds: Set<EntryId> = emptySet())

/**
 * The cache's current-entry reconciliation, for every server answer that names the running entry:
 * the running entry check, the tile's refresh and the timer state. Read [runningCheckStart] before
 * the request, then hand its answer to [checkedRunning]. [AccountCache] implements it.
 */
interface CurrentEntryCache {
    fun runningCheckStart(): RunningCheckStart

    fun checkedRunning(before: RunningCheckStart, server: TimeEntry?): RunningCheckWrites?
}
