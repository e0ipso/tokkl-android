package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.plannedEnd
import com.github.e0ipso.tokkl.time.transitionKind
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session-only inputs. Editing these never changes the account's defaults. */
data class PomodoroLengths(
    val work: String,
    val shortRest: String,
    val longRest: String,
    val every: String,
) {
    fun config(): PomodoroSessionConfig? {
        val work = work.toIntOrNull()?.takeIf { it in PomodoroDefaults.WORK_MINUTES } ?: return null
        val short = shortRest.toIntOrNull()?.takeIf { it in PomodoroDefaults.SHORT_REST_MINUTES } ?: return null
        val long = longRest.toIntOrNull()?.takeIf { it in PomodoroDefaults.LONG_REST_MINUTES } ?: return null
        val every = every.toIntOrNull()?.takeIf { it in PomodoroDefaults.LONG_REST_EVERY } ?: return null
        return PomodoroSessionConfig(work.toDouble(), short.toDouble(), long.toDouble(), every.toDouble())
    }

    companion object {
        fun from(defaults: PomodoroDefaults) = PomodoroLengths(
            defaults.workMinutes.toString(), defaults.shortRestMinutes.toString(),
            defaults.longRestMinutes.toString(), defaults.longRestEvery.toString(),
        )
    }
}

enum class PomodoroAction { LOAD, START, FIELDS, NEXT, END }

data class PomodoroSaveProblem(val action: PomodoroAction, val reason: String?)

enum class PomodoroNotice { SAVED_ON_PHONE, TOO_SHORT }

data class PomodoroPageState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val lengths: PomodoroLengths = PomodoroLengths.from(PomodoroDefaults.DEFAULT),
    val fields: EntryFields = EntryFields(),
    val session: PomodoroSession? = null,
    val account: AccountSettings? = null,
    val alarmDenied: Boolean = false,
    val fullScreenDenied: Boolean = false,
    val notificationsDenied: Boolean = false,
    val unsentCount: Int? = null,
    val refused: List<PendingChange> = emptyList(),
    val notice: PomodoroNotice? = null,
    val problem: PomodoroSaveProblem? = null,
) {
    val workspaceId: Long? get() = session?.workspaceId ?: account?.defaultWorkspaceId
    val canStart: Boolean get() = !loading && !busy && workspaceId != null && lengths.config() != null
}

/**
 * Commands use the existing transactional store. Field writes and phase changes share a mutex;
 * a failed command reloads the durable row before another tap can repeat a completed transition.
 * The queue's count and refusals are observed independently, including after End and a reload.
 * All public methods are called on the main thread, as for the other screen models.
 */
