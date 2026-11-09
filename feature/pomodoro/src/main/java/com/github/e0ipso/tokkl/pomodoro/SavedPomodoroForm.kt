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
    val lengths: List<String>? = null,
) {

    fun fields() = EntryFields(description, projectId, taskId, tags, billable)

    /** The typed lengths, or null while they follow the defaults or were saved in another shape. */
    fun lengths(): PomodoroLengths? = lengths?.takeIf { it.size == LENGTHS }?.let { (work, short, long, every) ->
        PomodoroLengths(work, short, long, every)
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private const val LENGTHS = 4

        private val json = Json { ignoreUnknownKeys = true }

        fun of(fields: EntryFields, lengths: PomodoroLengths?) = SavedPomodoroForm(
            fields.description,
            fields.projectId,
            fields.taskId,
            fields.tags,
            fields.billable,
            lengths?.let { listOf(it.work, it.shortRest, it.longRest, it.every) },
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
