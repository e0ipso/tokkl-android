package com.github.e0ipso.tokkl.navigation

/**
 * The back stack after opening the top-level page [route] from the bar, the rail or the More
 * sheet: the page right above Timer, or Timer alone. Back from the page goes to Timer, and Back
 * from Timer closes the app (docs/PRD.md B3).
 */
internal fun stackOpening(route: Route): List<Route> =
    if (route == Route.Timer) listOf(Route.Timer) else listOf(Route.Timer, route)

/**
 * Whether the screen on top is [focused][Route.focused], such as the fullscreen timer, or the entry
 * form opened from one. Both hide the bar.
 */
internal fun List<Route>.isFocused(): Boolean = lastOrNull()?.focused == true ||
    (lastOrNull() is Route.Entry && getOrNull(lastIndex - 1)?.focused == true)

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