class PomodoroScreenModel(
    private val store: PomodoroSessionStore,
    private val defaults: StateFlow<PomodoroDefaults>,
    settings: StateFlow<AccountSettings?>,
    unsent: Flow<Int>,
    refused: Flow<List<PendingChange>>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(
        PomodoroPageState(lengths = PomodoroLengths.from(defaults.value), account = settings.value),
    )
    val state: StateFlow<PomodoroPageState> = mutableState.asStateFlow()
    private val commands = Mutex()
    private var lengthsEdited = false
    private var fieldsRevision = 0L
    private var storedFieldsRevision = 0L
    private var exactAlarmAllowed: Boolean? = null

    init {
        scope.launch { defaults.collect { value ->
            if (!lengthsEdited) mutableState.update { it.copy(lengths = PomodoroLengths.from(value)) }
        } }
        scope.launch { settings.collect { account -> mutableState.update { it.copy(account = account) } } }
        scope.launch { unsent.collect { count -> mutableState.update { it.copy(unsentCount = count) } } }
        scope.launch { refused.collect { changes -> mutableState.update { it.copy(refused = changes) } } }
    }

    /** Refreshed on resume after returning from full-screen special app access settings. */
    fun fullScreenPermissionChanged(allowed: Boolean) {
        mutableState.update { it.copy(fullScreenDenied = !allowed) }
    }

    /** Permission changes update notices only; a late grant never restarts a session. */
    internal fun permissionsChanged(grants: PomodoroPermissions) {
        exactAlarmAllowed = grants.exactAlarm
        mutableState.update {
            it.copy(notificationsDenied = !grants.notifications, alarmDenied = !grants.exactAlarm, fullScreenDenied = !grants.fullScreen)
        }
    }

    private fun alarmDenied(): Boolean = exactAlarmAllowed?.not() ?: store.exactAlarmPermissionDenied()

    /** Called on opening and resuming the page; never polls or sends a request. */
    fun reload(): Job = scope.launch {
        commands.withLock {
            try {
                val revision = fieldsRevision
                val current = store.current()
                val ended = state.value.session != null && current == null
                if (ended) lengthsEdited = false
                mutableState.update {
                    it.copy(
                        loading = false, session = current,
                        lengths = if (ended) PomodoroLengths.from(defaults.value) else it.lengths,
                        fields = if (revision == fieldsRevision && fieldsRevision == storedFieldsRevision) current?.fields ?: it.fields else it.fields,
                        alarmDenied = alarmDenied(),
                        problem = it.problem?.takeUnless { problem -> problem.action == PomodoroAction.LOAD },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = true, problem = PomodoroSaveProblem(PomodoroAction.LOAD, e.message)) }
            }
        }
    }

    fun editLengths(lengths: PomodoroLengths) {
        if (state.value.loading || state.value.busy || state.value.session != null) return
        lengthsEdited = true
        mutableState.update { it.copy(lengths = lengths) }
    }

    /** Writes fields only. It never finishes a phase or revisits a queued entry. */
    fun editFields(fields: EntryFields): Job? {
        if (state.value.loading || state.value.busy || state.value.workspaceId == null) return null
        fieldsRevision++
        mutableState.update { it.copy(fields = fields) }
        if (state.value.session == null) return null
        return scope.launch {
            commands.withLock {
                try {
                    val revision = fieldsRevision
                    val current = store.updateFields(state.value.fields)
                    storedFieldsRevision = revision
                    mutableState.update { it.copy(session = current, problem = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableState.update { it.copy(problem = PomodoroSaveProblem(PomodoroAction.FIELDS, e.message)) }
                }
            }
        }
    }

    fun start(onStarted: () -> Unit = {}): Job? {
        val before = state.value
        if (!before.canStart || before.session != null) return null
        val lengths = before.lengths.config() ?: return null
        val workspace = before.workspaceId ?: return null
        return command(PomodoroAction.START) {
            val current = store.start(lengths, before.fields, workspace)
            storedFieldsRevision = fieldsRevision
            mutableState.update { it.copy(session = current, fields = current.fields) }
            // The session is committed before requesting anything; no grant is awaited.
            onStarted()
        }
    }

    fun next(): Job? = finish(PomodoroAction.NEXT, store::nextPhase)

    fun end(): Job? = finish(PomodoroAction.END, store::end)

    private fun finish(action: PomodoroAction, step: suspend () -> PomodoroStep): Job? {
        if (state.value.session == null || state.value.loading || state.value.busy) return null
        return command(action) {
            // Also retries a failed field write before freezing the entry. A failure stops here.
            store.updateFields(state.value.fields)
            storedFieldsRevision = fieldsRevision
            val result = step()
            val current = when (result) {
                PomodoroStep.Idle -> null
                is PomodoroStep.Running -> result.session
                is PomodoroStep.Saved -> result.session
                is PomodoroStep.TooShort -> result.session
            }
            val notice = when (result) {
                is PomodoroStep.Saved -> PomodoroNotice.SAVED_ON_PHONE
                is PomodoroStep.TooShort -> PomodoroNotice.TOO_SHORT
                else -> null
            }
            if (current == null) lengthsEdited = false
            mutableState.update {
                it.copy(
                    session = current, fields = current?.fields ?: it.fields, notice = notice,
                    lengths = if (current == null) PomodoroLengths.from(defaults.value) else it.lengths,
                )
            }
        }
    }

    private fun command(action: PomodoroAction, block: suspend () -> Unit): Job {
        // Synchronous guard prevents a second tap while the first is waiting for disk.
        mutableState.update { it.copy(busy = true, problem = null, notice = null) }
        return scope.launch {
            commands.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Alarm/sender callbacks can fail after a transaction committed. Read the row
                    // instead of restoring stale state or replaying the just-finished phase.
                    try {
                        val current = store.current()
                        val ended = state.value.session != null && current == null
                        if (ended) lengthsEdited = false
                        mutableState.update {
                            it.copy(session = current, lengths = if (ended) PomodoroLengths.from(defaults.value) else it.lengths)
                        }
                    } catch (read: CancellationException) {
                        throw read
                    } catch (_: Exception) {
                        mutableState.update { it.copy(loading = true) }
                    }
                    mutableState.update { it.copy(problem = PomodoroSaveProblem(action, e.message)) }
                } finally {
                    mutableState.update { it.copy(busy = false, alarmDenied = alarmDenied()) }
                }
            }
        }
    }
}

internal fun PomodoroSession.screenPhase() = SessionPhase(phaseKind, phaseStartedAt, completed)

internal fun PomodoroSession.screenLengths() = PomodoroSessionConfig(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery)

internal fun PomodoroSession.nextKind(): String = when (transitionKind(screenPhase(), screenLengths())) {
    "workToShortRest" -> "shortRest"
    "workToLongRest" -> "longRest"
    else -> "work"
}

/** The displayed number is the work phase that the current rest follows. */
internal fun PomodoroSession.number(): Long = completed.toLong() + if (phaseKind == "work") 1 else 0

data class PomodoroClock(val overtime: Boolean, val seconds: Long)

internal fun PomodoroSession.clockAt(now: Instant): PomodoroClock {
    val remaining = Duration.between(now, plannedEnd(screenPhase(), screenLengths())).toMillis()
    return if (remaining >= 0) PomodoroClock(false, (remaining + 999) / 1000)
    else PomodoroClock(true, -remaining / 1000)
}
