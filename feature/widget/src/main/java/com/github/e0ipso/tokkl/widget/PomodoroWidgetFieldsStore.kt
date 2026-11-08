package com.github.e0ipso.tokkl.widget

import android.content.Context
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.timerstate.HeldFieldsMemory
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import kotlinx.serialization.Serializable
import java.io.IOException

/** The last accepted session's fields, scoped to the holder's persisted account name. */
@Serializable
data class RememberedPomodoroFields(
    val account: String,
    val workspaceId: WorkspaceId,
    val description: String,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val tags: List<String>,
    val billable: Boolean,
) {
    fun fields(): EntryFields = EntryFields(description, projectId, taskId, tags, billable)
}

internal fun rememberedPomodoroFields(account: String, held: HeldTimerState): RememberedPomodoroFields? {
    val session = held.state.pomodoro ?: return null
    val entry = held.state.runningEntry?.takeIf { it.id == session.entryId } ?: return null
    return RememberedPomodoroFields(
        account,
        WorkspaceId(entry.workspaceId),
        entry.description.orEmpty(),
        entry.projectId?.let(::ProjectId),
        entry.taskId?.let(::TaskId),
        entry.tags.orEmpty(),
        entry.billable == true,
    )
}

internal fun encodeRememberedPomodoroFields(fields: RememberedPomodoroFields): String =
    ApiJson.encodeToString(RememberedPomodoroFields.serializer(), fields)

internal fun decodeRememberedPomodoroFields(text: String, account: String): RememberedPomodoroFields? = try {
    ApiJson.decodeFromString(RememberedPomodoroFields.serializer(), text).takeIf { it.account == account }
} catch (_: IllegalArgumentException) {
    null
}

/**
 * Last fields for idle Start, including starts learned from another device. The holder calls
 * [remember] under its acceptance lock before publishing a state. Retirement waits for that
 * write, so clearing this file afterwards cannot be undone by a late answer. The random account
 * name from TimerStateStore also prevents another account from reading an uncleared record.
 *
 * Calls read and write the disk on the calling thread, as the timer state's store does, so never
 * call them on the main thread. Like the held timer state, these private preferences are excluded from
 * backup by the app's existing rules. No token or other credential is stored here.
 */
class PomodoroWidgetFieldsStore(context: Context) :
    HeldFieldsMemory,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = clear()

    private val file = PreferencesFile(context, FILE_NAME)
    private val preferences get() = file.preferences

    fun load(account: String): RememberedPomodoroFields? =
        preferences.getString(KEY_FIELDS, null)?.let { decodeRememberedPomodoroFields(it, account) }

    /** Idle states leave the last fields in place; accepted field edits update them too. */
    @Throws(IOException::class)
    override fun remember(account: String, held: HeldTimerState) {
        val fields = rememberedPomodoroFields(account, held) ?: return
        val encoded = encodeRememberedPomodoroFields(fields)
        if (preferences.getString(KEY_FIELDS, null) == encoded) return
        file.commitHere { putString(KEY_FIELDS, encoded) }
    }

    @Throws(IOException::class)
    override fun clear() {
        file.commitHere("Could not clear $FILE_NAME") { clear() }
    }

    private companion object {
        const val FILE_NAME = "pomodoro_widget_fields"
        const val KEY_FIELDS = "fields"
    }
}
