package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The Pomodoro page's start form as the page keeps it in its saved state, so that a new process
 * restoring the page shows it as it was: its fields, and its lengths once the user typed them,
 * null while they follow the account's defaults.
 */
@Serializable
internal data class SavedPomodoroForm(
    val description: String,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val tags: List<String>,
    val billable: Boolean,
    val lengths: SavedLengths? = null,
) {

    fun fields() = EntryFields(description, projectId, taskId, tags, billable)

    /** The typed lengths, or null while they follow the defaults. */
    fun lengths(): PomodoroLengths? = lengths?.let { PomodoroLengths(it.work, it.shortRest, it.longRest, it.every) }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(fields: EntryFields, lengths: PomodoroLengths?) = SavedPomodoroForm(
            fields.description,
            fields.projectId,
            fields.taskId,
            fields.tags,
            fields.billable,
            lengths?.let { SavedLengths(it.work, it.shortRest, it.longRest, it.every) },
        )

        /** The form [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedPomodoroForm? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** [PomodoroLengths] as the saved state keeps them: the four fields as typed. */
@Serializable
internal data class SavedLengths(val work: String, val shortRest: String, val longRest: String, val every: String)
