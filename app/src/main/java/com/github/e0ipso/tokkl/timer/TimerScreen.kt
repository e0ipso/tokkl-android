package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.ScrollAwayColumn
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * How the Timer page shows time entries, as the web app's view switch does. [preferenceValue] is
 * the view's value in Tokkl's `timerView` preference.
 */
enum class TimerView(@StringRes val label: Int, val preferenceValue: String) {
    /** The entries, a day at a time. */
    LIST(R.string.timer_view_list, "list"),

    /** The calendar: the week, or the day on a phone held upright. */
    CALENDAR(R.string.timer_view_calendar, "calendar"),
}

/**
 * The Timer page, the first page after sign-in (docs/PRD.md B2), as the web app's TimerPage.tsx
 * lays it out: the [timer], then the List and Calendar switch, then the entries in the chosen
 * [view]. The page starts with its title, as the other top-level pages do, and never with the
 * wordmark, which stays out of page headers (docs/PRD.md, Wordmark).
 *
 * [listContent], the history, and [calendarContent], the calendar, fill the rest of the page
 * under the switch, with a bounded height, so each scrolls on its own. On a window of compact
 * height, such as a phone held sideways or upright at the largest font scale, the title, the
 * timer and the switch scroll away as the entries scroll on, so the entries get the page's whole
 * height ([ScrollAwayColumn]).
 */
@Composable
fun TimerScreen(
    view: TimerView,
    onViewChange: (TimerView) -> Unit,
    timer: @Composable () -> Unit,
    listContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
        ScrollAwayColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .screenContent(),
            top = {
                Spacer(Modifier.height(TokklTheme.spacing.space2))
                PageTitle(stringResource(R.string.nav_timer))
                Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
                timer()
                Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
                ViewSwitch(view, onViewChange)
                Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            },
        ) {
            when (view) {
                TimerView.LIST -> listContent()
                TimerView.CALENDAR -> calendarContent()
            }
        }
    }
}

/**
 * The List and Calendar choice. Each segment is a radio button to TalkBack, and the chosen one
 * carries a tick, so its colour is not the only sign.
 */
@Composable
private fun ViewSwitch(view: TimerView, onViewChange: (TimerView) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        for ((index, option) in TimerView.entries.withIndex()) {
            SegmentedButton(
                selected = option == view,
                onClick = { onViewChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, TimerView.entries.size),
                label = { Text(stringResource(option.label)) },
            )
        }
    }
}
