package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.ui.WindowWidth

/**
 * The back stack after opening [page] from the bar, the rail or the More sheet: the page right
 * above Timer, or Timer alone. Back from the page goes to Timer, and Back from Timer closes the
 * app (docs/PRD.md B3).
 */
internal fun stackOpening(page: TopLevelPage): List<Route> =
    if (page == TopLevelPage.TIMER) listOf(Route.Timer) else listOf(Route.Timer, page.route)

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
internal fun List<Route>.holdsAnyOf(unavailable: Set<TopLevelPage>): Boolean = any { TopLevelPage.of(it) in unavailable }

/**
 * Whether the entry form on top of [stack] shows beside the Timer page below it: on an expanded
 * window ([width]) in the List [view], so the history and the form share the width. The Calendar
 * view keeps the whole width for the week, so there the form takes the screen, as on a phone.
 */
internal fun showsEntryBeside(width: WindowWidth, view: TimerView, stack: List<Route>): Boolean =
    width == WindowWidth.EXPANDED && view == TimerView.LIST &&
        stack.lastOrNull() is Route.Entry && stack.getOrNull(stack.lastIndex - 1) == Route.Timer

/** Whether the screen on top is the fullscreen timer, or the entry form opened from it. Both hide the bar. */
internal fun List<Route>.isFocused(): Boolean =
    lastOrNull() == Route.FullscreenTimer || (lastOrNull() is Route.Entry && getOrNull(lastIndex - 1) == Route.FullscreenTimer)

/**
 * The stack with [entry] on top: in place of a form already on top, so a form opened beside the
 * history over another one starts afresh, as a form opened on its own screen does, or above the
 * screen on top.
 */
internal fun List<Route>.withEntry(entry: Route.Entry): List<Route> =
    if (lastOrNull() is Route.Entry) dropLast(1) + entry else this + entry

/** The stack down to Timer, as the focused timer leaves it when the timer goes idle. */
internal fun List<Route>.downToTimer(): List<Route> {
    var stack = this
    while (stack.size > 1 && stack.last() != Route.Timer) stack = stack.dropLast(1)
    return stack
}
