package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.model.TimeEntry
import java.util.UUID

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
