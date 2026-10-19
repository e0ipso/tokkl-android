package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.picker.Picked

/**
 * What an entry's project and task show as: their names and the project's `#rrggbb` colour, from
 * the cache. All null when the entry has none, or when the cache does not have them.
 */
data class EntryLabels(
    val project: String? = null,
    val projectColor: String? = null,
    val task: String? = null,
)

/** The labels of [fields]'s project and task among the cached [projects] and [tasks]. */
fun entryLabels(fields: EntryFields, projects: List<CachedProject>, tasks: List<CachedTask>): EntryLabels {
    val project = fields.projectId?.let { id -> projects.firstOrNull { it.id == id } }
    val task = fields.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
    return EntryLabels(project = project?.name, projectColor = project?.color, task = task?.name)
}

/** The project, task and tags of these fields, as the picker shows them. */
val EntryFields.picked: Picked get() = Picked(projectId = projectId, taskId = taskId, tags = tags)

/**
 * These fields with [picked]'s project, task and tags. Picking a project or task of a billable
 * project makes the entry billable, and a pick of tags alone leaves billable as it was, as the
 * web app's timer does (TimerBar.tsx in ../tokkl/packages/web).
 */
fun EntryFields.withPicked(picked: Picked, projects: List<CachedProject>): EntryFields {
    val projectPicked = picked.projectId != projectId || picked.taskId != taskId
    val project = picked.projectId?.let { id -> projects.firstOrNull { it.id == id } }
    return copy(
        projectId = picked.projectId,
        taskId = picked.taskId,
        tags = picked.tags,
        billable = billable || (projectPicked && project?.billable == true),
    )
}

/** The fields of the cached [this] that the user picks. */
fun CachedTimeEntry.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)
