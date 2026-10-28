package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import com.github.e0ipso.tokkl.timerstate.TimerPomodoroPhase
import com.github.e0ipso.tokkl.timerstate.TimerState
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

internal val T0: Instant = Instant.parse("2026-10-28T08:00:00Z")
internal const val WORKSPACE = 11L
internal const val ENTRY = 501L

/** A state the phone received at [T0] while the server's clock read [offset] later. */
internal fun heldState(
    revision: Long,
    pomodoro: TimerPomodoro? = work(),
    description: String = "Focused work",
    entryId: Long = pomodoro?.entryId ?: ENTRY,
    offset: Duration = Duration.ZERO,
): HeldTimerState {
    val running = pomodoro?.let {
        ServerTimeEntry(id = entryId, workspaceId = WORKSPACE, description = description, start = it.pomodoroStart.toString())
    }
    return HeldTimerState(TimerState(revision, T0.plus(offset), running, pause = null, pomodoro = pomodoro, discardedPomodoro = null), T0)
}

internal fun work(
    number: Int = 1,
    plannedEnd: Instant = T0.plusSeconds(1_500),
    start: Instant = T0,
    entryId: Long = ENTRY,
) = TimerPomodoro(
    workMinutes = 25, shortRestMinutes = 5, longRestMinutes = 15, longRestEvery = 4,
    phase = TimerPomodoroPhase(PhaseKind.WORK, start, completed = number - 1, plannedEnd = plannedEnd),
    upcomingPhase = PhaseKind.SHORT_REST, transition = PhaseTransition.WORK_TO_SHORT_REST,
    number = number, entryId = entryId, pomodoroStart = start,
)

internal fun rest(start: Instant = T0.plusSeconds(1_500)) = TimerPomodoro(
    workMinutes = 25, shortRestMinutes = 5, longRestMinutes = 15, longRestEvery = 4,
    phase = TimerPomodoroPhase(PhaseKind.SHORT_REST, start, completed = 1, plannedEnd = start.plusSeconds(300)),
    upcomingPhase = PhaseKind.WORK, transition = PhaseTransition.SHORT_REST_TO_WORK,
    number = 1, entryId = ENTRY, pomodoroStart = T0,
)

internal fun cachedEntry(description: String = "Focused work", id: Long = ENTRY, start: Instant = T0) =
    CachedTimeEntry(id, WORKSPACE, null, null, description, start, null, false, emptyList(), emptyList())

internal fun running(entry: CachedTimeEntry) =
    TimerControls.Running(entry, unsent = false, accumulatedSeconds = 0, target = TimerTarget.server(entryId = entry.id), pausable = false)

/**
 * A Tokkl server that holds one timer state. An action with the held revision advances it, as the
 * server does; any other revision is refused and the server's state goes to the phone, as a 409 does.
 */
internal class FakeServer(val held: MutableStateFlow<HeldTimerState?>) : SessionActions {
    val calls = CopyOnWriteArrayList<String>()
    var gate: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(Unit)
    var answer: TimerActionResult? = null

    /** What a Done action leaves behind, given the state it acted on. */
    var advance: (HeldTimerState?) -> HeldTimerState? = { current -> heldState((current?.state?.revision ?: 0) + 1, rest()) }

    /** What a read changes on the server's side. */
    var onRead: () -> Unit = {}

    override suspend fun read(): TimerActionResult {
        calls += "read"
        onRead()
        return TimerActionResult.Done
    }

    override suspend fun startSession(workspaceId: Long, fields: EntryFields, lengths: SessionLengths): TimerActionResult {
        calls += "start $workspaceId $fields ${lengths.workMinutes}/${lengths.shortRestMinutes}/${lengths.longRestMinutes}/${lengths.longRestEvery}"
        gate.await()
        return answer ?: TimerActionResult.Done.also { held.value = heldState(revision = 1) }
    }

    override suspend fun nextPhase(revision: Long): TimerActionResult = act("next $revision", revision)

    override suspend fun endSession(revision: Long): TimerActionResult = act("end $revision", revision) { null }

    private suspend fun act(call: String, revision: Long, next: (HeldTimerState?) -> HeldTimerState? = advance): TimerActionResult {
        calls += call
        gate.await()
        answer?.let { return it }
        val current = held.value
        if (current == null || current.state.revision != revision) return TimerActionResult.Refused
        held.value = next(current)
        return TimerActionResult.Done
    }
}

/** The change queue and the cache: an edit of the running entry changes the cached row. */
internal class FakeChanges(private val cache: MutableStateFlow<TimerControls>) : EntryChanges {
    val updates = CopyOnWriteArrayList<Pair<RunningEntryIdentity, EntryFields>>()
    var accepts = true

    override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean {
        updates += identity to fields
        if (!accepts) return false
        val row = (cache.value as? TimerControls.Running)?.entry ?: return true
        cache.value = running(row.copy(description = fields.description, projectId = fields.projectId, taskId = fields.taskId, tags = fields.tags, billable = fields.billable))
        return true
    }

    override suspend fun start(workspaceId: Long, fields: EntryFields): Long = error("the page starts nothing on the phone")
    override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = error("the page stops nothing on the phone")
    override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long = error("no create")
    override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean = error("no update")
    override suspend fun delete(entryId: Long): Boolean = error("no delete")
}

/** A page over a fake server, the cache and the account, running on the calling thread. */
internal class PageFixture(
    initial: HeldTimerState? = null,
    entry: CachedTimeEntry? = null,
    val defaults: MutableStateFlow<PomodoroDefaults> = MutableStateFlow(PomodoroDefaults(40, 8, 20, 3)),
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val held = MutableStateFlow(initial)
    val controls = MutableStateFlow<TimerControls>(entry?.let(::running) ?: TimerControls.Idle)
    val server = FakeServer(held)
    val changes = FakeChanges(controls)
    val settings = MutableStateFlow<AccountSettings?>(AccountSettings("europe/madrid", DayOfWeek.MONDAY, null, defaultWorkspaceId = WORKSPACE))
    val unsent = MutableStateFlow(0)
    val refused = MutableStateFlow<List<PendingChange>>(emptyList())
    val queueSends = CopyOnWriteArrayList<String>()
    var alarmsDenied = false
    val page = PomodoroScreenModel(
        held, controls, server, changes, defaults, settings, unsent, refused, scope,
        sendQueued = { queueSends += "send" },
        alarmProbe = { alarmsDenied },
    )

    fun close() = scope.cancel()
}
