package com.github.e0ipso.tokkl.navigation

import androidx.navigation3.runtime.NavKey
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * A screen, as the back stack holds it. Each feature declares the routes of its screens as
 * `@Serializable` classes and registers them in [FeatureEntries.registerRoutes], so the stack is
 * saved with their serializers and survives configuration changes and process death. Timer and the
 * entry form stay here: every signed-in stack starts at Timer, and other features open the form.
 */
interface Route : NavKey {

    /**
     * Whether this screen takes the whole window without the bar or the rail, as the fullscreen
     * timer does. The entry form opened above such a screen hides them too ([isFocused]).
     */
    val focused: Boolean get() = false

    /** The Timer page, at the bottom of every signed-in back stack. */
    @Serializable data object Timer : Route

    /**
     * The entry form, for what [opening] says. [id] tells one opening from another, so an entry
     * opened twice gets two forms, each with its own view model and saved state.
     */
    @Serializable
    data class Entry(val opening: EntryOpening, val id: String) : Route
}

/** What the entry form opens on. */
@Serializable
sealed interface EntryOpening {

    /** The past entry [entryId]. */
    @Serializable
    data class Edit(val entryId: EntryId) : EntryOpening

    /** The running segment that started at [startSeconds] and [startNanos], and only that one. */
    @Serializable
    data class Running(
        val entryId: EntryId,
        val workspaceId: WorkspaceId,
        val startSeconds: Long,
        val startNanos: Int,
    ) : EntryOpening {
        val identity: RunningEntryIdentity
            get() = RunningEntryIdentity(entryId, workspaceId, Instant.ofEpochSecond(startSeconds, startNanos.toLong()))

        companion object {
            fun of(identity: RunningEntryIdentity) =
                Running(identity.entryId, identity.workspaceId, identity.start.epochSecond, identity.start.nano)
        }
    }

    /**
     * A manual entry on the day [epochDay], or on today's date in the account's timezone. With
     * [noTimes], a no-time entry, as the calendar's All day row adds one.
     */
    @Serializable
    data class New(val epochDay: Long? = null, val noTimes: Boolean = false) : EntryOpening {
        val day: LocalDate? get() = epochDay?.let(LocalDate::ofEpochDay)

        companion object {
            fun on(day: LocalDate?, noTimes: Boolean = false) = New(day?.toEpochDay(), noTimes)
        }
    }
}

/**
 * The key a route's saved state and view models live under. Every route's text is different: the
 * objects print their names, and the entry form its opening and id.
 */
val Route.contentKey: String get() = toString()
