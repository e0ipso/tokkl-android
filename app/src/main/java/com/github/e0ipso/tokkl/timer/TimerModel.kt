package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.entry.withPicked
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the timer shows. */
sealed interface TimerState {

    /** The phone has not read its cache yet. */
    data object Loading : TimerState

    /**
     * No entry runs. Start starts one with [fields] in [workspaceId], the account's default
     * workspace, which is null until the phone has read it from the server. Start waits for it.
     */
    data class Idle(val fields: EntryFields, val labels: EntryLabels, val workspaceId: Long?) : TimerState

    /**
     * An entry runs since [start], with [fields]: the cached entry's, or an edit the cache does not
     * have yet.
     *
     * @property entryId the cached entry's id, or null from a Start tap until the cache shows the entry.
     * @property original the project, task and tags the cache has, which the picker keeps on offer
     *   when they are archived.
     * @property unsent the server has not received every change to the entry.
     */
    data class Running(
        val entryId: Long?,
        val workspaceId: Long,
        val start: Instant,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
        val unsent: Boolean,
    ) : TimerState
}

/**
 * The timer on the Timer page (docs/PRD.md T1): Start, Stop and the running entry's fields. Every
 * tap goes to [changes], the change queue, which changes the cache on the phone and queues the
 * change for the server, so nothing here waits for the network (docs/PRD.md principle 2). The
 * timer shows what the cache's [running] entry says, and from the tap until the cache shows it,
 * what the tap did. Start and Stop taps in between are ignored, so a double tap starts one entry.
 *
 * Start starts the draft that the idle timer holds. The cache keeps one running entry, and a
 * start on the phone stops a cached running entry first, as the server does, so one entry runs
 * at a time, offline too.
 *
 * A pick of project, task or tags, and billable, goes to the queue at once. A description goes
 * once typing ends, with [descriptionDone] or Stop, so the queue does not get a change for every
 * letter. Until the cache has an edit, the timer shows it over the cache's entry, so a refresh
 * cannot take back what is being typed.
 *
 * Call the tap handlers on the main thread. The running clock is the screen's: it counts from
 * [TimerState.Running.start] on the phone and never asks the server (principle 3).
 *
 * @param running the running entry with its send state, as AccountCache.running gives it.
 * @param accountSettings for the default workspace, where Start starts.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 */
