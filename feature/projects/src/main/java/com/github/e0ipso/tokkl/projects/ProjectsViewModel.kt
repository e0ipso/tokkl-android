package com.github.e0ipso.tokkl.projects

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.catalog.PageEvents
import com.github.e0ipso.tokkl.catalog.followTrips
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AppEvent
import com.github.e0ipso.tokkl.session.DefaultWorkspace
import com.github.e0ipso.tokkl.session.RefreshRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
 * The projects page: what the server sent and the form open over it in [catalog], whether the
 * phone is [offline], the projects and clients on the phone of the page's workspace, which the
 * page lists while the server's do not load and the form offers, and the [tasks] of the project
 * the form edits.
 */
@Immutable
data class ProjectsPageState(
    val catalog: ProjectsState = ProjectsState(),
    val offline: Boolean = false,
    val cachedProjects: List<CachedProject> = emptyList(),
    val clients: List<CachedClient> = emptyList(),
    val tasks: TasksState = TasksState(),
)

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
 * @param offline whether the phone is offline, when nothing can change.
 * @param appEvents MainActivity's trips to the background and back, which load the page again.
 */
class ProjectsViewModel(
    private val repository: ProjectsRepository,
    tasksRepository: TasksRepository,
    workspaceId: DefaultWorkspace,
    refreshCache: RefreshRequest,
    scope: CoroutineScope,
    io: CoroutineContext,
    cachedProjects: Flow<List<CachedProject>>,
    cachedClients: Flow<List<CachedClient>>,
    offline: StateFlow<Boolean>,
    appEvents: Flow<AppEvent>,
) : ViewModel(scope),
    PageEvents {

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

    /** What the page shows, while it reads it. The cached rows are empty until the cache is read. */
    val page: StateFlow<ProjectsPageState> = combine(
        state,
        offline,
        cachedProjects.onStart { emit(emptyList()) },
        cachedClients.onStart { emit(emptyList()) },
        tasks.state,
    ) { shown, isOffline, projects, clients, taskList ->
        val workspace = workspace()
        ProjectsPageState(
            catalog = shown,
            offline = isOffline,
            cachedProjects = projects.filter { workspace == null || it.workspaceId == workspace },
            clients = clients.filter { workspace == null || it.workspaceId == workspace },
            tasks = taskList,
        )
    }.stateIn(viewModelScope, whileShown, ProjectsPageState(state.value, offline.value, tasks = tasks.state.value))

    init {
        followTrips(appEvents, viewModelScope)
    }

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
