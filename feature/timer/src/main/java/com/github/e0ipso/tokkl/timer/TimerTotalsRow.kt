package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The Today and This week row for [state], which [timerTotals] makes. It shows nothing until
 * the account settings and the cache are read, and says so when the phone cannot resolve the
 * account's timezone.
 */
@Composable
fun TimerTotals(state: TimerTotalsState, modifier: Modifier = Modifier) {
    when (state) {
        TimerTotalsState.Hidden -> Unit

        is TimerTotalsState.UnknownZone -> Text(
            stringResource(R.string.history_unknown_time_zone, state.timeZone),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )

        is TimerTotalsState.Shown -> TimerTotalsRow(
            state.today,
            state.week,
            state.todaySeconds,
            state.weekSeconds,
            modifier,
        )
    }
}

/**
 * Today and this week share one row. Each total wraps inside its half, so a large font does not
 * stack them and push the view switch off the phone. The summary belongs to the scroll-away header.
 */
@Composable
private fun TimerTotalsRow(
    today: String,
    week: String,
    todaySeconds: Long,
    weekSeconds: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(top = TokklTheme.spacing.space3),
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4),
    ) {
        TimerTotal(R.string.timer_total_today, today, todaySeconds, "timer_totals_today", Modifier.weight(1f))
        TimerTotal(R.string.timer_total_this_week, week, weekSeconds, "timer_totals_week", Modifier.weight(1f))
    }
}

/** TalkBack gets the account-period label and the elapsed duration in words. */
@Composable
private fun TimerTotal(
    @StringRes labelId: Int,
    value: String,
    seconds: Long,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(labelId)
    val spoken = stringResource(R.string.timer_total_spoken, label, spokenDuration(LocalResources.current, seconds))
    Column(modifier.testTag(tag).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
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
