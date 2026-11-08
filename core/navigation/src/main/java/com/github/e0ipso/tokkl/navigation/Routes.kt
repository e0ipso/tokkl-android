package com.github.e0ipso.tokkl.navigation

import androidx.navigation3.runtime.NavKey
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * The app's screens, as the back stack holds them. The stack is saved with these serializers, so
 * it survives configuration changes and process death.
 */
@Serializable
sealed interface Route : NavKey {

    /**
     * Sign-in. With [server], signing in again to that account after the server refused its
     * token: the form starts filled in for it, and the sign-in checks it is the same user.
     */
    @Serializable
    data class SignIn(val server: String? = null, val userId: Long? = null) : Route {
        /** The account to sign in again to, or null for a plain sign-in. */
        val again: SignInAgain? get() = server?.let(ServerUrl::parse)?.let { SignInAgain(it, userId) }

        companion object {
            fun again(account: SignInAgain) = SignIn(account.server.toString(), account.userId)
        }
    }

    @Serializable data object Timer : Route

    /** The running timer on its own, without the bar. */
    @Serializable data object FullscreenTimer : Route

    @Serializable data object Pomodoro : Route

    @Serializable data object Reports : Route

    @Serializable data object Projects : Route

    @Serializable data object Clients : Route

    @Serializable data object Tags : Route

    @Serializable data object Trash : Route

    @Serializable data object Templates : Route

    @Serializable data object Settings : Route

    @Serializable data object Licences : Route

    /** The changes the server refused. */
    @Serializable data object NeedsAttention : Route

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
