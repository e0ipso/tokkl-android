package com.github.e0ipso.tokkl.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.entries.HistoryEntries
import com.github.e0ipso.tokkl.entries.OlderDays
import com.github.e0ipso.tokkl.entries.OlderPages
import com.github.e0ipso.tokkl.entries.SessionEntries
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

/**
 * The history on the Timer page (docs/PRD.md H1): the cached two weeks from the phone, which
 * show offline, and older days that [loadOlder] pages from the configured server,
 * [OlderPages.PAGE_DAYS] at a time, when the list's end shows. Nothing loads on a timer.
 * [entries] merges the cache's entries with the older days; this model groups them into days.
 *
 * A tap on any entry opens the entry form, also on the older days, whose entries the form edits
 * through the queue as the history holds them. Such an edit sits in the cache until the send and
 * the refresh after it, and the history then keeps showing the edited entry. Call [loadOlder] on
 * the main thread.
 */
class HistoryViewModel(
    private val entries: SessionEntries,
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

    fun entry(entryId: EntryId): TimeEntry? = entries.entry(entryId)

    fun loadOlder() = entries.loadOlder()

    private companion object {
        /** How long the state keeps following the cache after the last screen stops reading it. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Immutable
sealed interface HistoryState {

    /** The cache or the account settings are not read yet. */
    data object Loading : HistoryState

    /** The phone cannot resolve the account's timezone, [timeZone], so it cannot tell the days apart. */
    data class NoAccountTime(val timeZone: String) : HistoryState

    /**
     * The [days] with entries, newest first, as of [today] in the account's timezone [zone], and
     * what comes after them.
     */
    data class Shown(
        val days: List<HistoryDay>,
        val today: LocalDate,
        val zone: ZoneId,
        val settings: AccountSettings,
        val older: OlderDays,
    ) : HistoryState
}
