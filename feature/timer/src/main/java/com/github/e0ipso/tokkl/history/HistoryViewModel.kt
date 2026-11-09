package com.github.e0ipso.tokkl.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.entries.HistoryEntries
import com.github.e0ipso.tokkl.entries.HistoryEntriesSource
import com.github.e0ipso.tokkl.entries.OlderPages
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext

/**
 * The history on the Timer page (docs/PRD.md H1): the cached two weeks from the phone, which
 * show offline, and older days that [loadOlder] pages from the configured server,
 * [OlderPages.PAGE_DAYS] at a time, when the list's end shows. Nothing loads on a timer.
 * [entries], the session's one owner of the entries, merges the cache's with the older days and
 * keeps them in memory; this model groups them into days with the names of their projects and
 * tasks.
 *
 * A tap on any entry opens the entry form, also on the older days, whose entries the form edits
 * through the queue as the history holds them. Such an edit sits in the cache until the send and
 * the refresh after it, and the history then keeps showing the edited entry.
 *
 * Call [loadOlder] on the main thread.
 *
 * @param computation where the days are grouped from the entries.
 */
class HistoryViewModel(
    private val entries: HistoryEntriesSource,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    scope: CoroutineScope,
    computation: CoroutineContext,
) : ViewModel(scope) {

    val state: StateFlow<HistoryState> = combine(entries.history, projects, tasks) { shown, projectRows, taskRows ->
        when (shown) {
            HistoryEntries.Loading -> HistoryState.Loading

            is HistoryEntries.NoAccountTime -> HistoryState.NoAccountTime(shown.timeZone)

            is HistoryEntries.Shown -> HistoryState.Shown(
                days = historyDays(
                    shown.current,
                    shown.older,
                    projectRows,
                    taskRows,
                    shown.time,
                    collapse = shown.settings.collapseSimilar,
                    windowStart = shown.firstCached,
                ),
                today = shown.time.today(),
                zone = shown.time.zone,
                settings = shown.settings,
                older = shown.status,
            )
        }
    }
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryState.Loading)

    /** The entry [entryId] as the history last showed it, as [HistoryEntriesSource.entry] keeps it. */
    fun entry(entryId: EntryId): TimeEntry? = entries.entry(entryId)

    /** Loads the days before the oldest day loaded, as [HistoryEntriesSource.loadOlder] does. */
    fun loadOlder() = entries.loadOlder()

    private companion object {
        /** How long the state keeps following the cache after the last screen stops reading it. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
