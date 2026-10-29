package com.github.e0ipso.tokkl.widget

import android.content.Context
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import java.io.IOException
import kotlinx.serialization.Serializable

/** The last accepted session's fields, scoped to the holder's persisted account name. */
@Serializable
data class RememberedPomodoroFields(
    val account: String,
    val workspaceId: Long,
    val description: String,
    val projectId: Long?,
    val taskId: Long?,
    val tags: List<String>,
    val billable: Boolean,
) {
    fun fields(): EntryFields = EntryFields(description, projectId, taskId, tags, billable)
}

internal fun rememberedPomodoroFields(account: String, held: HeldTimerState): RememberedPomodoroFields? {
    val session = held.state.pomodoro ?: return null
    val entry = held.state.runningEntry?.takeIf { it.id == session.entryId } ?: return null
    return RememberedPomodoroFields(
        account, entry.workspaceId, entry.description.orEmpty(), entry.projectId, entry.taskId,
        entry.tags.orEmpty(), entry.billable == true,
    )
}

internal fun encodeRememberedPomodoroFields(fields: RememberedPomodoroFields): String =
    ApiJson.encodeToString(RememberedPomodoroFields.serializer(), fields)

internal fun decodeRememberedPomodoroFields(text: String, account: String): RememberedPomodoroFields? =
    try {
        ApiJson.decodeFromString(RememberedPomodoroFields.serializer(), text).takeIf { it.account == account }
    } catch (e: IllegalArgumentException) {
        null
    }

/**
 * Last fields for idle Start, including starts learned from another device. The holder calls
 * [remember] under its acceptance lock before publishing a state. Retirement waits for that
 * write, so clearing this file afterwards cannot be undone by a late answer. The random account
 * name from TimerStateStore also prevents another account from reading an uncleared record.
 *
 * Calls read the disk. Like the held timer state, these private preferences are excluded from
 * backup by the app's existing rules. No token or other credential is stored here.
 */
class PomodoroWidgetFieldsStore(context: Context) {
    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun load(account: String): RememberedPomodoroFields? =
        preferences.getString(KEY_FIELDS, null)?.let { decodeRememberedPomodoroFields(it, account) }

    /** Idle states leave the last fields in place; accepted field edits update them too. */
    @Throws(IOException::class)
    fun remember(account: String, held: HeldTimerState) {
        val fields = rememberedPomodoroFields(account, held) ?: return
        val encoded = encodeRememberedPomodoroFields(fields)
        if (preferences.getString(KEY_FIELDS, null) == encoded) return
        if (!preferences.edit().putString(KEY_FIELDS, encoded).commit()) throw IOException("Could not write $FILE_NAME")
    }

    @Throws(IOException::class)
    fun clear() {
        if (!preferences.edit().clear().commit()) throw IOException("Could not clear $FILE_NAME")
    }

    private companion object {
        const val FILE_NAME = "pomodoro_widget_fields"
        const val KEY_FIELDS = "fields"
    }
}
