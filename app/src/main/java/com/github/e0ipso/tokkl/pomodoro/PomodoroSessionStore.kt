package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.nextPhase as advancePhase
import com.github.e0ipso.tokkl.time.plannedEnd
import com.github.e0ipso.tokkl.time.startSession
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/**
 * What finishing a phase did to the session and the queue. [TooShort] is the result a screen
 * turns into "too short, not saved": the snapped ends met, or the span was under half a step,
 * so nothing was queued.
 */
sealed class PomodoroStep {
    /** No session was running. */
    data object Idle : PomodoroStep()

    /** The phase changed and the pomodoro is still open. */
    data class Running(val session: PomodoroSession) : PomodoroStep()

    /**
     * A stopped entry was queued. [session] is the one still running, or null when the session
     * ended. [entryId] is the create's local id.
     */
    data class Saved(val session: PomodoroSession?, val entryId: Long, val span: SnappedSpan) : PomodoroStep()

    /** The closed pomodoro was not queued. [session] is null when the session ended. */
    data class TooShort(val session: PomodoroSession?) : PomodoroStep()
}

/**
 * The running Pomodoro and the entries it saves. The session row lives in [CacheDatabase].
 * [start], [nextPhase], [end] and [updateFields] follow the clock-free rules in
 * `time/Pomodoro.kt`. A pomodoro runs from a work start until the next work start, or until
 * [end]. Both of those freeze the fields and the snapped span into one ordinary queued create,
 * in the same Room transaction as the session row. A span [AccountTime.snapUnsent] rejects is
 * not queued. The next pomodoro starts at the previous one's unsnapped end.
 *
 * The create is the queue's create path, so a post whose answer was lost is replayed by
 * [com.github.e0ipso.tokkl.cache.PendingChange.maybeSent]. There is no second retry store.
 *
 * Blocks on disk once the call has read [clock], so call it off the main thread. [clock] is the
 * time of the tap, read before the transaction, as the timer reads its own.
 *
 * A phase start arms one exact alarm at that phase's planned end. Next phase and end cancel it
 * and, when a phase continues, arm the next one. The alarm records that the planned end was
 * reached and leaves the phase in place, including when a reboot finds that end already past.
 * Nothing is armed while no session runs.
 *
 * @param accountTime the account's zone and resolution at the tap. Null means the phone cannot
 *   resolve the zone: the times are kept as they are and no zone is invented. A missing or
 *   invalid resolution also keeps the times, as a server without `time_resolution_minutes` does.
 */
