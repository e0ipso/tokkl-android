package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.CacheRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/** What the task list opens over the project form. */
sealed interface TaskOverlay {

    /** The form that renames [task] and marks it done or not done. */
    data class Edit(val task: Task) : TaskOverlay

    /** The question whether to delete [task]. */
    data class ConfirmDelete(val task: Task) : TaskOverlay
}

/** The project's tasks, open ones first and done ones after them, each part sorted as Tokkl sorts them. */
data class TasksData(val tasks: List<Task>) {

    /** With [task] as the server sent it after a change, in its place in the order. */
    fun with(task: Task): TasksData = TasksData((without(task.id).tasks + task).sortedForList())

    fun without(id: TaskId): TasksData = TasksData(tasks.filterNot { it.id == id })
}

typealias TasksState = CatalogState<TasksData, TaskOverlay>

/**
 * The task list in the project form (docs/PRD.md P5): the project's tasks from the server, loaded
 * each time a project's form opens or the app comes back to the page, and the changes the list
 * sends at once, as [CatalogModel] describes. Nothing is queued, and after each change the server accepts,
 * [refreshes] reloads the offline cache, so the entry editor's picker offers the change.
 *
 * @param repository the project's tasks on the server.
 */
class TasksModel(
    private val repository: TasksRepository,
    settings: AccountSettingsSource,
    refreshes: CacheRefresh,
    scope: CoroutineScope,
    io: CoroutineContext,
) {

    /** The project whose tasks the list shows, while it is open. Loads read it on [io]. */
    @Volatile
    private var project: ProjectId? = null

    private val catalog = CatalogModel<TasksData, TaskOverlay>(OpenProject(), settings, refreshes, scope, io)

    val state: StateFlow<TasksState> = catalog.state

    /** Opens the list of [project]'s tasks and loads them. Does nothing while that list is open. */
    fun open(project: ProjectId) {
        if (this.project == project && catalog.opened) return
        if (this.project != project) {
            catalog.close()
            this.project = project
        }
        catalog.shown()
    }

    /** Closes the list, as its form closes. */
    fun close() {
        catalog.close()
        project = null
    }

    fun retry() = catalog.retry()

    /** The app came back to the foreground: an open list loads again. */
    fun returned() = catalog.returned()

    fun reconnected() = catalog.reconnected()

    fun doneShown() = catalog.doneShown()

    fun askToEdit(task: Task) = catalog.show(TaskOverlay.Edit(task))

    fun askToDelete(task: Task) = catalog.show(TaskOverlay.ConfirmDelete(task))

    /** Closes the form or the question, which keeps the task as it is. */
    fun dismiss() = catalog.dismiss()

    /** Adds a task named [name], trimmed, to the project. */
    fun create(name: String) {
        val project = project ?: return
        val trimmed = name.trim()
        val known = state.value.data?.tasks.orEmpty().mapTo(HashSet()) { it.id }
        catalog.send(
            request = { workspace -> repository.create(workspace, project, trimmed, known) },
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
            request = { workspace -> repository.update(workspace, project, task.id, trimmed, done) },
            apply = TasksData::with,
            done = { CatalogChange.Saved(it.name) },
        )
    }

    /** Deletes the task the question is about. */
    fun delete() {
        val project = project ?: return
        val task = (state.value.overlay as? TaskOverlay.ConfirmDelete)?.task ?: return
        catalog.send(
            request = { workspace -> repository.delete(workspace, project, task.id) },
            apply = { data, _ -> data.without(task.id) },
            done = { CatalogChange.Deleted(task.name) },
        )
    }

    /** The tasks of the project open now, which the list loads. */
    private inner class OpenProject : CatalogRepository<TasksData> {
        override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = repository.workspace(stored)

        override suspend fun load(workspace: WorkspaceId): ApiResult<TasksData> {
            val project = project ?: return ApiResult.Failure(
                ApiError.InvalidResponse(IllegalArgumentException("no project is open")),
            )
            return repository.load(workspace, project)
        }
    }
}

/** Open tasks before done ones, each by name ignoring case, as the web app lists them. */
internal fun List<Task>.sortedForList(): List<Task> = sortedWith(
    compareBy<Task> { !it.active }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.id.value },
)
