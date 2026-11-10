package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId

// The one mapping between the cache's Room rows, which keep the server's numbers, and the app's
// model, whose ids are typed. The rest of the app reads and writes the model.

internal fun CachedTimeEntry.toModel() = TimeEntry(
    id = EntryId(id),
    workspaceId = WorkspaceId(workspaceId),
    projectId = projectId?.let(::ProjectId),
    taskId = taskId?.let(::TaskId),
    description = description,
    timing = EntryTiming.of(start, stop, allDay, day),
    billable = billable,
    tags = tags,
    tagIds = tagIds.map(::TagId),
)

internal fun TimeEntry.toCached() = CachedTimeEntry(
    id = id.value,
    workspaceId = workspaceId.value,
    projectId = projectId?.value,
    taskId = taskId?.value,
    description = description,
    start = timing.start,
    stop = stop,
    billable = billable,
    tags = tags,
    tagIds = tagIds.map { it.value },
    allDay = timing is EntryTiming.AllDay,
    day = (timing as? EntryTiming.AllDay)?.day,
)

internal fun CachedEntryWithSendState.toModel() = EntryWithSendState(entry.toModel(), unsent)

internal fun CachedProject.toModel() = Project(
    id = ProjectId(id),
    workspaceId = WorkspaceId(workspaceId),
    clientId = clientId?.let(::ClientId),
    name = name,
    color = color,
    active = active,
    billable = billable,
)

internal fun CachedClient.toModel() = Client(ClientId(id), WorkspaceId(workspaceId), name, archived)

internal fun CachedTag.toModel() = Tag(TagId(id), WorkspaceId(workspaceId), name)

internal fun CachedTask.toModel() = Task(
    TaskId(id),
    WorkspaceId(workspaceId),
    projectId?.let(::ProjectId),
    name,
    active,
)

internal fun CachedFavorite.toModel() = Favorite(
    position = position,
    description = description,
    projectId = projectId?.let(::ProjectId),
    taskId = taskId?.let(::TaskId),
    tags = tags,
    billable = billable,
)

internal fun Favorite.toCached() = CachedFavorite(
    position = position,
    description = description,
    projectId = projectId?.value,
    taskId = taskId?.value,
    tags = tags,
    billable = billable,
)

internal fun PendingChange.toHeld() = HeldChange(sequence, kind, entry().toModel(), refusal)

/** The server's entry as the app models it, or null when its start is not an RFC 3339 timestamp. */
fun ServerTimeEntry.toTimeEntry(): TimeEntry? = toCached()?.toModel()
