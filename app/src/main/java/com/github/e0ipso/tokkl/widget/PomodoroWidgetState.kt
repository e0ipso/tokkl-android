package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Server session data for the Pomodoro widget, without requests or local phase rules. */
sealed interface PomodoroWidgetState {
    data object NoAccount : PomodoroWidgetState
    data object Unsupported : PomodoroWidgetState

    data class Idle(
        val account: String,
        val defaults: PomodoroDefaults,
        val entry: PomodoroWidgetEntry,
    ) : PomodoroWidgetState

    data class Active(
        val account: String,
        val revision: Long,
        val session: TimerPomodoro,
        /** Phase boundaries on the phone's clock, corrected by the held server offset. */
        val start: Instant,
        val end: Instant,
        val ended: Boolean,
        val entry: PomodoroWidgetEntry,
    ) : PomodoroWidgetState
}

/** Fields for Start and the labels for the last or active session's entry. */
data class PomodoroWidgetEntry(
    val workspaceId: Long,
    val fields: EntryFields,
    val project: String?,
    val projectColor: String?,
    val task: String?,
)

/**
 * Reads only held/cached data. [ringing] is the existing alarm service's phase-end signal; an
 * ordinary cache or state emission also checks the corrected planned end at [deviceNow]. The
 * launcher ticks between emissions, so no polling or idle wakeup is needed.
 */
fun pomodoroWidgetState(
    workspaceId: Long?,
    account: String?,
    available: Boolean,
    held: HeldTimerState?,
    defaults: PomodoroDefaults,
    remembered: RememberedPomodoroFields?,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    deviceNow: Instant,
    ringing: PomodoroPhaseKey? = null,
): PomodoroWidgetState {
    if (workspaceId == null || account == null) return PomodoroWidgetState.NoAccount
    if (!available) return PomodoroWidgetState.Unsupported
    val last = remembered?.takeIf { it.account == account }
    val session = held?.state?.pomodoro
    val fields = held?.let { rememberedPomodoroFields(account, it) } ?: last
    val entryWorkspace = fields?.workspaceId ?: workspaceId
    val entryFields = fields?.fields() ?: EntryFields()
    val labels = entryLabels(
        entryFields, projects.filter { it.workspaceId == entryWorkspace }, tasks.filter { it.workspaceId == entryWorkspace },
    )
    val entry = PomodoroWidgetEntry(entryWorkspace, entryFields, labels.project, labels.projectColor, labels.task)
    if (session == null) return PomodoroWidgetState.Idle(account, defaults, entry)
    val end = session.phase.plannedEnd.minus(held.clockOffset)
    return PomodoroWidgetState.Active(
        account, held.state.revision, session, session.phase.start.minus(held.clockOffset), end,
        ended = !deviceNow.isBefore(end) || ringing?.start == session.phase.start,
        entry = entry,
    )
}

/** Hashes all drawn values and captured action targets, excluding the independently ticking clock. */
fun PomodoroWidgetState.key(): String {
    val content = buildJsonObject {
        when (val state = this@key) {
            PomodoroWidgetState.NoAccount -> put("state", "no-account")
            PomodoroWidgetState.Unsupported -> put("state", "unsupported")
            is PomodoroWidgetState.Idle -> {
                put("state", "idle")
                put("account", state.account)
                put("work", state.defaults.workMinutes)
                put("shortRest", state.defaults.shortRestMinutes)
                put("longRest", state.defaults.longRestMinutes)
                put("every", state.defaults.longRestEvery)
                put("entry", state.entry.keyContent())
            }
            is PomodoroWidgetState.Active -> {
                put("state", "active")
                put("account", state.account)
                put("revision", state.revision)
                put("session", ApiJson.encodeToJsonElement(TimerPomodoro.serializer(), state.session))
                put("start", state.start.toString())
                put("end", state.end.toString())
                put("ended", state.ended)
                put("entry", state.entry.keyContent())
            }
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun PomodoroWidgetEntry.keyContent() = buildJsonObject {
    val saved = RememberedPomodoroFields("", workspaceId, fields.description, fields.projectId, fields.taskId, fields.tags, fields.billable)
    put("fields", ApiJson.encodeToJsonElement(RememberedPomodoroFields.serializer(), saved))
    put("project", project?.let(::JsonPrimitive) ?: JsonNull)
    put("projectColor", projectColor?.let(::JsonPrimitive) ?: JsonNull)
    put("task", task?.let(::JsonPrimitive) ?: JsonNull)
}
