package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A body of `POST /api/v9/me/preferences`: the keys of one setting, and nothing else. */
sealed interface PreferencesChange

/** The four Pomodoro lengths a session starts with, which the web app's Settings edit too. */
@Serializable
data class PomodoroDefaultsChange(
    @SerialName(WORK_MINUTES) val workMinutes: Int,
    @SerialName(SHORT_REST_MINUTES) val shortRestMinutes: Int,
    @SerialName(LONG_REST_MINUTES) val longRestMinutes: Int,
    @SerialName(LONG_REST_EVERY) val longRestEvery: Int,
) : PreferencesChange {

    companion object {
        /** Tokkl's preference keys, which a refresh reads back. */
        const val WORK_MINUTES = "pomodoro_focus_interval_in_minutes"
        const val SHORT_REST_MINUTES = "pomodoro_break_interval_in_minutes"
        const val LONG_REST_MINUTES = "pomodoro_long_break_interval_in_minutes"
        const val LONG_REST_EVERY = "pomodoro_long_break_every"
    }
}

/** The long-running timer reminder: whether it is on, and after how many hours. */
@Serializable
data class ReminderChange(@SerialName(ENABLED) val enabled: Boolean, @SerialName(HOURS) val hours: Int) :
    PreferencesChange {

    companion object {
        /** Tokkl's preference keys, which a refresh reads back. */
        const val ENABLED = "long_timer_reminder_enabled"
        const val HOURS = "long_timer_reminder_hours"
    }
}

/** The Timer page's List or Calendar view, as Tokkl names it: `list` or `calendar`. */
@Serializable
data class TimerViewChange(@SerialName(KEY) val view: String) : PreferencesChange {

    companion object {
        /** Tokkl's preference key, which a refresh reads back. */
        const val KEY = "timerView"
    }
}

/** The whole list of favorites, in order, as Tokkl's web app saves them. */
@Serializable
data class FavoritesChange(val favorites: List<FavoriteBody>) : PreferencesChange

/** One favorite: what a tap starts. [projectId] and [taskId] go as null when unset; tags by name. */
@Serializable
data class FavoriteBody(
    val description: String,
    @SerialName("project_id") val projectId: Long?,
    @SerialName("task_id") val taskId: Long?,
    val tags: List<String>,
    val billable: Boolean,
)

/** The signed-in user, their preferences and their workspaces. */
class AccountApi internal constructor(private val client: TogglApiClient) {

    /**
     * `GET /api/v9/me`, read as [deserializer]: sign-in reads a whole [Me], while the catalog pages
     * and the reports read only the default workspace, so a body that lacks the rest still serves
     * them.
     */
    suspend fun <T> me(deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.get(client.server.api("me"), deserializer)

    /** `GET /api/v9/me?with_related_data=true`, with the clients, projects, tags and tasks. */
    suspend fun meWithRelatedData(): ApiResult<Me> = client.get(
        client.server.api("me").newBuilder().addQueryParameter("with_related_data", "true").build(),
        Me.serializer(),
    )

    /** `GET /api/v9/me/preferences`. */
    suspend fun preferences(): ApiResult<JsonObject> =
        client.get(client.server.api("me", "preferences"), JsonObject.serializer())

    /**
     * `POST /api/v9/me/preferences` with the keys of [change] alone. Tokkl merges them into the
     * stored preferences, so every other key stays. The answer reads as [deserializer].
     */
    suspend fun <T> savePreferences(
        change: PreferencesChange,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(HttpMethod.POST, client.server.api("me", "preferences"), change.json(), deserializer)

    /** `GET /api/v9/workspaces/{workspaceId}`, read as [deserializer]. */
    suspend fun <T> workspace(workspaceId: Long, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.get(client.server.api("workspaces", workspaceId.toString()), deserializer)

    private fun PreferencesChange.json(): JsonElement = when (this) {
        is PomodoroDefaultsChange -> json(PomodoroDefaultsChange.serializer(), this)
        is ReminderChange -> json(ReminderChange.serializer(), this)
        is TimerViewChange -> json(TimerViewChange.serializer(), this)
        is FavoritesChange -> json(FavoritesChange.serializer(), this)
    }
}
