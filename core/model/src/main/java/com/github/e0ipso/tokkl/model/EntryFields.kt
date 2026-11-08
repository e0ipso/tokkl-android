package com.github.e0ipso.tokkl.model

/** The fields of a time entry that the user picks, apart from its workspace and times. */
data class EntryFields(
    val description: String = "",
    val projectId: ProjectId? = null,
    val taskId: TaskId? = null,
    /** Tag names. A name no cached tag has is kept, and the server creates the tag. */
    val tags: List<String> = emptyList(),
    val billable: Boolean = false,
)

/**
 * One of Tokkl's favorites, which the app calls timer templates, at [position] in the user's
 * list. Favorites have no server id.
 */
data class Favorite(
    val position: Int,
    val description: String,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val tags: List<String>,
    val billable: Boolean,
)

/** The fields of [this] that the user picks. */
fun TimeEntry.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)

/** What a tap on this favorite starts. */
fun Favorite.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)
