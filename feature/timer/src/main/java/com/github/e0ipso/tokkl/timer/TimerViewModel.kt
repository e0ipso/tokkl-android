package com.github.e0ipso.tokkl.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * The timer on the Timer page (docs/PRD.md T1): Start, Pause, Resume, Stop and the running entry's
 * fields.
 *
 * Start, Stop of a running entry, Continue, favorites and field edits go to [changes], the change
 * queue, so they never wait for the network (docs/PRD.md principle 2). From such a tap until the
 * cache shows it, the timer shows what the tap did and ignores other taps, so a double tap starts
 * one entry. A start stops a cached running entry first, as the server does, so one entry runs at
 * a time, offline too. Pause, Resume and Stop while paused belong to the server's pause chain and
 * go to [pauseChain] at once, at the revision the timer was shown at, so a stale tap gets a
 * refusal; until the server's state shows, the timer keeps what it showed and ignores other taps.
 * A refusal or no answer becomes a [notice]. Field edits are [RunningFieldEdits].
 *
 * [local] holds what the taps did that the cache or the server may not show yet, and changes only
 * through the transitions in TimerTaps.kt. The shown [state] is [timerState] of [local] over the
 * cached controls.
 *
 * Call the tap handlers on the main thread. The running clock counts from
 * [TimerState.Running.start] on the phone and never asks the server (principle 3).
 *
 * @param controls the running entry or paused chain, as the app's shared timer controls give them.
 * @param accountSettings for the default workspace, where Start starts, and for the Today and
 *   This week totals.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 * @param trace marks each Start and Stop tap until the screen reports with [shown] that it drew
 *   what the tap did.
 * @param pauseChain the server's Pause, Resume and Discard, or null where the app sends none.
 * @param sendQueued sends the queue now and waits for it, so a description typed before Pause
 *   reaches the server before the Pause does.
 * @param discards the too-short pomodoro whose notice no screen has shown, from the account's
 *   timer state, which a screen takes when it shows it.
 * @param timerApi whether the server offers the timer API, which decides whether a timer that
 *   the cache does not show yet offers Pause.
 */
