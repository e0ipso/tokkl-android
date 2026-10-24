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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
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
    scope: CoroutineScope,
    private val sessionChanges: Flow<*> = store.notificationChanges,
) {
    private val lifetime = SupervisorJob(scope.coroutineContext[Job])
    private val modelScope = CoroutineScope(scope.coroutineContext + lifetime)
    private var retired = false
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
        modelScope.launch { defaults.collect { value ->
            if (!lengthsEdited) mutableState.update { it.copy(lengths = PomodoroLengths.from(value)) }
        } }
        modelScope.launch { settings.collect { account -> mutableState.update { it.copy(account = account) } } }
        modelScope.launch { unsent.collect { count -> mutableState.update { it.copy(unsentCount = count) } } }
        modelScope.launch { refused.collect { changes -> mutableState.update { it.copy(refused = changes) } } }
    }

    /** Refreshed on resume after returning from full-screen special app access settings. */
    fun fullScreenPermissionChanged(allowed: Boolean) {
        if (retireIfForgotten()) return
        mutableState.update { it.copy(fullScreenDenied = !allowed) }
    }

    /** Permission changes update notices only; a late grant never restarts a session. */
    internal fun permissionsChanged(grants: PomodoroPermissions) {
        if (retireIfForgotten()) return
        exactAlarmAllowed = grants.exactAlarm
        mutableState.update {
            it.copy(notificationsDenied = !grants.notifications, alarmDenied = !grants.exactAlarm, fullScreenDenied = !grants.fullScreen)
        }
    }

    private fun alarmDenied(): Boolean = exactAlarmAllowed?.not() ?: store.exactAlarmPermissionDenied()

    /** Collected by the resumed page. Cancellation owns the read as well as the subscription. */
    suspend fun observeSession() = coroutineScope {
        // The page's lifecycle owns this coroutine, but clearing its ViewModel must stop it
        // too, even before Compose disposes the old page. Remove the hook on every stop.
        val observer = coroutineContext.job
        val completion = lifetime.invokeOnCompletion { observer.cancel() }
        try {
            sessionChanges.takeWhile { !retired && !store.isForgotten }.collect {
                commands.withLock { refreshLocked() }
            }
            if (store.isForgotten) retire()
        } finally {
            completion.dispose()
        }
    }

    /** Called on opening and resuming the page; never polls or sends a request. */
    fun reload(): Job = modelScope.launch { commands.withLock { refreshLocked() } }

    private suspend fun refreshLocked() {
        if (retireIfForgotten()) return
        try {
            val revision = fieldsRevision
            val current = store.current()
            if (retireIfForgotten()) return
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

    private fun retireIfForgotten(): Boolean {
        if (store.isForgotten) retire()
        return retired
    }

    private fun retire() {
        if (retired) return
        retired = true
        lifetime.cancel()
        lengthsEdited = false
        fieldsRevision = 0L
        storedFieldsRevision = 0L
        mutableState.value = PomodoroPageState(
            loading = false, lengths = PomodoroLengths.from(PomodoroDefaults.DEFAULT),
        )
    }

    fun editLengths(lengths: PomodoroLengths) {
        if (retireIfForgotten() || state.value.loading || state.value.busy || state.value.session != null) return
        lengthsEdited = true
        mutableState.update { it.copy(lengths = lengths) }
    }

    /** Writes fields only. It never finishes a phase or revisits a queued entry. */
    fun editFields(fields: EntryFields, expected: PomodoroPhaseKey? = state.value.session?.phaseKey()): Job? {
        if (retireIfForgotten() || state.value.loading || state.value.busy || state.value.workspaceId == null) return null
        // A focus-loss callback can be captured by the same old composition as Next or End.
        if (expected != state.value.session?.phaseKey()) return reload()
        fieldsRevision++
        mutableState.update { it.copy(fields = fields) }
        if (expected == null) return null
        return modelScope.launch {
            commands.withLock {
                try {
                    val revision = fieldsRevision
                    val current = store.updateFieldsIfCurrent(state.value.fields, expected)
                    if (retireIfForgotten()) return@withLock
                    if (current == null) {
                        refreshLocked()
                        return@withLock
                    }
                    storedFieldsRevision = revision
                    mutableState.update { it.copy(session = current, problem = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!retireIfForgotten()) {
                        mutableState.update { it.copy(problem = PomodoroSaveProblem(PomodoroAction.FIELDS, e.message)) }
                    }
                }
            }
        }
    }

    fun start(onStarted: () -> Unit = {}): Job? {
        if (retireIfForgotten()) return null
        val before = state.value
        if (!before.canStart || before.session != null) return null
        val lengths = before.lengths.config() ?: return null
        val workspace = before.workspaceId ?: return null
        return command(PomodoroAction.START) {
            val current = store.start(lengths, before.fields, workspace)
            if (retireIfForgotten()) return@command
            storedFieldsRevision = fieldsRevision
            mutableState.update { it.copy(session = current, fields = current.fields) }
            // The session is committed before requesting anything; no grant is awaited.
            onStarted()
        }
    }

    fun next(expected: PomodoroPhaseKey? = state.value.session?.phaseKey()): Job? =
        finish(PomodoroAction.NEXT, expected, store::nextPhaseIfCurrent)

    fun end(expected: PomodoroPhaseKey? = state.value.session?.phaseKey()): Job? =
        finish(PomodoroAction.END, expected, store::endIfCurrent)

    private fun finish(
        action: PomodoroAction,
        expected: PomodoroPhaseKey?,
        step: suspend (PomodoroPhaseKey) -> PomodoroStep,
    ): Job? {
        if (retireIfForgotten() || expected == null || state.value.loading || state.value.busy) return null
        return command(action) {
            // Retry dirty fields only for the displayed phase. The transition checks again
            // under its transaction lock, including a competing action during this write.
            val revision = fieldsRevision
            val written = store.updateFieldsIfCurrent(state.value.fields, expected)
            if (retireIfForgotten()) return@command
            if (written == null) {
                refreshLocked()
                return@command
            }
            storedFieldsRevision = revision
            val result = step(expected)
            if (retireIfForgotten()) return@command
            // Idle also means a stale identity. Read the actual row instead of showing idle
            // or publishing the result's snapshot after another action has already advanced it.
            refreshLocked()
            val notice = when (result) {
                is PomodoroStep.Saved -> PomodoroNotice.SAVED_ON_PHONE
                is PomodoroStep.TooShort -> PomodoroNotice.TOO_SHORT
                else -> null
            }
            mutableState.update { it.copy(notice = notice) }
        }
    }

    private fun command(action: PomodoroAction, block: suspend () -> Unit): Job {
        // Synchronous guard prevents a second tap while the first is waiting for disk.
        mutableState.update { it.copy(busy = true, problem = null, notice = null) }
        return modelScope.launch {
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
                    if (!retireIfForgotten()) {
                        mutableState.update { it.copy(busy = false, alarmDenied = alarmDenied()) }
                    }
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
