package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.snapshots.Snapshot
import com.github.e0ipso.tokkl.model.TimeEntry

/**
 * Changes the back stack, [stack], as the screens and the taps from outside the app ask. Each
 * change is one snapshot, so a route that stays, such as Timer under a page that closes, keeps
 * its view models and saved state. Call it on the main thread.
 */
class Navigator(private val stack: MutableList<Route>, val openings: EntryFormOpenings) {

    val top: Route? get() = stack.lastOrNull()

    /** Whether the fullscreen timer, or the entry form opened from it, is on top ([isFocused]). */
    val focused: Boolean get() = stack.isFocused()

    /**
     * Shows the top-level page [route] right above Timer, or Timer alone, dropping whatever stood
     * above Timer before.
     */
    fun open(route: Route) = replace(stackOpening(route))

    /** As [open], but a page in [unavailable] opens Timer instead. */
    fun open(route: Route, unavailable: Set<Route>) = open(if (route in unavailable) Route.Timer else route)

    /** Puts [route] above the screen on top. */
    fun push(route: Route) {
        stack.add(route)
    }

    /** Closes [route] when it is on top. */
    fun close(route: Route) {
        if (stack.lastOrNull() == route) stack.removeAt(stack.lastIndex)
    }

    /** Back: closes the screen on top, when one is below it. */
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Opens the entry form on [opening], on [older] when the cache does not hold the entry ([withEntry]). */
    fun openEntry(opening: EntryOpening, older: TimeEntry? = null) =
        replace(stack.withEntry(openings.route(opening, older)))

    /** Starts over with [route] alone, as sign-in does. */
    fun startOver(route: Route) = replace(listOf(route))

    /** Closes the focused timer and what stands above it, down to Timer ([downToTimer]). */
    fun downToTimer() = replace(stack.downToTimer())

    private fun replace(routes: List<Route>) {
        if (stack.toList() == routes) return
        Snapshot.withMutableSnapshot {
            stack.clear()
            stack.addAll(routes)
        }
    }
}