class PomodoroSessionStore internal constructor(
    private val database: PomodoroSessionDatabase,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
    private val accountTime: () -> AccountTime?,
    private val onQueued: () -> Unit = {},
    private val alarms: PomodoroAlarmScheduler = IdlePomodoroAlarms,
    private val phaseEnds: PomodoroPhaseEndRecord = MemoryPomodoroPhaseEnds(),
) {

    /** The running session, or null when none is. */
    suspend fun current(): PomodoroSession? = withContext(io) { database.current() }

    /** True when the exact-alarm permission is denied. The Pomodoro page reads this. */
    fun exactAlarmPermissionDenied(): Boolean = alarms.permissionDenied

    /** The phase the alarm has reached, or null when the running phase has not, or none runs. */
    fun reachedPhaseEnd(): ReachedPhaseEnd? = phaseEnds.read()

    /**
     * Starts a session at the tap time with [lengths] and [fields] in [workspaceId]. The lengths
     * use the rules' ranges. Throws when a session is already running, so an open pomodoro is
     * not dropped.
     */
    suspend fun start(lengths: PomodoroSessionConfig, fields: EntryFields, workspaceId: Long): PomodoroSession {
        val at = clock.instant()
        val phase = startSession(at)
        plannedEnd(phase, lengths)
        val session = phase.stored(workspaceId, lengths, fields.distinctTags(), pomodoroStartedAt = phase.startedAt)
        return withContext(io) {
            val saved = database.transaction {
                if (database.current() != null) throw IllegalStateException("A pomodoro session is already running.")
                database.save(session)
                session
            }
            follow(saved)
            saved
        }
    }

    /**
     * Confirms the running phase at the tap time, including a skip before its planned end and a
     * confirm in overtime. A rest that returns to work finishes the pomodoro.
     */
    suspend fun nextPhase(): PomodoroStep {
        val at = Instant.ofEpochMilli(clock.instant().toEpochMilli())
        val time = accountTime()
        val result = withContext(io) {
            val step = database.transaction {
                val session = database.current() ?: return@transaction PomodoroStep.Idle
                val change = advancePhase(session.phase(), at, session.config())
                val closes = change.transition == "shortRestToWork" || change.transition == "longRestToWork"
                if (!closes) {
                    val updated = change.next.stored(session, session.pomodoroStartedAt)
                    database.save(updated)
                    return@transaction PomodoroStep.Running(updated)
                }
                close(session, change.next.startedAt, time, change.next)
            }
            follow(step.keptSession())
            step
        }
        if (result is PomodoroStep.Saved) onQueued()
        return result
    }

    /** Ends the session at the tap time and finishes the open pomodoro. */
    suspend fun end(): PomodoroStep {
        val at = Instant.ofEpochMilli(clock.instant().toEpochMilli())
        val time = accountTime()
        val result = withContext(io) {
            val step = database.transaction {
                val session = database.current() ?: return@transaction PomodoroStep.Idle
                close(session, at, time, next = null)
            }
            follow(step.keptSession())
            step
        }
        if (result is PomodoroStep.Saved) onQueued()
        return result
    }

    /**
     * Keeps [fields] for the pomodoro in progress and for later ones. A pomodoro already queued
     * keeps the fields it was queued with.
     */
    suspend fun updateFields(fields: EntryFields): PomodoroSession? = withContext(io) {
        database.transaction {
            val session = database.current() ?: return@transaction null
            val updated = session.copy(fields = fields.distinctTags())
            database.save(updated)
            updated
        }
    }

    /**
     * The phase alarm fired. Records that the running phase has reached its planned end, once
     * [clock] says so. An early delivery is armed again for that end. The phase stays where it is.
     */
    internal suspend fun onPhaseAlarm() {
        withContext(io) {
            val session = database.current() ?: return@withContext
            val end = plannedEnd(session.phase(), session.config())
            if (now().isBefore(end)) {
                alarms.schedule(end)
                return@withContext
            }
            ring(session)
        }
    }

    /**
     * Arms the stored phase's alarm again. With no session, nothing is armed. A planned end that
     * has already passed rings at once and leaves that phase in overtime until the user confirms.
     */
    internal suspend fun rearm() {
        withContext(io) {
            val session = database.current()
            if (session == null) {
                releaseAlarm()
                return@withContext
            }
            alarms.watchSession(true)
            val end = plannedEnd(session.phase(), session.config())
            if (!now().isBefore(end)) {
                alarms.cancel()
                ring(session)
            } else {
                phaseEnds.clear()
                alarms.schedule(end)
            }
        }
    }

    /**
     * Drops the armed alarm and the record that a phase reached its end, and stops listening for
     * boot and clock changes. The session row is unchanged. Sign-out (TKA-0253) clears that row
     * with the cache and calls this so the alarm does not outlive the account.
     */
    suspend fun disarm() = withContext(io) { releaseAlarm() }

    /** Removes the session row and the alarm without queueing an entry. The boot test uses it. */
    internal suspend fun discardRunningSession() {
        withContext(io) {
            database.transaction { database.delete() }
            releaseAlarm()
        }
    }

    /**
     * Finishes the pomodoro that began at [PomodoroSession.pomodoroStartedAt] and ends at [at].
     * The queued create takes the session's fields as they are now. [next] is the phase that
     * continues, or null when the session ends.
     */
    private fun close(session: PomodoroSession, at: Instant, time: AccountTime?, next: SessionPhase?): PomodoroStep {
        require(!at.isBefore(session.phaseStartedAt)) { "Confirm must not be before the phase start" }
        val span = snappedPomodoro(session.pomodoroStartedAt, at, time)
        val following = next?.stored(session, next.startedAt)
        if (span == null) {
            if (following == null) database.delete() else database.save(following)
            return PomodoroStep.TooShort(following)
        }
        val entryId = database.createStopped(session.workspaceId, session.fields, span.start, span.stop)
        if (following == null) database.delete() else database.save(following)
        return PomodoroStep.Saved(following, entryId, span)
    }

    /** The instant of a check, at the session rules' millisecond precision. */
    private fun now(): Instant = Instant.ofEpochMilli(clock.instant().toEpochMilli())

    /**
     * The phase has reached its planned end. This does not change the phase: overtime continues
     * until the user confirms. TKA-0250 starts the ringing service from here.
     */
    private fun ring(session: PomodoroSession) {
        phaseEnds.write(ReachedPhaseEnd(session.phaseKind, session.phaseStartedAt))
    }

    private fun follow(session: PomodoroSession?) {
        if (session == null) {
            releaseAlarm()
            return
        }
        alarms.watchSession(true)
        phaseEnds.clear()
        alarms.cancel()
        alarms.schedule(plannedEnd(session.phase(), session.config()))
    }

    private fun releaseAlarm() {
        alarms.watchSession(false)
        alarms.cancel()
        phaseEnds.clear()
    }

    companion object {
        /** A store on [database] whose creates go through [queue]. */
        fun open(
            database: CacheDatabase,
            queue: ChangeQueue,
            io: CoroutineContext,
            clock: Clock = Clock.systemUTC(),
            accountTime: () -> AccountTime?,
            onQueued: () -> Unit = {},
            alarms: PomodoroAlarmScheduler,
            phaseEnds: PomodoroPhaseEndRecord,
        ): PomodoroSessionStore = PomodoroSessionStore(
            RoomPomodoroSessionDatabase(database, queue),
            io,
            clock,
            accountTime,
            onQueued,
            alarms,
            phaseEnds,
        )
    }
}

