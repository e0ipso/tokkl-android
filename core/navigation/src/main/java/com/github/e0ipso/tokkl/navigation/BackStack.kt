package com.github.e0ipso.tokkl.navigation

/**
 * The back stack after opening [page] from the bar, the rail or the More sheet: the page right
 * above Timer, or Timer alone. Back from the page goes to Timer, and Back from Timer closes the
 * app (docs/PRD.md B3).
 */
internal fun stackOpening(page: TopLevelPage): List<Route> =
    if (page == TopLevelPage.TIMER) listOf(Route.Timer) else listOf(Route.Timer, page.route)

/** Whether the screen on top is the fullscreen timer, or the entry form opened from it. Both hide the bar. */
internal fun List<Route>.isFocused(): Boolean = lastOrNull() == Route.FullscreenTimer || (
    lastOrNull() is Route.Entry && getOrNull(
        lastIndex - 1,
    ) == Route.FullscreenTimer
    )

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
