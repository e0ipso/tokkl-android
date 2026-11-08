package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId

/** [entry] as the cache's Room row, for a test that writes the row itself. */
fun cachedRowOf(entry: TimeEntry) = CachedTimeEntry(
    id = entry.id.value,
    workspaceId = entry.workspaceId.value,
    projectId = entry.projectId?.value,
    taskId = entry.taskId?.value,
    description = entry.description,
    start = entry.start,
    stop = entry.stop,
    billable = entry.billable,
    tags = entry.tags,
    tagIds = entry.tagIds.map { it.value },
    allDay = entry.allDay,
    day = entry.day,
)

/** The cache's Room [row] as the app models it, for a test that read the row itself. */
fun modelOf(row: CachedTimeEntry) = TimeEntry(
    id = EntryId(row.id),
    workspaceId = WorkspaceId(row.workspaceId),
    projectId = row.projectId?.let(::ProjectId),
    taskId = row.taskId?.let(::TaskId),
    description = row.description,
    start = row.start,
    stop = row.stop,
    billable = row.billable,
    tags = row.tags,
    tagIds = row.tagIds.map(::TagId),
    allDay = row.allDay,
    day = row.day,
)
