package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroStep
import com.github.e0ipso.tokkl.session.Me
import com.github.e0ipso.tokkl.session.MePreferences
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

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
    private val pomodoro: () -> PomodoroSessionStore? = { null },
) {

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
     * write keeps the whole cache as it was. Read history ends a pause chain whose timer changed
     * elsewhere, as [ChangeDao.reconcilePauseChain] describes. Blocks, so call it off the main
     * thread.
     */
    fun refresh(api: TogglApiClient, me: Me, preferences: MePreferences?, capabilities: Capabilities) {
        val changes = database.changes()
        val (before, mark) = try {
            database.runInTransaction<Pair<EntryRefreshStart, ChainMark?>> {
                dao.entryRefreshStart() to changes.chainMark()
            }
        } catch (e: SQLiteException) {
            // Without the before-read state, a stale answer could overwrite a completed send.
            return
        }
        val snapshot = readServerSnapshot(api, me, preferences, clock, favorites = capabilities.favorites)
        val sessions = pomodoro()
        val handoff = try {
            database.runInTransaction<PomodoroStep> {
                // A pending local entry may have been created and then removed during the read.
                // Protect its server ID too, even when no cached row remains to compare.
                val mappedIds = before.pendingIds.filter { it < 0 }.mapNotNull(changes::serverId)
                val protectedBefore = before.copy(pendingIds = before.pendingIds + mappedIds)
                dao.refresh { current, pending -> current.refreshedWith(snapshot, pending, protectedBefore) }
                if (snapshot.timeEntries != null) changes.reconcilePauseChain(mark)
                endForForeignTimer(sessions, snapshot.timeEntries.orEmpty())
            }
        } catch (e: SQLiteException) {
            // Keeps both the cache and session until the next refresh.
            return
        }
        finishHandoff(sessions, handoff)
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
        val sessions = pomodoro()
        val handoff = try {
            database.runInTransaction<PomodoroStep> {
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
                endForForeignTimer(sessions, listOfNotNull(writes.upsert))
            }
        } catch (e: SQLiteException) {
            // Keeps both the cache and session until the next check.
            return
        }
        finishHandoff(sessions, handoff)
    }

    /** All identity checks and the partial save use the refresh's existing transaction. */
    private fun endForForeignTimer(sessions: PomodoroSessionStore?, entries: List<CachedTimeEntry>): PomodoroStep {
        if (sessions == null) return PomodoroStep.Idle
        val session = database.pomodoroSessions().current() ?: return PomodoroStep.Idle
        val changes = database.changes()
        val uncertainStarts = changes.possiblySentStarts()
        val foreign = foreignPomodoroStart(
            session.pomodoroStartedAt,
            entries,
            cached = changes::entry,
            locallyStarted = { entry ->
                changes.isMapped(entry.id) || changes.countFor(entry.id) > 0 ||
                    uncertainStarts.any { it.mayBeLocalRunning(entry) }
            },
        ) ?: return PomodoroStep.Idle
        return sessions.endForTimerInTransaction(foreign.start)
    }

    /** This public cache API already blocks on IO. Never block while holding Room's transaction. */
    private fun finishHandoff(sessions: PomodoroSessionStore?, step: PomodoroStep) {
        if (step != PomodoroStep.Idle) runBlocking { sessions?.timerHandoffCommitted(step) }
    }

    /**
     * Deletes every cached row, the queue of unsent changes, the pause chain and the pomodoro
     * session. Signing out calls this, together with clearing the stored session and settings.
     * A new timer session starts, so a timer action shown before cannot act after it. Blocks, so
     * call it off the main thread.
     */
    fun clear() {
        database.clearAllTables()
        database.changes().ensureTimerSession(TimerSession(id = newTimerId()))
    }
}

/** What a running entry check read before its request: the [running] entries and the [chain]. */
class RunningCheckStart(val running: List<CachedTimeEntry>, val chain: ChainMark?)

/** Keep stale, locally overlaid, phone-created and already observed starts out of the handoff. */
internal fun foreignPomodoroStart(
    pomodoroStartedAt: Instant,
    refreshed: List<CachedTimeEntry>,
    cached: (Long) -> CachedTimeEntry?,
    locallyStarted: (CachedTimeEntry) -> Boolean,
): CachedTimeEntry? = refreshed.asSequence().filter { row ->
    row.id > 0 && row.stop == null && row.start.isAfter(pomodoroStartedAt) &&
        cached(row.id)?.let { it.stop == null && it.start == row.start } == true && !locallyStarted(row)
}.minByOrNull { it.start }

/** Unanswered running POSTs keep their start, with only server timestamp precision removed. */
internal fun PendingChange.mayBeLocalRunning(entry: CachedTimeEntry): Boolean =
    entry.id !in existingIds && couldHaveMade(
        ServerTimeEntry(
            id = entry.id,
            workspaceId = entry.workspaceId,
            projectId = entry.projectId,
            taskId = entry.taskId,
            description = entry.description,
            start = entry.start.toString(),
            stop = null,
            billable = entry.billable,
            tags = entry.tags,
        ),
        setOf(start, start.truncatedTo(ChronoUnit.SECONDS)),
    )