class TimerModel(
    running: Flow<EntryWithSendState?>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val cached: StateFlow<CachedRunning> = running
        .map<EntryWithSendState?, CachedRunning> { CachedRunning.Read(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CachedRunning.Unread)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    private val local = MutableStateFlow(Local())

    val state: StateFlow<TimerState> = combine(cached, catalog, accountSettings, local) { running, names, settings, taps ->
        timerState(running, settings?.defaultWorkspaceId, taps).labelled(names)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerState.Loading)

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Idle ?: return
        val workspaceId = shown.workspaceId ?: return
        val starting = Pending.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), shown.fields, workspaceId)
        local.update { it.copy(pending = starting, draft = EntryFields()) }
        // Undispatched, so the queue reads the tap time within the tap's handler.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var queued = false
            try {
                changes.start(workspaceId, starting.fields)
                queued = true
                settle { entry -> entry != null && !entry.start.isBefore(starting.at) }
            } finally {
                local.update { taps ->
                    when {
                        taps.pending != starting -> taps
                        // Nothing started, so the draft comes back.
                        !queued && taps.draft == EntryFields() -> taps.copy(pending = null, draft = starting.fields)
                        else -> taps.copy(pending = null)
                    }
                }
            }
        }
    }

    /** Stops the running entry, with a description still being typed. */
    fun stop() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Running ?: return
        val entryId = shown.entryId ?: return
        val edited = local.value.edit?.takeIf { it.start == shown.start }?.fields
        val stopping = Pending.Stopping(shown.start)
        local.update { it.copy(pending = stopping, edit = null) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.stop(entryId, edited)) settle { entry -> entry?.start != shown.start }
            } finally {
                local.update { if (it.pending == stopping) it.copy(pending = null) else it }
            }
        }
    }

    /** Shows [description] as typed. It goes to the queue with [descriptionDone], a pick or Stop. */
    fun editDescription(description: String) = edit(send = false) { it.copy(description = description) }

    /** Sends a description typed for the running entry, when it differs from the cache's. */
    fun descriptionDone() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Running ?: return
        val edit = local.value.edit?.takeIf { it.start == shown.start && !it.sent } ?: return
        if (edit.fields == cachedRunning()?.fields()) {
            local.update { if (it.edit == edit) it.copy(edit = null) else it }
        } else {
            send(shown, edit.fields)
        }
    }

    /** Sets the project, task and tags, as [EntryFields.withPicked] says. */
    fun pick(picked: Picked) = edit(send = true) { it.withPicked(picked, catalog.value.projects) }

    fun setBillable(billable: Boolean) = edit(send = true) { it.copy(billable = billable) }

    /**
     * Changes the idle timer's draft, or the running entry's fields. While a Start or Stop the cache
     * does not show yet is pending, the running entry's fields stay as they are: its id is not
     * known yet, or it has stopped.
     */
    private fun edit(send: Boolean, change: (EntryFields) -> EntryFields) {
        when (val shown = snapshot()) {
            TimerState.Loading -> Unit
            is TimerState.Idle -> local.update { it.copy(draft = change(it.draft)) }
            is TimerState.Running -> {
                if (local.value.pending != null || shown.entryId == null) return
                val fields = change(shown.fields)
                when {
                    fields == shown.fields -> Unit
                    send -> send(shown, fields)
                    else -> local.update { it.copy(edit = Edit(shown.start, fields, sent = false)) }
                }
            }
        }
    }

    /**
     * Queues [fields] for the running entry, and shows them until the cache has them, or has
     * anything newer than what it had before.
     */
    private fun send(shown: TimerState.Running, fields: EntryFields) {
        val entryId = shown.entryId ?: return
        val before = cachedRunning()?.fields()
        val edit = Edit(shown.start, fields, sent = true)
        local.update { it.copy(edit = edit) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.update(entryId, fields, shown.start, stop = null)) {
                    settle { entry -> entry == null || entry.start != shown.start || entry.fields().let { it == fields || it != before } }
                }
            } finally {
                local.update { if (it.edit == edit) it.copy(edit = null) else it }
            }
        }
    }

    /** Waits until the cache's running entry passes [done], or gives up after [SETTLE_MS]. */
    private suspend fun settle(done: (CachedTimeEntry?) -> Boolean) {
        withTimeoutOrNull(SETTLE_MS) {
            cached.first { it is CachedRunning.Read && done(it.running?.entry) }
        }
    }

    private fun cachedRunning(): CachedTimeEntry? = (cached.value as? CachedRunning.Read)?.running?.entry

    /** The state as of now, without labels, which the tap handlers act on. */
    private fun snapshot(): TimerState = timerState(cached.value, accountSettings.value?.defaultWorkspaceId, local.value)

    private fun timerState(cached: CachedRunning, defaultWorkspaceId: Long?, taps: Local): TimerState {
        if (cached !is CachedRunning.Read) return TimerState.Loading
        val running = cached.running
        val idle = TimerState.Idle(taps.draft, EntryLabels(), defaultWorkspaceId)
        when (val pending = taps.pending) {
            // The cache's entry shows once the start has settled, with an id that Stop can take.
            is Pending.Starting -> return TimerState.Running(
                entryId = null,
                workspaceId = pending.workspaceId,
                start = pending.at,
                fields = pending.fields,
                labels = EntryLabels(),
                original = Picked(),
                unsent = true,
            )
            is Pending.Stopping -> if (running?.entry?.start == pending.start) return idle
            null -> Unit
        }
        val entry = running?.entry ?: return idle
        val cachedFields = entry.fields()
        return TimerState.Running(
            entryId = entry.id,
            workspaceId = entry.workspaceId,
            start = entry.start,
            fields = taps.edit?.takeIf { it.start == entry.start }?.fields ?: cachedFields,
            labels = EntryLabels(),
            original = Picked(cachedFields.projectId, cachedFields.taskId, cachedFields.tags),
            unsent = running.unsent,
        )
    }

    private fun TimerState.labelled(names: Catalog): TimerState = when (this) {
        TimerState.Loading -> this
        is TimerState.Idle -> copy(labels = entryLabels(fields, names.projects, names.tasks))
        is TimerState.Running -> copy(labels = entryLabels(fields, names.projects, names.tasks))
    }

    /** The cache's running entry, once read. */
    private sealed interface CachedRunning {
        data object Unread : CachedRunning

        data class Read(val running: EntryWithSendState?) : CachedRunning
    }

    private data class Catalog(val projects: List<CachedProject> = emptyList(), val tasks: List<CachedTask> = emptyList())

    /** What the taps have done that the cache may not show yet. */
    private data class Local(
        /** What Start starts with. */
        val draft: EntryFields = EntryFields(),
        val edit: Edit? = null,
        val pending: Pending? = null,
    )

    /** [fields] for the running entry that started at [start], shown until the cache has them. [sent] once queued. */
    private data class Edit(val start: Instant, val fields: EntryFields, val sent: Boolean)

    /** A Start or Stop tap that the cache may not show yet. */
    private sealed interface Pending {
        /** Start was tapped at [at], in whole milliseconds as the cache keeps times. */
        data class Starting(val at: Instant, val fields: EntryFields, val workspaceId: Long) : Pending

        /** Stop was tapped for the entry that started at [start]. */
        data class Stopping(val start: Instant) : Pending
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * How long a tap shows what it did while the cache does not: far longer than a write to
         * the phone's database takes, so only a cache that never shows the tap reaches it.
         */
        const val SETTLE_MS = 5_000L
    }
}
