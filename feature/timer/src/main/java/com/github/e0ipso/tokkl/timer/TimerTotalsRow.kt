package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.nextTotalsDelay
import com.github.e0ipso.tokkl.timerstate.timerPeriodTotals
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
fun TimerTotals(
    entries: Flow<List<CachedTimeEntry>>,
    settings: StateFlow<AccountSettings?>,
    clock: Clock = Clock.systemUTC(),
) {
    val current by settings.collectAsState()
    val cached: List<CachedTimeEntry>? by entries.collectAsState(initial = null)
    val now = rememberTotalsNow(clock, current, cached?.any { it.stop == null } == true)
    val account = current ?: return
    val rows = cached ?: return
    val totals = timerPeriodTotals(rows, account, now)
    if (totals == null) {
        Text(stringResource(R.string.history_unknown_time_zone, account.timeZone), style = MaterialTheme.typography.bodyLarge)
        return
    }
    val text = totals.formatted(account)
    TimerTotalsRow(text.today, text.week, totals.todaySeconds, totals.weekSeconds)
}

/**
 * Today and this week share one row. Each total wraps inside its half, so a large font does not
 * stack them and push the view switch off the phone. The summary belongs to the scroll-away header.
 */
@Composable
private fun TimerTotalsRow(today: String, week: String, todaySeconds: Long, weekSeconds: Long) {
    Row(
        Modifier.fillMaxWidth().padding(top = TokklTheme.spacing.space3),
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4),
    ) {
        TimerTotal(R.string.timer_total_today, today, todaySeconds, "timer_totals_today", Modifier.weight(1f))
        TimerTotal(R.string.timer_total_this_week, week, weekSeconds, "timer_totals_week", Modifier.weight(1f))
    }
}

/** TalkBack gets the account-period label and the elapsed duration in words. */
@Composable
private fun TimerTotal(@StringRes labelId: Int, value: String, seconds: Long, tag: String, modifier: Modifier = Modifier) {
    val label = stringResource(labelId)
    val spoken = stringResource(R.string.timer_total_spoken, label, spokenDuration(LocalResources.current, seconds))
    Column(modifier.testTag(tag).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The instant the totals use. While an entry runs it moves each whole second. Otherwise it
 * waits until account midnight, so an idle page still rolls the day and the week. With no
 * [settings] zone there is nothing to roll.
 */
@Composable
private fun rememberTotalsNow(clock: Clock, settings: AccountSettings?, hasRunning: Boolean): Instant {
    var now by remember(clock) { mutableStateOf(clock.instant()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val time = settings?.time()
    LaunchedEffect(clock, lifecycle, time?.zone, time?.firstDayOfWeek, hasRunning) {
        val accountTime = time ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = clock.instant()
                now = current
                delay(nextTotalsDelay(current, accountTime, hasRunning))
            }
        }
    }
    return now
}

/**
 * Whole seconds from [start] to now on [clock], moved on at each whole second while the screen
 * shows, or zero without [start]. Nothing here asks the server.
 */
@Composable
internal fun rememberElapsedSeconds(start: Instant?, clock: Clock): Long {
    var now by remember(clock) { mutableStateOf(clock.instant()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (start != null) {
        LaunchedEffect(start, clock, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = clock.instant()
                    delay(SECOND_MS - Math.floorMod(Duration.between(start, now).toMillis(), SECOND_MS))
                }
            }
        }
    }
    return if (start == null) 0L else elapsedSeconds(start, now)
}

/** Whole seconds from [start] to [now], as the web app's entrySeconds counts, and never below zero. */
internal fun elapsedSeconds(start: Instant, now: Instant): Long =
    maxOf(0L, Math.floorDiv(Duration.between(start, now).toMillis(), SECOND_MS))

private const val SECOND_MS = 1_000L