private fun PomodoroStep.keptSession(): PomodoroSession? = when (this) {
    PomodoroStep.Idle -> null
    is PomodoroStep.Running -> session
    is PomodoroStep.Saved -> session
    is PomodoroStep.TooShort -> session
}

/**
 * The span a finished pomodoro stores. [AccountTime.snapUnsent] snaps each end to the nearest
 * grid point, taking the later point on a tie, and returns null when the snapped ends meet or
 * the span is under half a step. Without a resolution the times stay as they are. Without an
 * [AccountTime] they stay as they are too: the phone does not invent a zone.
 */
internal fun snappedPomodoro(start: Instant, end: Instant, time: AccountTime?): SnappedSpan? {
    if (time == null) {
        require(!end.isBefore(start)) { "The stop comes before the start." }
        return SnappedSpan(start, end)
    }
    return time.snapUnsent(start, end)
}

/** The session row and the queue create, committed together by [transaction]. */
internal interface PomodoroSessionDatabase {
    fun current(): PomodoroSession?

    fun save(session: PomodoroSession)

    fun delete()

    /** Queues a stopped create on this thread. The caller is inside [transaction]. */
    fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long

    fun <T> transaction(block: () -> T): T
}

/** [CacheDatabase.runInTransaction] around the session row and [ChangeQueue.queueStoppedCreate]. */
internal class RoomPomodoroSessionDatabase(
    private val database: CacheDatabase,
    private val queue: ChangeQueue,
) : PomodoroSessionDatabase {
    private val dao = database.pomodoroSessions()

    override fun current(): PomodoroSession? = dao.current()

    override fun save(session: PomodoroSession) {
        dao.upsert(session)
    }

    override fun delete() {
        dao.delete()
    }

    override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
        queue.queueStoppedCreate(workspaceId, fields, start, stop)

    override fun <T> transaction(block: () -> T): T = database.runInTransaction { block() }
}

private fun PomodoroSession.phase() = SessionPhase(phaseKind, phaseStartedAt, completed)

private fun PomodoroSession.config() = PomodoroSessionConfig(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery)

private fun SessionPhase.stored(
    session: PomodoroSession,
    pomodoroStartedAt: Instant,
) = session.copy(
    phaseKind = kind,
    phaseStartedAt = startedAt,
    completed = this.completed,
    pomodoroStartedAt = pomodoroStartedAt,
)

private fun SessionPhase.stored(
    workspaceId: Long,
    lengths: PomodoroSessionConfig,
    fields: EntryFields,
    pomodoroStartedAt: Instant,
) = PomodoroSession(
    workspaceId = workspaceId,
    workMinutes = lengths.workMinutes,
    shortRestMinutes = lengths.shortRestMinutes,
    longRestMinutes = lengths.longRestMinutes,
    longRestEvery = lengths.longRestEvery,
    phaseKind = kind,
    phaseStartedAt = startedAt,
    pomodoroStartedAt = pomodoroStartedAt,
    completed = this.completed,
    fields = fields,
)

private fun EntryFields.distinctTags() = copy(tags = tags.distinct())
