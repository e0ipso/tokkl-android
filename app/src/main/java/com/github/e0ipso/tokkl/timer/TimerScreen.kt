package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import com.github.e0ipso.tokkl.ui.ScrollAwayColumn
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.TopBarStatusBand
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.compactHeightWindow
import com.github.e0ipso.tokkl.ui.shortWindow
import com.github.e0ipso.tokkl.ui.windowWidth
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
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
 * lays it out: the [timer], then Today and This week, then the List and Calendar switch, then
 * the entries in the chosen [view], under the top bar with the page's title, as on the other
 * top-level pages. The bar never carries the wordmark, which stays out of page headers
 * (docs/PRD.md, Wordmark). Today and This week are part of the timer slot, beneath its controls.
 *
 * [listContent], the history, and [calendarContent], the calendar, fill the rest of the page
 * under the switch, with a bounded height, so each scrolls on its own. On a window of compact
 * height, such as a phone held sideways or upright at the largest font scale, the bar, the timer
 * with Today and This week, and the switch scroll away as the entries scroll on, so the entries
 * get the page's whole height ([ScrollAwayColumn]). A bar pinned there would leave the week grid too
 * little room. The Calendar view does the same at every height: on a phone held upright the
 * timer, the totals and the switch scroll away, and the grid has the page's height under the
 * calendar's toolbar.
 * On a phone held sideways the page opens in the Calendar view with all of that, the calendar's
 * toolbar too, already scrolled away, so the week shows at least four hour rows at once. A drag
 * on the day heads, or the grid back at midnight, brings them back, and TalkBack scrolls to them.
 *
 * On an expanded window the Calendar view takes the extra width, up to
 * [TokklSpacing.wideContentMaxWidth], so the week's columns get the room, and the timer,
 * its totals and the switch line up with it. The List view keeps a phone's column, so its
 * lines stay short.
 */
@Composable
fun TimerScreen(
    view: TimerView,
    onViewChange: (TimerView) -> Unit,
    timer: @Composable () -> Unit,
    listContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    feedback: @Composable () -> Unit = {},
) {
    val title = stringResource(R.string.nav_timer)
    val scrollAway = view == TimerView.CALENDAR || compactHeightWindow()
    val columnWidth = if (view == TimerView.CALENDAR && windowWidth() == WindowWidth.EXPANDED) {
        TokklSpacing.wideContentMaxWidth
    } else {
        TokklSpacing.contentMaxWidth
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { feedback() },
        topBar = { if (scrollAway) TopBarStatusBand() else TokklTopBar(title) },
        contentWindowInsets = ScrollingScreenInsets,
    ) { innerPadding ->
        ScrollAwayColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            scrollAway = scrollAway,
            startScrolledAway = view == TimerView.CALENDAR && shortWindow(),
            top = {
                if (scrollAway) TokklTopBar(title, windowInsets = WindowInsets(0))
                Column(Modifier.screenContent(columnWidth)) {
                    Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
                    timer()
                    Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
                    ViewSwitch(view, onViewChange)
                    Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
                }
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .screenContent(columnWidth),
            ) {
                when (view) {
                    TimerView.LIST -> listContent()
                    TimerView.CALENDAR -> calendarContent()
                }
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