class TimerViewModel(
    controls: Flow<TimerControls>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    scope: CoroutineScope,
    private val clock: Clock,
    private val trace: TapTrace,
    private val pauseChain: PauseActions?,
    private val sendQueued: suspend () -> Unit,
    discards: DiscardNotices,
    timerApi: Flow<Boolean>,
) : ViewModel(scope) {

    private val local = MutableStateFlow(TimerTaps())

    private val cached: StateFlow<CachedControls> = controls
        .map<TimerControls, CachedControls> { entry ->
            val editId = local.value.edit?.entryId
            CachedControls.Read(entry, editId, editId?.let { changes.currentId(it) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CachedControls.Unread)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    private val queuedStarts = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One item for each timer start made here, Start, Resume, Continue or a favorite, once the
     * queue or the server has it, for the notification permission's question (docs/PRD.md N5).
     * The start never waits for a collector. The latest start waits for a screen collector after
     * a configuration change.
     */
    val starts: SharedFlow<Unit> = queuedStarts.asSharedFlow()

    private val pauseOffered: StateFlow<Boolean> = timerApi.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        false,
    )

    val state: StateFlow<TimerState> = combine(
        cached,
        catalog,
        accountSettings,
        local,
        pauseOffered,
    ) { running, names, settings, taps, offered ->
        timerState(running, settings?.defaultWorkspaceId, taps, offered).labelled(names.projects, names.tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerState.Loading)

    private val fieldEdits = RunningFieldEdits(local, cached, changes, viewModelScope, ::snapshot) {
        catalog.value.projects
    }

    /** The trace section of the last Start or Stop tap, until the screen shows what it did. */
    private var openTap: OpenTap? = null

    private val notices = TimerNoticeBoard(discards)

    /**
     * What the timer's screens should say now, or null: a claimed notice until it has shown, then
     * the latest failed pause chain action, then a too-short pomodoro no screen has shown yet. A
     * screen [claim]s a notice before it shows it and calls [noticeShown] after, so each notice
     * shows on one screen.
     */
    val notice: StateFlow<TimerNotice?> = notices.notices(discards.discardNotice)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Claims [notice] for the screen about to show it, and returns what to show, or null when
     * there is nothing left to show. A too-short pomodoro is recorded as shown here, once, so a
     * replay of its state or a restart does not show it again.
     */
    suspend fun claim(notice: TimerNotice): TimerNotice? = notices.claim(notice)

    /** The screen has shown [notice], or stopped showing it. */
    fun noticeShown(notice: TimerNotice) = notices.shown(notice)

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Idle ?: return
        val workspaceId = shown.workspaceId ?: return
        beginTap(TapTrace.START) { it is TimerState.Running }
        startWith(workspaceId, shown.fields, typed = null)
    }

    /**
     * Tells the view model that the screen has drawn [state], which ends the trace section of a Start
     * or Stop tap once [state] shows what the tap did. Call it on the main thread.
     */
    fun shown(state: TimerState) {
        val tap = openTap ?: return
        if (tap.done(state)) {
            openTap = null
            tap.end()
        }
    }

    /** Opens [section] of the trace until the screen draws a state that is [done]. */
    private fun beginTap(section: String, done: (TimerState) -> Boolean) {
        // A screen that stopped showing the timer never reported the last tap's state.
        openTap?.end()
        openTap = OpenTap(trace.begin(section), done)
    }

    /**
     * Continues [source] (docs/PRD.md T2): starts a timer with its description, project, task,
     * tags and billable, in its workspace, as the web app's Continue does. A running entry stops
     * at the new one's start, with a description still being typed for it. Ignored while the
     * cache is unread, and while a tap the cache or the server does not show yet is pending, so a
     * double tap starts one entry.
     */
    fun continueEntry(source: TimeEntry) {
        if (local.value.pending != null) return
        val typed = when (val shown = snapshot()) {
            TimerState.Loading -> return
            is TimerState.Idle, is TimerState.Paused -> null
            is TimerState.Running -> typedFor(shown)
        }
        startWith(source.workspaceId, source.fields(), typed)
    }

    /**
     * Starts a timer with [fields] at once, as a favorite does (docs/PRD.md T5), in the account's
     * default workspace, through the same start as Continue: a running entry stops at the new
     * one's start, with a description still being typed for it, as the server does for the web
     * app's favorites (TimerBar.tsx in ../tokkl/packages/web). Does nothing until a workspace is
     * known, or while a tap is pending.
     */
    fun startFrom(fields: EntryFields) {
        if (local.value.pending != null) return
        when (val shown = snapshot()) {
            TimerState.Loading -> Unit

            is TimerState.Idle -> shown.workspaceId?.let { startWith(it, fields, typed = null) }

            is TimerState.Running -> startWith(
                accountSettings.value?.defaultWorkspaceId ?: shown.workspaceId,
                fields,
                typedFor(shown),
            )

            is TimerState.Paused -> startWith(
                accountSettings.value?.defaultWorkspaceId ?: shown.workspaceId,
                fields,
                typed = null,
            )
        }
    }

    /** The description still being typed for the running entry [shown], not queued yet. */
    private fun typedFor(shown: TimerState.Running): TypedEdit? =
        local.value.editFor(shown, cached.value)?.takeIf { !it.sent }?.let { edit ->
            shown.entryId?.let { TypedEdit(RunningEntryIdentity(it, shown.workspaceId, shown.start), edit) }
        }

    /**
     * Starts a timer with [fields] in [workspaceId], after queuing the [typed] description of the
     * running entry, which the start then stops. The idle timer's draft makes way for the new entry,
     * and comes back when nothing starts.
     */
    private fun startWith(workspaceId: WorkspaceId, fields: EntryFields, typed: TypedEdit?) {
        val draft = local.value.draft
        val starting = PendingTap.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), fields, workspaceId)
        local.update { it.startTapped(starting, typed = typed != null) }
        // Undispatched, so the queue reads the tap time within the tap's handler.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var queued = false
            try {
                if (typed != null) changes.updateRunning(typed.identity, typed.edit.fields, typed.identity.start)
                starting.entryId.complete(changes.start(workspaceId, starting.fields))
                queued = true
                queuedStarts.tryEmit(Unit)
                cached.settle { it.runningEntry()?.start?.isBefore(starting.at) == false }
            } finally {
                starting.entryId.complete(null)
                local.update { it.startEnded(starting, queued, draft) }
            }
        }
    }

    /**
     * Stops the running entry, with a description still being typed, through the queue. A
     * running clone stops in the same way, and the server ends its chain. While paused, ends the
     * server's chain with Discard.
     */
    fun stop() {
        if (local.value.pending != null) return
        when (val shown = snapshot()) {
            is TimerState.Running -> stopRunning(shown)
            is TimerState.Paused -> discard()
            else -> Unit
        }
    }

    private fun stopRunning(shown: TimerState.Running) {
        val entryId = shown.entryId ?: return
        val edited = local.value.editFor(shown, cached.value)?.fields
        beginTap(TapTrace.STOP) { it is TimerState.Idle }
        val stopping = PendingTap.Stopping(shown.start)
        local.update { it.stopTapped(stopping) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.stop(entryId, edited)) cached.settle { it.runningEntry()?.start != shown.start }
            } finally {
                local.update { it.tapEnded(stopping) }
            }
        }
    }

    /**
     * Pauses the running entry on the server (docs/PRD.md T1). A description still being typed is
     * queued and sent first, so the chain keeps it. The timer shows what it showed until the
     * server's paused state does.
     */
    fun pause() {
        val actions = pauseChain ?: return
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Running ?: return
        val running = cached.value.controls() as? TimerControls.Running ?: return
        if (!running.pausable || shown.entryId != running.entry.id) return
        val typed = local.value.editFor(shown, cached.value)?.takeIf { !it.sent }
        if (typed != null) fieldEdits.send(shown, typed.fields)
        act(running.target, done = { it.target != running.target }) {
            if (typed != null) {
                fieldEdits.awaitWrites()
                sendQueued()
            }
            actions.pause()
        }
    }

    /**
     * Resumes the paused chain on the server (docs/PRD.md T1), at the revision it was shown at:
     * the server starts a clone with the fields kept at Pause, and the clock goes on from the
     * chain's seconds. The paused view stays until the server's clone shows.
     */
    fun resume() {
        val actions = pauseChain ?: return
        if (local.value.pending != null) return
        if (snapshot() !is TimerState.Paused) return
        val paused = cached.value.controls() as? TimerControls.Paused ?: return
        act(paused.target, done = { it.target != paused.target }, onDone = { queuedStarts.tryEmit(Unit) }) {
            actions.resume(paused.target.revision)
        }
    }

    /** Ends the paused chain on the server, at the revision it was shown at. */
    private fun discard() {
        val actions = pauseChain ?: return
        val paused = cached.value.controls() as? TimerControls.Paused ?: return
        beginTap(TapTrace.STOP) { it is TimerState.Idle }
        act(paused.target, done = { it.target != paused.target }) { actions.discard(paused.target.revision) }
    }

    /**
     * Sends a pause chain action for what [target] showed, ignoring other taps until the server
     * answers and its state shows, as [done] tells. A refusal or no answer becomes a [notice].
     */
    private fun act(
        target: TimerTarget,
        done: (TimerControls) -> Boolean,
        onDone: () -> Unit = {},
        send: suspend () -> TimerActionResult,
    ) {
        val acting = PendingTap.Acting(target)
        local.update { it.actionTapped(acting) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = send()
                if (result == TimerActionResult.Done) {
                    onDone()
                    cached.settle(done)
                } else {
                    notices.failed(result)
                }
            } finally {
                local.update { it.tapEnded(acting) }
            }
        }
    }

    /** A focused control keeps the durable target that supplied its displayed context. */
    fun isShown(target: TimerTarget): Boolean = local.value.pending == null && cached.value.controls()?.target == target

    fun pauseShown(target: TimerTarget) {
        if (isShown(target)) pause()
    }
    fun resumeShown(target: TimerTarget) {
        if (isShown(target)) resume()
    }
    fun stopShown(target: TimerTarget) {
        if (isShown(target)) stop()
    }

    /** Reuse the editor's local-write settlement and identity/remap checks. */
    suspend fun runningForEditShown(target: TimerTarget): RunningEntryIdentity? =
        if (isShown(target)) runningForEdit() else null

    /** Shows [description] as typed. It goes to the queue with [descriptionDone], a pick, Pause or Stop. */
    fun editDescription(description: String) = fieldEdits.editDescription(description)

    /** Sends a description typed for the running entry when it differs from the cache's. */
    fun descriptionDone() = fieldEdits.descriptionDone()

    /**
     * Settles the description and initial Start before opening the shared editor. The caller
     * clears field focus first, so the text field has handed over its latest value. Waiting is
     * for local writes only. Keep the captured id through remapping and never select a replacement
     * just because it has the same start time. Cancellation, including a disposed navigation host,
     * opens nothing. Nothing opens while a Stop or a pause chain action is on its way.
     */
    suspend fun runningForEdit(): RunningEntryIdentity? = fieldEdits.runningForEdit()

    /** Sets the project, task and tags, as [EntryFields.withPicked] says. */
    fun pick(picked: Picked) = fieldEdits.pick(picked)

    fun setBillable(billable: Boolean) = fieldEdits.setBillable(billable)

    /**
     * Sets every field to [fields], as a picked description suggestion does: the web app's
     * pickSuggestion in TimerBar.tsx (../tokkl/packages/web) fills the description, project, task,
     * tags and billable, on the idle timer's draft or the running entry.
     */
    fun fill(fields: EntryFields) = fieldEdits.fill(fields)

    /** The state as of now, without labels, which the tap handlers act on. */
    private fun snapshot(): TimerState = timerState(
        cached.value,
        accountSettings.value?.defaultWorkspaceId,
        local.value,
        pauseOffered.value,
    )

    /** A tap's open trace section, which [end] closes once the screen shows a state that is [done]. */
    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)

    private data class Catalog(val projects: List<Project> = emptyList(), val tasks: List<Task> = emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
