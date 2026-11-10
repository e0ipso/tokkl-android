package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * What the entry form keeps in its saved state, so that a new process restoring its route opens
 * it as it was: the copy it opened on of an entry the cache lacked, [older], and what was
 * [typed]. The route says what the form opens on, and the form reads the entry from the cache
 * again, so a save goes against the entry as the phone holds it then.
 */
@Serializable
internal data class SavedEntryForm(val older: SavedEntry? = null, val typed: TypedDraft? = null) {

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The form [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedEntryForm? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/**
 * The fields of a draft that differ from what the form opened with, each null while it is as it
 * opened, so a field the user left takes what the cache holds when the form opens again.
 */
@Serializable
internal data class TypedDraft(
    val fields: SavedFields? = null,
    val epochDay: Long? = null,
    val start: String? = null,
    val end: String? = null,
    val duration: String? = null,
    val syncedDuration: String? = null,
    val durationSeconds: Long? = null,
    val noTimes: Boolean? = null,
) {
    /** [form], as the form opened again, with what was typed over it. */
    fun over(form: EntryDraft): EntryDraft = form.copy(
        fields = fields?.fields() ?: form.fields,
        date = epochDay?.let(LocalDate::ofEpochDay) ?: form.date,
        start = start ?: form.start,
        end = end ?: form.end,
        duration = duration ?: form.duration,
        syncedDuration = syncedDuration ?: form.syncedDuration,
        durationSeconds = durationSeconds ?: form.durationSeconds,
        noTimes = noTimes ?: form.noTimes,
    )

    companion object {
        /** What [form] has that differs from [opened], the form as it opened, or null for nothing. */
        fun of(opened: EntryDraft, form: EntryDraft): TypedDraft? = TypedDraft(
            fields = form.fields.takeIf { it != opened.fields }?.let(::SavedFields),
            epochDay = form.date.takeIf { it != opened.date }?.toEpochDay(),
            start = form.start.takeIf { it != opened.start },
            end = form.end.takeIf { it != opened.end },
            duration = form.duration.takeIf { it != opened.duration },
            syncedDuration = form.syncedDuration.takeIf { it != opened.syncedDuration },
            durationSeconds = form.durationSeconds.takeIf { it != opened.durationSeconds },
            noTimes = form.noTimes.takeIf { it != opened.noTimes },
        ).takeIf { it != TypedDraft() }
    }
}

@Serializable
internal data class SavedFields(
    val description: String,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val tags: List<String>,
    val billable: Boolean,
) {
    constructor(fields: EntryFields) : this(
        fields.description,
        fields.projectId,
        fields.taskId,
        fields.tags,
        fields.billable,
    )

    fun fields() = EntryFields(description, projectId, taskId, tags, billable)
}

/**
 * A [TimeEntry] as the saved state keeps it, with its instants as ISO-8601 text and its timing
 * flat, as the cache's rows keep it.
 */
@Serializable
internal data class SavedEntry(
    val id: EntryId,
    val workspaceId: WorkspaceId,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val description: String,
    val start: String,
    val stop: String?,
    val billable: Boolean,
    val tags: List<String>,
    val tagIds: List<TagId>,
    val allDay: Boolean,
    val epochDay: Long?,
) {
    constructor(entry: TimeEntry) : this(
        entry.id,
        entry.workspaceId,
        entry.projectId,
        entry.taskId,
        entry.description,
        entry.start.toString(),
        entry.stop?.toString(),
        entry.billable,
        entry.tags,
        entry.tagIds,
        entry.timing is EntryTiming.AllDay,
        (entry.timing as? EntryTiming.AllDay)?.day?.toEpochDay(),
    )

    fun entry() = TimeEntry(
        id = id,
        workspaceId = workspaceId,
        projectId = projectId,
        taskId = taskId,
        description = description,
        timing = EntryTiming.of(
            Instant.parse(start),
            stop?.let(Instant::parse),
            allDay,
            epochDay?.let(LocalDate::ofEpochDay),
        ),
        billable = billable,
        tags = tags,
        tagIds = tagIds,
    )
}
