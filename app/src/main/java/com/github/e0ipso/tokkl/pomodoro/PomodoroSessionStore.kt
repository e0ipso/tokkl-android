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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val ringing: PomodoroRinging = NoPomodoroRinging,
) {
    private val phaseLock = Mutex()
    private val callbacks = Any()
    private val notificationRevision = MutableStateFlow(0L)
    internal val notificationChanges = notificationRevision.asStateFlow()

    /** Read and publish under the phase lock, so a late collector cannot replace a ring. */
    internal suspend fun withNotificationState(use: suspend (PomodoroSession?, Boolean) -> Unit) = withContext(io) {
        phaseLock.withLock {
            val session = if (isForgotten) null else database.current()
            val reached = session != null &&
                phaseEnds.read() == ReachedPhaseEnd(session.phaseKind, session.phaseStartedAt)
            use(session, reached)
        }
    }

    private fun notificationChanged() { notificationRevision.value += 1 }

    /** A forgotten store is never reused by a later account, including by retained page models. */
    @Volatile internal var isForgotten: Boolean = false
        private set

    private fun notifyQueued() = synchronized(callbacks) {
        if (!isForgotten) onQueued()
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) {
        phaseLock.withLock { block() }
    }

    /** The running session, or null when none is. */
    suspend fun current(): PomodoroSession? = locked { if (isForgotten) null else database.current() }

    /**
     * One snapshot of queued changes and the open work span. A rest still owns its preceding
     * unsaved work, but elapsed rest alone never makes too-short work count as another change.
     * A completed span is already in the ordinary queue and is counted there exactly once.
     */
    suspend fun unsavedChangeCount(): Int = locked {
        if (isForgotten) return@locked 0
        val at = now()
        val time = accountTime()
        database.transaction {
            val session = database.current()
            val end = session?.let { if (it.phaseKind == "work") at else it.phaseStartedAt }
            val work = session != null && end != null && end.isAfter(session.pomodoroStartedAt) &&
                snappedPomodoro(session.pomodoroStartedAt, end, time) != null
            database.queuedCount() + if (work) 1 else 0
        }
    }

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
        val (saved, stoppedTimers) = locked {
            check(!isForgotten) { "This account has signed out." }
            val result = database.transaction {
                if (database.current() != null) throw IllegalStateException("A pomodoro session is already running.")
                val stopped = database.stopTimers(session.phaseStartedAt)
                database.save(session)
                session to stopped
            }
            follow(database.current())
            result
        }
        if (stoppedTimers) notifyQueued()
        return saved
    }

    /**
     * Confirms the running phase at the tap time, including a skip before its planned end and a
     * confirm in overtime. A rest that returns to work finishes the pomodoro.
     */
    suspend fun nextPhase(): PomodoroStep = nextPhaseIfCurrent(null)

    /** Notification actions carry the phase they were posted for. A stale action changes nothing. */
    internal suspend fun nextPhaseIfCurrent(expected: PomodoroPhaseKey?): PomodoroStep {
        val at = Instant.ofEpochMilli(clock.instant().toEpochMilli())
        val time = accountTime()
        val result = locked {
            if (isForgotten) return@locked PomodoroStep.Idle
            val step = database.transaction {
                val session = database.current() ?: return@transaction PomodoroStep.Idle
                if (expected != null && session.phaseKey() != expected) return@transaction PomodoroStep.Idle
                val change = advancePhase(session.phase(), at, session.config())
                val closes = change.transition == "shortRestToWork" || change.transition == "longRestToWork"
                if (!closes) {
                    val updated = change.next.stored(session, session.pomodoroStartedAt)
                    database.save(updated)
                    return@transaction PomodoroStep.Running(updated)
                }
                close(session, change.next.startedAt, time, change.next)
            }
            if (step != PomodoroStep.Idle || database.current() == null) follow(database.current())
            step
        }
        if (result is PomodoroStep.Saved) notifyQueued()
        return result
    }

    /** Ends the session at the tap time and finishes the open pomodoro. */
    suspend fun end(): PomodoroStep = endIfCurrent(null)

    internal suspend fun endIfCurrent(expected: PomodoroPhaseKey?): PomodoroStep {
        val at = Instant.ofEpochMilli(clock.instant().toEpochMilli())
        val time = accountTime()
        val result = locked {
            if (isForgotten) return@locked PomodoroStep.Idle
            val step = database.transaction {
                val session = database.current() ?: return@transaction PomodoroStep.Idle
                if (expected != null && session.phaseKey() != expected) return@transaction PomodoroStep.Idle
                require(!at.isBefore(session.phaseStartedAt)) { "Confirm must not be before the phase start" }
                close(session, at, time, next = null)
            }
            if (step != PomodoroStep.Idle || database.current() == null) follow(database.current())
            step
        }
        if (result is PomodoroStep.Saved) notifyQueued()
        return result
    }

    /**
     * Called only inside the queue/cache's existing Room transaction. The fresh row is the
     * authority: two starts, a refresh and Next cannot save the same pomodoro twice. Remote
     * starts can precede the current rest phase, so compare against the open pomodoro's start.
     * No callback, alarm operation or nested transaction may run here.
     */
    internal fun endForTimerInTransaction(at: Instant): PomodoroStep {
        if (isForgotten) return PomodoroStep.Idle
        val session = database.current() ?: return PomodoroStep.Idle
        return close(session, at, accountTime(), next = null)
    }

    /**
     * Reconcile only after the outer transaction committed. Take the phase lock without
     * dispatching: AccountCache's blocking IO caller can also use this through runBlocking
     * without dispatching back onto its own executor. A new session keeps its own alarms.
     * Queue callbacks run after releasing the lock, including callbacks that refresh the cache.
     */
    internal suspend fun timerHandoffCommitted(step: PomodoroStep, notifyQueue: Boolean = true) {
        if (step == PomodoroStep.Idle) return
        try {
            phaseLock.withLock {
                if (!isForgotten && database.current() == null) releaseAlarm()
            }
        } finally {
            if (notifyQueue && step is PomodoroStep.Saved) notifyQueued()
        }
    }

    /**
     * Keeps [fields] for the pomodoro in progress and for later ones. A pomodoro already queued
     * keeps the fields it was queued with.
     */
    suspend fun updateFields(fields: EntryFields): PomodoroSession? = updateFieldsIfCurrent(fields, null)

    /** Page drafts must not overwrite fields belonging to a phase the page never displayed. */
    internal suspend fun updateFieldsIfCurrent(fields: EntryFields, expected: PomodoroPhaseKey?): PomodoroSession? = locked {
        if (isForgotten) return@locked null
        database.transaction {
            val session = database.current() ?: return@transaction null
            if (expected != null && session.phaseKey() != expected) return@transaction null
            val updated = session.copy(fields = fields.distinctTags())
            database.save(updated)
            notificationChanged()
            updated
        }
    }

    /**
     * The phase alarm fired. Records that the running phase has reached its planned end, once
     * [clock] says so. An early delivery is armed again for that end. The phase stays where it is.
     */
    internal suspend fun onPhaseAlarm(expected: PomodoroPhaseKey? = null) {
        locked {
            if (isForgotten) return@locked
            val session = database.current() ?: return@locked
            if (expected != null && session.phaseKey() != expected) return@locked
            val end = plannedEnd(session.phase(), session.config())
            if (now().isBefore(end)) {
                alarms.schedule(end, session.phaseKey())
                return@locked
            }
            ring(session)
        }
    }

    /**
     * Arms the stored phase's alarm again. With no session, nothing is armed. A planned end that
     * has already passed rings at once and leaves that phase in overtime until the user confirms.
     */
    internal suspend fun rearm(deferRingingToAlarm: Boolean = false) {
        locked {
            notificationChanged()
            if (isForgotten) return@locked
            val session = database.current()
            if (session == null) {
                releaseAlarm()
                return@locked
            }
            alarms.watchSession(true)
            val end = plannedEnd(session.phase(), session.config())
            if (!now().isBefore(end)) {
                alarms.cancel()
                if (deferRingingToAlarm) {
                    // API 35+ forbids starting mediaPlayback from BOOT_COMPLETED. Deliver an
                    // immediate user-requested exact alarm instead; its receiver may start it.
                    phaseEnds.write(ReachedPhaseEnd(session.phaseKind, session.phaseStartedAt))
                    alarms.schedule(now(), session.phaseKey())
                } else {
                    ring(session)
                }
            } else {
                ringing.stop()
                phaseEnds.clear()
                alarms.schedule(end, session.phaseKey())
            }
        }
    }

    /**
     * Drops the armed alarm and the record that a phase reached its end, and stops listening for
     * boot and clock changes. The session row is unchanged. Sign-out (TKA-0253) clears that row
     * with the cache and calls this so the alarm does not outlive the account.
     */
    suspend fun disarm() = locked { if (!isForgotten) releaseAlarm() }

    /**
     * Fence account-bound commands before waiting for any in-flight phase/output operation.
     * Delete without saving work, then attempt every local cleanup even if one effect fails.
     * The queue's existing sign-out steps cancel retries, await its sender and clear its rows.
     */
    suspend fun forget() {
        synchronized(callbacks) { isForgotten = true }
        locked {
            try {
                database.transaction { database.delete() }
            } finally {
                releaseAlarm()
            }
        }
    }

    /** Removes the session row and the alarm without queueing an entry. The boot test uses it. */
    internal suspend fun discardRunningSession() {
        locked {
            if (isForgotten) return@locked
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
        require(!at.isBefore(session.pomodoroStartedAt)) { "A timer cannot start before the open pomodoro" }
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
     * until the user confirms. The service loops that transition until Next or End.
     */
    private suspend fun ring(session: PomodoroSession) {
        phaseEnds.write(ReachedPhaseEnd(session.phaseKind, session.phaseStartedAt))
        notificationChanged()
        ringing.start(session.ring())
    }

    /** Revalidates a service start/restart while excluding a concurrent Next/End. */
    internal suspend fun withRingingPhase(
        expected: PomodoroPhaseKey?,
        use: suspend (PomodoroRing) -> Unit,
    ): Boolean = locked {
        val session = currentRinging(expected) ?: return@locked false
        use(session.ring())
        true
    }

    /** Swiping the notification never changes the phase or stops either output. */
    internal suspend fun repostRinging(expected: PomodoroPhaseKey) = locked {
        val session = currentRinging(expected) ?: return@locked
        ringing.start(session.ring())
    }

    private fun currentRinging(expected: PomodoroPhaseKey?): PomodoroSession? {
        if (isForgotten) return null
        val session = database.current() ?: return null
        if (expected != null && session.phaseKey() != expected) return null
        if (phaseEnds.read() != ReachedPhaseEnd(session.phaseKind, session.phaseStartedAt)) return null
        return session.takeUnless { now().isBefore(plannedEnd(it.phase(), it.config())) }
    }

    private suspend fun follow(session: PomodoroSession?) {
        notificationChanged()
        if (isForgotten) return
        if (session == null) {
            releaseAlarm()
            return
        }
        ringing.stop()
        alarms.watchSession(true)
        phaseEnds.clear()
        alarms.cancel()
        alarms.schedule(plannedEnd(session.phase(), session.config()), session.phaseKey())
    }

    private suspend fun releaseAlarm() {
        notificationChanged()
        try {
            ringing.stop()
        } finally {
            try {
                alarms.watchSession(false)
            } finally {
                try {
                    alarms.cancel()
                } finally {
                    phaseEnds.clear()
                }
            }
        }
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
            ringing: PomodoroRinging = NoPomodoroRinging,
        ): PomodoroSessionStore = PomodoroSessionStore(
            RoomPomodoroSessionDatabase(database, queue),
            io,
            clock,
            accountTime,
            onQueued,
            alarms,
            phaseEnds,
            ringing,
        )
    }
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

    /** The ordinary queue, including held or possibly-sent creates. Read inside [transaction]. */
    fun queuedCount(): Int = 0

    /** Queues a stopped create on this thread. The caller is inside [transaction]. */
    fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long

    /** Switch-stop every timer in this transaction. Already paused chains stay paused. */
    fun stopTimers(at: Instant): Boolean = false

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

    override fun queuedCount(): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM pending_changes").use {
            check(it.moveToFirst())
            it.getInt(0)
        }

    override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
        queue.queueStoppedCreate(workspaceId, fields, start, stop)

    override fun stopTimers(at: Instant): Boolean = queue.stopTimersForPomodoro(at)

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
