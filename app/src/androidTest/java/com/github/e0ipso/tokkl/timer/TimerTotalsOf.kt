package com.github.e0ipso.tokkl.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock

/**
 * The Today and This week row for [entries] in [settings], ticking on [clock] through
 * [timerTotals], as the Timer page's view model ticks it for the page.
 */
@Composable
fun TimerTotalsOf(
    entries: Flow<List<TimeEntry>>,
    settings: StateFlow<AccountSettings?>,
    clock: Clock = Clock.systemUTC(),
) {
    val totals = remember(entries, settings, clock) { timerTotals(entries, settings, clock) }
    val state by totals.collectAsStateWithLifecycle(TimerTotalsState.Hidden)
    TimerTotals(state)
}
