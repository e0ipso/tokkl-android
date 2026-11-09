package com.github.e0ipso.tokkl.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timernotice.TimerFrame
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/**
 * The timer on the Timer page (docs/PRD.md T1): Start, Pause, Resume, Stop and the running entry's
 * fields. The taps are [TimerTapActions], the field edits [RunningFieldEdits], the trace of each
 * Start and Stop [TapSections], and what the signed-in frame reads of the timer, with its notices,
 * is [frame].
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
 * @param changes the change queue, which Start, Stop and the field edits go to.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 * @param trace marks each Start and Stop tap until the screen reports with [shown] that it drew
 *   what the tap did.
 * @param pauseChain the server's Pause, Resume and Discard, or null where the app sends none.
 * @param sender sends the queue now and waits for it, so a description typed before Pause
 *   reaches the server before the Pause does.
 * @param discards the too-short pomodoro whose notice no screen has shown, from the account's
 *   timer state, which a screen takes when it shows it.
 * @param timerApi whether the server offers the timer API, which decides whether a timer that
 *   the cache does not show yet offers Pause.
 * @param view the Timer page's view, List or Calendar, as the Timer view setting keeps it.
 */
class TimerViewModel(
    controls: Flow<TimerControls>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    scope: CoroutineScope,
    clock: Clock,
    trace: TapTrace,
    pauseChain: PauseActions?,
    sender: QueueSender,
    discards: DiscardNotices,
    timerApi: Flow<Boolean>,
    view: StateFlow<TimerView>,
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

    private val snapshot = TimerSnapshot(cached, local, accountSettings, pauseOffered)

    private val fieldEdits = RunningFieldEdits(local, cached, changes, viewModelScope, snapshot, catalog)

    private val notices = TimerNoticeBoard(discards)

    private val sections = TapSections(trace)

    private val taps = TimerTapActions(
        local,
        cached,
        snapshot,
        accountSettings,
        changes,
        pauseChain,
        sender,
        fieldEdits,
        notices,
        sections,
        clock,
        viewModelScope,
    )

    /** What the signed-in frame reads of the timer: whether it is idle, the page's view, the starts and the notices. */
    val frame: TimerFrame = TimerFrameSource(
        settledIdle = state.map { (it as? TimerState.Idle)?.authoritative == true },
        // Kept up to date while the view model lives, so the frame's first frame back reads it.
        listView = view.map { it == TimerView.LIST }
            .stateIn(viewModelScope, SharingStarted.Eagerly, view.value == TimerView.LIST),
        starts = taps.starts,
        notices = notices,
        discards = discards,
        scope = viewModelScope,
    )

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() = taps.start()

    /**
     * Tells the view model that the screen has drawn [state], which ends the trace section of a Start
     * or Stop tap once [state] shows what the tap did. Call it on the main thread.
     */
    fun shown(state: TimerState) = sections.shown(state)

    /** Continues [source], as [TimerTapActions.continueEntry] does. */
    fun continueEntry(source: TimeEntry) = taps.continueEntry(source)

    /** Starts a timer with [fields] at once, as a favorite does: [TimerTapActions.startFrom]. */
    fun startFrom(fields: EntryFields) = taps.startFrom(fields)

    /** Stops the running entry through the queue, or ends the paused chain on the server. */
    fun stop() = taps.stop()

    /** Pauses the running entry on the server, after a description still being typed. */
    fun pause() = taps.pause()

    /** Resumes the paused chain on the server, at the revision it was shown at. */
    fun resume() = taps.resume()

    /** A focused control keeps the durable target that supplied its displayed context. */
    fun isShown(target: TimerTarget): Boolean = taps.isShown(target)

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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
