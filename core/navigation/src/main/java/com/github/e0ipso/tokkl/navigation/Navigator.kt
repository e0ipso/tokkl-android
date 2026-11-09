package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.snapshots.Snapshot
import com.github.e0ipso.tokkl.model.TimeEntry
import java.util.UUID

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

/**
 * The entry form openings MainActivity made, each with the copy of an entry that only the
 * history's or the calendar's memory holds, until the form's view model takes it. A new process
 * starts with none: a form restored from saved state opens on what its route says, and its view
 * model brings back the copy and the typed fields from the form's own saved state.
 */
class EntryFormOpenings {
    private val waiting = mutableMapOf<String, TimeEntry?>()

    /** A new form's route for [opening], on [older] when the cache does not hold the entry. */
    fun route(opening: EntryOpening, older: TimeEntry?): Route.Entry {
        val route = Route.Entry(opening, UUID.randomUUID().toString())
        waiting[route.id] = older
        return route
    }

    /**
     * What [route] opens: its opening, with the copy this process holds of an entry the cache
     * lacks, which only the first take gets.
     */
    fun take(route: Route.Entry): EntryFormRequest = EntryFormRequest(route.opening, waiting.remove(route.id))

    fun clear() = waiting.clear()
}

/** What a new entry form opens on: [opening], with [older] for an entry the cache lacks. */
class EntryFormRequest(val opening: EntryOpening, val older: TimeEntry?)
