package com.github.e0ipso.tokkl.timer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.entryfields.DescriptionSuggestions
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/**
 * What the Timer page shows around the timer, the history and the calendar: the List or Calendar
 * [view], what the timer's picker and description suggestions offer from the cache, whether the
 * phone is [offline], and the account [settings] that the entry actions' messages format with.
 */
@Immutable
data class TimerPageState(
    val view: TimerView = TimerView.LIST,
    val picker: PickerSource = PickerSource(),
    val suggestions: DescriptionSuggestions = DescriptionSuggestions.NONE,
    val offline: Boolean = false,
    val settings: AccountSettings? = null,
)

/**
 * The Timer page's own state, beside the session's timer, entry actions and timer templates:
 * [state], and the Today and This week [totals], which tick on their own so only their row
 * redraws each second. Both follow the cache while the page reads them.
 *
 * @param entries the cached entries with queued changes applied, newest first.
 */
class TimerPageViewModel(
    private val views: TimerViewSetting,
    entries: Flow<List<TimeEntry>>,
    picker: Flow<PickerSource>,
    settings: StateFlow<AccountSettings?>,
    offline: StateFlow<Boolean>,
    clock: Clock,
    scope: CoroutineScope,
) : ViewModel(scope) {

    // Empty until the cache is read, so the view and the network show without waiting for it.
    private val catalog = combine(entries, picker) { rows, source ->
        source to DescriptionSuggestions.of(rows, source.projects, source.clients)
    }.onStart { emit(PickerSource() to DescriptionSuggestions.NONE) }

    val state: StateFlow<TimerPageState> = combine(
        views.view,
        catalog,
        offline,
        settings,
    ) { view, (source, suggestions), isOffline, account ->
        TimerPageState(view, source, suggestions, isOffline, account)
    }.stateIn(
        viewModelScope,
        whileShown,
        TimerPageState(view = views.view.value, offline = offline.value, settings = settings.value),
    )

    val totals: StateFlow<TimerTotalsState> = timerTotals(entries, settings, clock)
        .stateIn(viewModelScope, whileShown, TimerTotalsState.Hidden)

    fun chooseView(view: TimerView) = views.choose(view)
}
