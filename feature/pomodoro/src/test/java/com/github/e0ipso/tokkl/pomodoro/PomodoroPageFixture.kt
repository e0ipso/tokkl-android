package com.github.e0ipso.tokkl.pomodoro

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TimerPomodoro
import com.github.e0ipso.tokkl.api.TimerPomodoroPhase
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.model.PhaseTransition
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.held
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

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
        ServerTimeEntry(
            id = entryId,
            workspaceId = WORKSPACE,
            description = description,
            start = it.pomodoroStart.toString(),
        )
    }
    return TimerState(
        revision,
        T0.plus(offset),
        running,
        pause = null,
        pomodoro = pomodoro,
        discardedPomodoro = null,
    ).held(T0)
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

internal fun cachedEntry(description: String = "Focused work", id: Long = ENTRY, start: Instant = T0) = TimeEntry(
    EntryId(
        id,
    ),
    WorkspaceId(WORKSPACE), null, null, description, EntryTiming.Running(start), false, emptyList(), emptyList(),
)

internal fun running(entry: TimeEntry) = TimerControls.Running(
    entry,
    unsent = false,
    accumulatedSeconds = 0,
    target = TimerTarget.server(entryId = entry.id),
    pausable = false,
)

/**
 * A Tokkl server that holds one timer state. An action with the held revision advances it, as the
 * server does; any other revision is refused and the server's state goes to the phone, as a 409 does.
 */
internal class FakeServer(val held: MutableStateFlow<HeldTimerState?>) : SessionActions {
    val calls = CopyOnWriteArrayList<String>()
    var gate: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(Unit)
    var answer: TimerActionResult? = null

    /** What a Done action leaves behind, given the state it acted on. */
    var advance: (HeldTimerState?) -> HeldTimerState? = { current ->
        heldState(
            (current?.revision ?: 0) + 1,
            rest(),
        )
    }

    /** What a read changes on the server's side. */
    var onRead: () -> Unit = {}

    override suspend fun read(): TimerActionResult {
        calls += "read"
        onRead()
        return TimerActionResult.Done
    }

    override suspend fun startSession(
        workspaceId: WorkspaceId,
        fields: EntryFields,
        lengths: SessionLengths,
    ): TimerActionResult {
        calls +=
            "start $workspaceId $fields ${lengths.workMinutes}/${lengths.shortRestMinutes}/${lengths.longRestMinutes}/${lengths.longRestEvery}"
        gate.await()
        return answer ?: TimerActionResult.Done.also { held.value = heldState(revision = 1) }
    }

    override suspend fun nextPhase(revision: Long): TimerActionResult = act("next $revision", revision)

    override suspend fun endSession(revision: Long): TimerActionResult = act("end $revision", revision) { null }

    private suspend fun act(
        call: String,
        revision: Long,
        next: (HeldTimerState?) -> HeldTimerState? = advance,
    ): TimerActionResult {
        calls += call
        gate.await()
        answer?.let { return it }
        val current = held.value
        if (current == null || current.revision != revision) return TimerActionResult.Refused
        held.value = next(current)
        return TimerActionResult.Done
    }
}

/** The change queue and the cache: an edit of the running entry changes the cached row. */
internal class FakeChanges(private val cache: MutableStateFlow<TimerControls>) : RunningEntryChanges {
    val updates = CopyOnWriteArrayList<Pair<RunningEntryIdentity, EntryFields>>()
    var accepts = true

    /** What the phone's storage throws for each write, or null while it takes them. */
    var refusal: Exception? = null

    override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean {
        refusal?.let { throw it }
        updates += identity to fields
        if (!accepts) return false
        val row = (cache.value as? TimerControls.Running)?.entry ?: return true
        cache.value = running(
            row.copy(
                description = fields.description,
                projectId = fields.projectId,
                taskId = fields.taskId,
                tags = fields.tags,
                billable = fields.billable,
            ),
        )
        return true
    }

    override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId = error(
        "the page starts nothing on the phone",
    )
    override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean = error(
        "the page stops nothing on the phone",
    )

    override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? =
        throw AssertionError("unexpected runningEntry")

    override suspend fun currentId(entryId: EntryId): EntryId = entryId
}

/** A page over a fake server, the cache and the account, running on the calling thread. */
internal class PageFixture(
    initial: HeldTimerState? = null,
    entry: TimeEntry? = null,
    val defaults: MutableStateFlow<PomodoroDefaults> = MutableStateFlow(PomodoroDefaults(40, 8, 20, 3)),
    val saved: SavedStateHandle = SavedStateHandle(),
) {
    /** What escaped the page's coroutines, which on the phone would reach the thread and end the app. */
    val escaped = CopyOnWriteArrayList<Throwable>()
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, thrown -> escaped += thrown },
    )
    val held = MutableStateFlow(initial)
    val controls = MutableStateFlow<TimerControls>(entry?.let(::running) ?: TimerControls.Idle)
    val server = FakeServer(held)
    val changes = FakeChanges(controls)
    val settings = MutableStateFlow<AccountSettings?>(
        AccountSettings("europe/madrid", DayOfWeek.MONDAY, null, defaultWorkspaceId = WorkspaceId(WORKSPACE)),
    )
    val unsent = MutableStateFlow(0)
    val refused = MutableStateFlow<List<HeldChange>>(emptyList())
    val queueSends = CopyOnWriteArrayList<String>()
    var alarmsDenied = false
    val picker = MutableStateFlow(PickerSource())
    val session = PomodoroSessionEdits(held, server, changes, sender = { queueSends += "send" })
    val page = PomodoroViewModel(
        held, controls, session, defaults, settings, unsent, refused, scope,
        exactAlarms = { alarmsDenied },
        picker = picker,
        offline = MutableStateFlow(false),
        saved = saved,
    )

    fun close() = scope.cancel()
}
