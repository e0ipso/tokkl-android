package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** What the task list opens over the project form. */
sealed interface TaskOverlay {

    /** The form that renames [task] and marks it done or not done. */
    data class Edit(val task: ProjectTask) : TaskOverlay

    /** The question whether to delete [task]. */
    data class ConfirmDelete(val task: ProjectTask) : TaskOverlay
}

/** The project's tasks, open ones first and done ones after them, each part sorted as Tokkl sorts them. */
data class TasksData(val tasks: List<ProjectTask>) {

    /** With [task] as the server sent it after a change, in its place in the order. */
    fun with(task: ProjectTask): TasksData = TasksData((without(task.id).tasks + task).sortedForList())

    fun without(id: Long): TasksData = TasksData(tasks.filterNot { it.id == id })
}

typealias TasksState = CatalogState<TasksData, TaskOverlay>

/**
 * The task list in the project form (docs/PRD.md P5): the project's tasks from the server, loaded
 * each time a project's form opens or the app comes back to it, and the changes the list sends at
 * once, as [CatalogModel] describes. Nothing is queued, and after each change the server accepts,
 * [refreshCache] reloads the offline cache, so the entry editor's picker offers the change.
 */
class TasksModel(
    client: () -> TogglApiClient?,
    workspaceId: () -> Long?,
    refreshCache: () -> Unit,
    scope: CoroutineScope,
    io: CoroutineContext,
) {

    /** The project whose tasks the list shows, while it is open. Loads read it on [io]. */
    @Volatile
    private var project: Long? = null

    private val catalog = CatalogModel<TasksData, TaskOverlay>(client, workspaceId, refreshCache, scope, io, ::read)

    val state: StateFlow<TasksState> = catalog.state

    /** Opens the list of [project]'s tasks. Does nothing while it is open, so turning the phone sends no request. */
    fun open(project: Long) {
        if (this.project != project) {
            catalog.close()
            this.project = project
        }
        catalog.open()
    }

    fun close() {
        catalog.close()
        project = null
    }

    fun retry() = catalog.retry()

    fun reconnected() = catalog.reconnected()

    fun wentToBackground() = catalog.wentToBackground()

    fun cameToForeground() = catalog.cameToForeground()

    fun doneShown() = catalog.doneShown()

    fun askToEdit(task: ProjectTask) = catalog.show(TaskOverlay.Edit(task))

    fun askToDelete(task: ProjectTask) = catalog.show(TaskOverlay.ConfirmDelete(task))

    /** Closes the form or the question, which keeps the task as it is. */
    fun dismiss() = catalog.dismiss()

    /** Adds a task named [name], trimmed, to the project. */
    fun create(name: String) {
        val project = project ?: return
        val trimmed = name.trim()
        val known = state.value.data?.tasks.orEmpty().mapTo(HashSet()) { it.id }
        catalog.send(
            request = { api, workspace -> TasksServer(api, workspace, project).create(trimmed, known) },
            apply = TasksData::with,
            done = { CatalogChange.Created(it.name) },
        )
    }

    /** Saves the task the form is about with [name], trimmed, and [done]. */
    fun save(name: String, done: Boolean) {
        val project = project ?: return
        val task = (state.value.overlay as? TaskOverlay.Edit)?.task ?: return
        val trimmed = name.trim()
        catalog.send(
            request = { api, workspace -> TasksServer(api, workspace, project).update(task.id, trimmed, done) },
            apply = TasksData::with,
            done = { CatalogChange.Saved(it.name) },
        )
    }

    /** Deletes the task the question is about. */
    fun delete() {
        val project = project ?: return
        val task = (state.value.overlay as? TaskOverlay.ConfirmDelete)?.task ?: return
        catalog.send(
            request = { api, workspace -> TasksServer(api, workspace, project).delete(task.id) },
            apply = { data, _ -> data.without(task.id) },
            done = { CatalogChange.Deleted(task.name) },
        )
    }

    private fun read(api: TogglApiClient, workspace: Long): ApiResult<TasksData> {
        val project = project ?: return ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("no project is open")))
        return when (val answer = TasksServer(api, workspace, project).list()) {
            is ApiResult.Success -> ApiResult.Success(TasksData(answer.value.filter { it.projectId == project }.sortedForList()))
            is ApiResult.Failure -> answer
        }
    }
}

/** Open tasks before done ones, each by name ignoring case, as the web app lists them. */
private fun List<ProjectTask>.sortedForList(): List<ProjectTask> =
    sortedWith(
        compareBy<ProjectTask> { !it.active }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id },
    )
