package com.github.e0ipso.tokkl.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogPageEvents
import com.github.e0ipso.tokkl.catalog.CatalogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.CoroutineContext

/** The project form: a new project, or [Edit] of one the server sent. */
sealed interface ProjectForm {
    data object New : ProjectForm

    data class Edit(val project: Project) : ProjectForm
}

/**
 * What the projects page loads: every project of the workspace, archived ones included, sorted as
 * Tokkl sorts them, and the workspace's rate and currency, or null when they did not load.
 */
data class ProjectsData(val projects: List<Project>, val defaults: WorkspaceDefaults?) {

    /** With [project] as the server sent it after a change, in its place in the order. */
    fun with(project: Project): ProjectsData = copy(
        projects = (projects.filterNot { it.id == project.id } + project).sortedForList(),
    )
}

typealias ProjectsState = CatalogState<ProjectsData, ProjectForm>

/**
 * The projects page's state (docs/PRD.md P2): the workspace's projects from the server, loaded
 * each time the page opens or the app comes back to it, and the changes the page sends at once,
 * as [CatalogModel] describes. The form of a project the server sent lists its [tasks] (P5).
 *
 * @param repository the workspace's projects on the server.
 * @param tasksRepository the tasks of the project the form edits, on the server.
 * @param cachedProjects the projects on the phone, which the page lists while the server's do not
 *   load.
 * @param cachedClients the clients on the phone, which the form offers and the list names.
 */
class ProjectsViewModel(
    private val repository: ProjectsRepository,
    tasksRepository: TasksRepository,
    workspaceId: () -> Long?,
    refreshCache: () -> Unit,
    scope: CoroutineScope,
    io: CoroutineContext,
    val cachedProjects: Flow<List<CachedProject>> = flowOf(emptyList()),
    val cachedClients: Flow<List<CachedClient>> = flowOf(emptyList()),
) : ViewModel(scope),
    CatalogPageEvents {

    private val catalog = CatalogModel<ProjectsData, ProjectForm>(
        repository,
        workspaceId,
        refreshCache,
        viewModelScope,
        io,
    )

    /** The task list of the project the form edits. */
    val tasks = TasksModel(tasksRepository, workspaceId, refreshCache, viewModelScope, io)

    val state: StateFlow<ProjectsState> = catalog.state

    fun workspace(): Long? = catalog.workspace()

    override fun open() = catalog.open()

    fun retry() = catalog.retry()

    override fun reconnected() {
        catalog.reconnected()
        tasks.reconnected()
    }

    override fun close() {
        catalog.close()
        tasks.close()
    }

    override fun wentToBackground() {
        catalog.wentToBackground()
        tasks.wentToBackground()
    }

    override fun cameToForeground() {
        catalog.cameToForeground()
        tasks.cameToForeground()
    }

    fun doneShown() = catalog.doneShown()

    fun newProject() = catalog.show(ProjectForm.New)

    fun edit(project: Project) = catalog.show(ProjectForm.Edit(project))

    fun closeForm() = catalog.dismiss()

    /** Creates the project, or saves the one the form edits, and closes the form once the server accepts it. */
    fun save(fields: ProjectFields) {
        val form = state.value.overlay ?: return
        val known = state.value.data?.projects.orEmpty().mapTo(HashSet()) { it.id }
        catalog.send(
            request = { workspace ->
                when (form) {
                    ProjectForm.New -> repository.create(workspace, fields, known)
                    is ProjectForm.Edit -> repository.update(workspace, form.project.id, fields)
                }
            },
            apply = ProjectsData::with,
            done = { saved ->
                if (form == ProjectForm.New) {
                    CatalogChange.Created(
                        saved.name,
                    )
                } else {
                    CatalogChange.Saved(saved.name)
                }
            },
        )
    }

    /** Archives [project] when [active] is false, and restores it when true. */
    fun setActive(project: Project, active: Boolean) {
        catalog.send(
            request = { workspace -> repository.setActive(workspace, project.id, active) },
            apply = ProjectsData::with,
            done = { saved ->
                if (saved.active) {
                    CatalogChange.Restored(
                        saved.name,
                    )
                } else {
                    CatalogChange.Archived(saved.name)
                }
            },
        )
    }
}

/** By name, ignoring case, as Tokkl sorts them. */
internal fun List<Project>.sortedForList(): List<Project> =
    sortedWith(compareBy<Project, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
