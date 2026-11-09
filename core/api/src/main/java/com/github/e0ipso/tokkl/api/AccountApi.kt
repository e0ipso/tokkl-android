package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.WorkspaceId
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
class AccountApi internal constructor(private val client: ApiTransport) {

    /** `GET /api/v9/me`, the whole [Me] that sign-in reads. */
    suspend fun me(): ApiResult<Me> = client.get(client.server.apiUrl("me"), Me.serializer())

    /**
     * `GET /api/v9/me` read for its default workspace and role alone, as the catalog pages and the
     * reports read it, so a body that lacks the rest of a [Me] still serves them.
     */
    suspend fun defaultWorkspace(): ApiResult<MeWorkspace> =
        client.get(client.server.apiUrl("me"), MeWorkspace.serializer())

    /** `GET /api/v9/me?with_related_data=true`, with the clients, projects, tags and tasks. */
    suspend fun meWithRelatedData(): ApiResult<Me> = client.get(
        client.server.apiUrl("me").newBuilder().addQueryParameter("with_related_data", "true").build(),
        Me.serializer(),
    )

    /** `GET /api/v9/me/preferences`. */
    suspend fun preferences(): ApiResult<MePreferences> =
        client.get(client.server.apiUrl("me", "preferences"), JsonObject.serializer()).map(::MePreferences)

    /**
     * `POST /api/v9/me/preferences` with the keys of [change] alone. Tokkl merges them into the
     * stored preferences, so every other key stays, and answers with the stored preferences as a
     * JSON object, which this call checks and does not read.
     */
    suspend fun savePreferences(change: PreferencesChange): ApiResult<Unit> = client.send(
        HttpMethod.POST,
        client.server.apiUrl("me", "preferences"),
        change.json(),
        JsonObject.serializer(),
    ).map { }

    /** `GET /api/v9/workspaces/{workspaceId}`. */
    suspend fun workspace(workspaceId: WorkspaceId): ApiResult<WorkspaceSettings> =
        client.get(client.server.apiUrl("workspaces", workspaceId.toString()), WorkspaceSettings.serializer())

    private fun PreferencesChange.json(): JsonElement = when (this) {
        is PomodoroDefaultsChange -> json(PomodoroDefaultsChange.serializer(), this)
        is ReminderChange -> json(ReminderChange.serializer(), this)
        is TimerViewChange -> json(TimerViewChange.serializer(), this)
        is FavoritesChange -> json(FavoritesChange.serializer(), this)
    }
}
