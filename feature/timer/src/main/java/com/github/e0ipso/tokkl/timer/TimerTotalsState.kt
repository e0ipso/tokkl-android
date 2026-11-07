package com.github.e0ipso.tokkl.timer

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.nextTotalsDelay
import com.github.e0ipso.tokkl.timerstate.timerPeriodTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import java.time.Instant

/** What the Timer page's Today and This week row shows. */
@Immutable
sealed interface TimerTotalsState {

    /** Nothing: the account settings or the cache have not been read yet. */
    data object Hidden : TimerTotalsState

    /** The phone cannot resolve the account's [timeZone], so there are no days to sum. */
    data class UnknownZone(val timeZone: String) : TimerTotalsState

    /** Both totals, in the account's duration format and in seconds for TalkBack. */
    data class Shown(val today: String, val week: String, val todaySeconds: Long, val weekSeconds: Long) :
        TimerTotalsState
}

/**
 * The row for [entries], the cached rows with queued changes and remaps applied, in [settings]
 * at [now]. The pause chain's cumulative clock is not added, since its segments are rows already.
 * Null [entries] or [settings] have not been read yet.
 */
fun timerTotalsAt(entries: List<CachedTimeEntry>?, settings: AccountSettings?, now: Instant): TimerTotalsState {
    if (entries == null || settings == null) return TimerTotalsState.Hidden
    val totals = timerPeriodTotals(entries, settings, now) ?: return TimerTotalsState.UnknownZone(settings.timeZone)
    val text = totals.formatted(settings)
    return TimerTotalsState.Shown(text.today, text.week, totals.todaySeconds, totals.weekSeconds)
}

/**
 * The row for the cached [entries] in [settings], again on each tick of [clock]: each whole
 * second while an entry runs, otherwise at the account's midnight, so an idle page still rolls the
 * day and the week. Without settings, or with a timezone the phone cannot resolve, nothing ticks.
 * It subscribes to [entries] once, whatever the settings do, and asks the server nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun timerTotals(
    entries: Flow<List<CachedTimeEntry>>,
    settings: Flow<AccountSettings?>,
    clock: Clock,
): Flow<TimerTotalsState> = combine(settings, entries) { account, rows -> account to rows }
    .transformLatest { (account, rows) ->
        val time = account?.time()
        if (time == null) {
            emit(timerTotalsAt(rows, account, clock.instant()))
            return@transformLatest
        }
        val running = rows.any { it.stop == null }
        while (true) {
            val now = clock.instant()
            emit(timerTotalsAt(rows, account, now))
            delay(nextTotalsDelay(now, time, running))
        }
    }
