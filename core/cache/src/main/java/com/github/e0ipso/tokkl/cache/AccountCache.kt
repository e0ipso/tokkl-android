package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import androidx.annotation.WorkerThread
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
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * What the app last loaded from the server, readable offline (docs/PRD.md O2): projects, clients,
 * tags, tasks, favorites and about two weeks of history. Screens read the flows, which come from
 * the phone and never wait for the network. [ServerCacheRefresh] replaces the rows with the server's
 * values.
 *
 * The account settings are not cached here. AccountSettingsSource keeps them and stays their one
 * source of truth. Every read and write here is main-safe: the flows read off the main thread, and
 * the suspending calls switch to [io] themselves. What must run under a caller's lock goes through
 * [CurrentEntryCache].
 */
class AccountCache(private val database: CacheDatabase, private val io: CoroutineContext) : AccountDataOwner {

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
     * stored them. Blocks on the disk, so a caller can hold sign-out back until it returns
     * ([com.github.e0ipso.tokkl.session.AccountContext.ifCurrent]): call it off the main thread.
     */
    @WorkerThread
    fun replaceFavorites(favorites: List<Favorite>) {
        dao.replaceFavorites(
            favorites.mapIndexed { position, favorite -> favorite.copy(position = position).toCached() },
        )
    }

    /**
     * The server ids that replaced the local ids of [entries], for the entries the phone created
     * whose creates the server has answered. Reads on [io].
     */
    suspend fun serverIdsOf(entries: List<TimeEntry>): Map<LocalEntryId, EntryId> = withContext(io) {
        val changes = database.changes()
        entries.mapNotNull { it.id.local }.mapNotNull { local ->
            changes.serverId(local.value)?.let { local to EntryId(it) }
        }.toMap()
    }

    /**
     * Deletes every cached row and the queue of unsent changes, on [io]. Signing out calls this,
     * before it clears what follows the cache.
     */
    suspend fun clear() = withContext(io) {
        database.clearAllTables()
    }
}

/**
 * The cache's current-entry reads and writes over Room, for [CurrentEntryCache]'s callers, which
 * run them on a worker thread under their own lock.
 */
class RoomCurrentEntries(private val database: CacheDatabase) : CurrentEntryCache {

    @WorkerThread
    override fun latestRunning(): EntryWithSendState? = database.changes().latestRunning()?.toModel()

    @WorkerThread
    override fun runningCheckStart(): RunningCheckStart {
        val changes = database.changes()
        return database.runInTransaction<RunningCheckStart> {
            RunningCheckStart(
                changes.runningEntries().map { it.toModel() },
                changes.pendingEntryIds().mapTo(HashSet(), ::EntryId),
            )
        }
    }

    @WorkerThread
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
}

/**
 * What a running entry check read before its request: the [running] entries and the
 * [pendingIds] of the entries the queue held changes to.
 */
class RunningCheckStart(val running: List<TimeEntry>, val pendingIds: Set<EntryId> = emptySet())

/**
 * The cache's running entry, read and reconciled where no suspension may come in between: under
 * the timer state holder's lock, in the running entry check's worker and in the tile's refresh.
 * Every call blocks on the disk, so make it on a worker thread. For every server answer that
 * names the running entry, read [runningCheckStart] before the request, then hand its answer to
 * [checkedRunning]. [RoomCurrentEntries] implements it.
 */
interface CurrentEntryCache {

    /** The running entry that started last, as `AccountCache.running` shows it, read now. */
    @WorkerThread
    fun latestRunning(): EntryWithSendState?

    /** The cached running entries and the ids with queued changes, read together before a request. */
    @WorkerThread
    fun runningCheckStart(): RunningCheckStart

    /**
     * Writes a running entry check's answer, [server], the entry the server runs or null when none
     * runs, read after the cache held what [before] holds, in one transaction, as
     * [runningCheckWrites] describes: entries with unsent changes, entries with changes queued at
     * [before] that the sender may have sent since, and rows written since [before], stay as they
     * are. A pending local id that the server has answered since protects its server id too.
     * Returns what it wrote, or null when the write failed and the cache stays for the next check.
     */
    @WorkerThread
    fun checkedRunning(before: RunningCheckStart, server: TimeEntry?): RunningCheckWrites?
}
