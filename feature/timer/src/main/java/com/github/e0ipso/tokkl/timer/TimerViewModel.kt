package com.github.e0ipso.tokkl.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/**
 * The timer on the Timer page (docs/PRD.md T1): Start, Pause, Resume, Stop and the running entry's
 * fields, through [TimerCommands] and its [RunningFieldEdits]. Call the tap handlers on the main
 * thread. The running clock counts from [TimerState.Running.start] on the phone and never asks
 * the server (principle 3).
 *
 * @param trace marks each Start and Stop tap until the screen reports with [shown] that it drew
 *   what the tap did.
 * @param pauseChain the server's Pause, Resume and Discard, or null where the app sends none.
 * @param discards the too-short pomodoro whose notice no screen has shown, from the account's
 *   timer state, which a screen takes when it shows it.
 */
class TimerViewModel(
    controls: Flow<TimerControls>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    val accountSettings: StateFlow<AccountSettings?>,
    changes: RunningEntryChanges,
    scope: CoroutineScope,
    clock: Clock,
    trace: TapTrace,
    pauseChain: PauseActions?,
    sender: suspend () -> Unit,
    discards: DiscardNotices,
    timerApi: Flow<Boolean>,
    view: StateFlow<TimerView>,
) : ViewModel(scope) {

    private val notices = TimerNoticeBoard(discards)

    private val sections = TapSections(trace)

    private val commands = TimerCommands(
        controls,
        projects,
        tasks,
        accountSettings,
        timerApi,
        changes,
        pauseChain,
        sender,
        notices,
        sections,
        clock,
        viewModelScope,
    )

    private val edits = commands.edits

    val state: StateFlow<TimerState> = commands.state

    val frame: TimerFrame = TimerFrameSource(
        settledIdle = state.map { (it as? TimerState.Idle)?.authoritative == true },
        // Kept up to date while the view model lives, so the frame's first frame back reads it.
        listView = view.map { it == TimerView.LIST }
            .stateIn(viewModelScope, SharingStarted.Eagerly, view.value == TimerView.LIST),
        starts = commands.starts,
        notices = notices,
        discards = discards,
        scope = viewModelScope,
    )

    fun start() = commands.start()

    /**
     * Tells the view model that the screen has drawn [state], which ends the trace section of a Start
     * or Stop tap once [state] shows what the tap did. Call it on the main thread.
     */
    fun shown(state: TimerState) = sections.shown(state)

    fun continueEntry(source: TimeEntry) = commands.continueEntry(source)

    fun startFrom(fields: EntryFields) = commands.startFrom(fields)

    /** Stops the running entry through the queue, or ends the paused chain on the server. */
    fun stop() = commands.stop()

    fun pause() = commands.pause()

    fun resume() = commands.resume()

    fun isShown(target: TimerTarget): Boolean = commands.isShown(target)

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
    fun editDescription(description: String) = edits.editDescription(description)

    /** Sends a description typed for the running entry when it differs from the cache's. */
    fun descriptionDone() = edits.descriptionDone()

    /**
     * Settles the description and initial Start before opening the shared editor. The caller
     * clears field focus first, so the text field has handed over its latest value. Waiting is
     * for local writes only. Keep the captured id through remapping and never select a replacement
     * just because it has the same start time. Cancellation, including a disposed navigation host,
     * opens nothing. Nothing opens while a Stop or a pause chain action is on its way.
     */
    suspend fun runningForEdit(): RunningEntryIdentity? = edits.runningForEdit()

    fun pick(picked: Picked) = edits.pick(picked)

    fun setBillable(billable: Boolean) = edits.setBillable(billable)

    /**
     * Sets every field to [fields], as a picked description suggestion does: the web app's
     * pickSuggestion in TimerBar.tsx (../tokkl/packages/web) fills the description, project, task,
     * tags and billable, on the idle timer's draft or the running entry.
     */
    fun fill(fields: EntryFields) = edits.fill(fields)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
