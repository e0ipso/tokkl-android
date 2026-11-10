package com.github.e0ipso.tokkl.projects

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.platform.PageLoads
import com.github.e0ipso.tokkl.platform.stateInPage
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/** The project form: a new project, or [Edit] of one the server sent, with what it holds. */
sealed interface ProjectForm {
    val draft: ProjectDraft

    fun holding(draft: ProjectDraft): ProjectForm

    data class New(override val draft: ProjectDraft) : ProjectForm {
        override fun holding(draft: ProjectDraft) = copy(draft = draft)
    }

    data class Edit(val project: ProjectDetails, override val draft: ProjectDraft) : ProjectForm {
        override fun holding(draft: ProjectDraft) = copy(draft = draft)
    }

    companion object {
        /**
         * The form for a new project, or for [project] as the server sent it, as it opens in a
         * workspace with [projectCount] projects, which picks the colour the web app offers.
         */
        fun of(project: ProjectDetails?, projectCount: Int): ProjectForm {
            val draft = ProjectDraft.of(project, ProjectPalette.forNewProject(projectCount))
            return if (project == null) New(draft) else Edit(project, draft)
        }
    }
}

/**
 * What the projects page loads: every project of the workspace, archived ones included, sorted as
 * Tokkl sorts them, and the workspace's rate and currency, or null when they did not load.
 */
data class ProjectsData(val projects: List<ProjectDetails>, val defaults: WorkspaceDefaults?) {

    fun with(project: ProjectDetails): ProjectsData = copy(
        projects = (projects.filterNot { it.id == project.id } + project).sortedForList(),
    )
}

typealias ProjectsState = CatalogState<ProjectsData, ProjectForm>

/**
 * The projects and clients on the phone, of the page's workspace, are what the page lists while
 * the server's do not load, and what the form offers.
 */
@Immutable
data class ProjectsPageState(
    val catalog: ProjectsState = ProjectsState(),
    val offline: Boolean = false,
    val cachedProjects: List<Project> = emptyList(),
    val clients: List<Client> = emptyList(),
    val tasks: TasksState = TasksState(),
) {
    /** Whether the page says that changing the projects needs the server. */
    val serverNeeded: Boolean get() = offline || catalog.loadProblem == ServerProblem.Unreachable

    val canChange: Boolean get() = catalog.canChange && !offline

    /** The change the page says it made last: the open project's tasks' first. */
    val done: CatalogChange? get() = tasks.done ?: catalog.done
}

/**
 * The projects page's state (docs/PRD.md P2): the workspace's projects from the server, loaded
 * each time the page opens or the app comes back to it, and the changes the page sends at once,
 * as [CatalogModel] describes. The form of a project the server sent lists its [tasks] (P5).
 *
 * @param app the app's trips to the background and the network's returns, which load the page and
 *   the open form's tasks again while the page shows ([stateInPage]).
 * @param saved the route's saved state, which keeps the open form and what it holds for a new
 *   process that restores the page.
 */
class ProjectsViewModel(
    private val repository: ServerProjectsRepository,
    tasksRepository: ServerTasksRepository,
    settings: AccountSettingsSource,
    changes: ServerChanges,
    scope: CoroutineScope,
    io: CoroutineContext,
    cachedProjects: Flow<List<Project>>,
    cachedClients: Flow<List<Client>>,
    private val offline: StateFlow<Boolean>,
    app: AppForeground,
    private val saved: SavedStateHandle,
) : ViewModel(scope) {

    private val catalog = CatalogModel<ProjectsData, ProjectForm>(
        repository,
        settings,
        changes,
        viewModelScope,
        io,
        restored = saved.get<String>(SAVED_FORM)?.let(SavedProjectForm::decode)?.form(),
    )

    /** Whether the page has shown, so a form's task list may load. */
    private var pageShown = false

    val tasks = TasksModel(tasksRepository, settings, changes, viewModelScope, io)

    /** The list and what is open over it, before the cache and the network join them in [page]. */
    internal val state: StateFlow<ProjectsState> = catalog.state

    /** The cached rows are empty until the cache is read. */
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
    }.stateInPage(
        viewModelScope,
        ProjectsPageState(state.value, offline.value, tasks = tasks.state.value),
        app,
        object : PageLoads {
            /** The page shows: loads the projects, and the tasks of a form left open or restored. */
            override fun shown() {
                pageShown = true
                catalog.shown()
                tasks.shown()
            }

            override fun returned() {
                catalog.returned()
                tasks.returned()
            }

            override fun reconnected() {
                catalog.reconnected()
                tasks.reconnected()
            }
        },
    )

    init {
        // The form of a project the server sent lists its tasks, while it is open. A form restored
        // before the page shows loads them when it does.
        viewModelScope.launch {
            state.map { (it.overlay as? ProjectForm.Edit)?.project?.id }.distinctUntilChanged().collect { project ->
                when {
                    project == null -> tasks.close()
                    pageShown -> tasks.open(project)
                    else -> tasks.select(project)
                }
            }
        }
        // A new process that restores the page opens the form again as it was.
        viewModelScope.launch {
            state.map { it.overlay }.distinctUntilChanged().collect { form ->
                saved[SAVED_FORM] = form?.let(SavedProjectForm::of)?.encode()
            }
        }
    }

    fun workspace(): WorkspaceId? = catalog.workspace()

    fun retry() = catalog.retry()

    fun doneShown() = catalog.doneShown()

    fun newProject() = catalog.show(ProjectForm.of(null, projectCount()))

    fun edit(project: ProjectDetails) = catalog.show(ProjectForm.of(project, projectCount()))

    fun editDraft(draft: ProjectDraft) = catalog.change { it.holding(draft) }

    fun closeForm() = catalog.dismiss()

    /** Creates the project, or saves the one the form edits, and closes the form once the server accepts it. */
    fun save(fields: ProjectFields) {
        val form = state.value.overlay ?: return
        val known = state.value.data?.projects.orEmpty().mapTo(HashSet()) { it.id }
        catalog.send(
            request = { workspace ->
                when (form) {
                    is ProjectForm.New -> repository.create(workspace, fields, known)
                    is ProjectForm.Edit -> repository.update(workspace, form.project.id, fields)
                }
            },
            apply = ProjectsData::with,
            done = { saved ->
                if (form is ProjectForm.New) {
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
    fun setActive(project: ProjectDetails, active: Boolean) {
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

    /** How many projects the workspace has, as the page loaded them, which picks a new project's colour. */
    private fun projectCount(): Int = state.value.data?.projects?.size ?: 0

    private companion object {
        const val SAVED_FORM = "project-form"
    }
}

/** By name, ignoring case, as Tokkl sorts them. */
internal fun List<ProjectDetails>.sortedForList(): List<ProjectDetails> =
    sortedWith(compareBy<ProjectDetails, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id.value })

/**
 * The project form as the page keeps it in its saved state, so that a new process restoring the
 * page opens it as it was: the project it edits as the server sent it, none for a new one, and
 * what it holds.
 */
@Serializable
internal data class SavedProjectForm(val project: ProjectDetails? = null, val draft: ProjectDraft) {

    fun form(): ProjectForm = project?.let { ProjectForm.Edit(it, draft) } ?: ProjectForm.New(draft)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(form: ProjectForm) = SavedProjectForm((form as? ProjectForm.Edit)?.project, form.draft)

        /** The form [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedProjectForm? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
