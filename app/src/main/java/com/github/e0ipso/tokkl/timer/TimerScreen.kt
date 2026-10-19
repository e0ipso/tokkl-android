package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.Brand
import com.github.e0ipso.tokkl.ui.BrandSize
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/** How the Timer page shows time entries, as the web app's view switch does. */
enum class TimerView(@StringRes val label: Int) {
    /** The entries, a day at a time. */
    LIST(R.string.timer_view_list),

    /** The week calendar. */
    CALENDAR(R.string.timer_view_calendar),
}

/**
 * The Timer page, the first page after sign-in (docs/PRD.md B2), as the web app's TimerPage.tsx
 * lays it out: the timer, then the List and Calendar switch, then the entries in the chosen [view]. The timer
 * (TKA-0039) is not built yet, so the brand stands in its place.
 *
 * [listContent], the history, and [calendarContent] fill the rest of the page under the switch,
 * with a bounded height, so each scrolls on its own. Until the week view (TKA-0030) exists, the
 * calendar says what it will show.
 */
@Composable
fun TimerScreen(
    view: TimerView,
    onViewChange: (TimerView) -> Unit,
    listContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    calendarContent: @Composable () -> Unit = { ComesLater(R.string.timer_calendar_later) },
) {
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .screenContent()
                .padding(top = TokklTheme.spacing.space2),
        ) {
            Brand(BrandSize.MEDIUM, Modifier.semantics { heading() })
            Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
            ViewSwitch(view, onViewChange)
            Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
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

/** What a view will show once it is built. It scrolls, so it fits under a large font. */
@Composable
private fun ComesLater(@StringRes message: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Text(text = stringResource(message), style = MaterialTheme.typography.bodyLarge)
    }
}
