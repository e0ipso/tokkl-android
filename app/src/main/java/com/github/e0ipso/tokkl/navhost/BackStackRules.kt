package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.ui.WindowWidth

/** The top-level page the screen on top belongs to: itself, or the nearest one below it. */
internal fun List<Route>.currentPage(): TopLevelPage =
    asReversed().firstNotNullOfOrNull(TopLevelPage::of) ?: TopLevelPage.TIMER

/**
 * The pages the server's [flags] leave without a use: Trash without the trash API, Timer
 * templates without the favorites key, and Pomodoro without the timer API, since the server owns
 * the session. The
 * bar, the rail and the More sheet leave them out, and the host opens none of them.
 */
fun unavailablePages(flags: Capabilities): Set<TopLevelPage> = buildSet {
    if (!flags.trash) add(TopLevelPage.TRASH)
    if (!flags.favorites) add(TopLevelPage.TEMPLATES)
    if (!flags.timerState) add(TopLevelPage.POMODORO)
}

/**
 * This route, or Timer when it belongs to a page in [unavailable]. A back stack restored from
 * before a capability was lost, or a direct open of a page, then shows Timer.
 */
fun Route.orTimerIfUnavailable(unavailable: Set<TopLevelPage>): Route =
    if (TopLevelPage.of(this) in unavailable) Route.Timer else this

/** Whether a route on the stack belongs to a page in [unavailable]. */
internal fun List<Route>.holdsAnyOf(unavailable: Set<TopLevelPage>): Boolean = any {
    TopLevelPage.of(
        it,
    ) in unavailable
}

/**
 * Whether the entry form on top of [stack] shows beside the Timer page below it: on an expanded
 * window ([width]) in the List [view], so the history and the form share the width. The Calendar
 * view keeps the whole width for the week, so there the form takes the screen, as on a phone.
 */
internal fun showsEntryBeside(width: WindowWidth, view: TimerView, stack: List<Route>): Boolean =
    width == WindowWidth.EXPANDED && view == TimerView.LIST &&
        stack.lastOrNull() is Route.Entry && stack.getOrNull(stack.lastIndex - 1) == Route.Timer
