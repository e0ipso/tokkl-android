package com.github.e0ipso.tokkl.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.catalog.CatalogNotices
import com.github.e0ipso.tokkl.catalog.CatalogPage
import com.github.e0ipso.tokkl.catalog.CatalogPageEffects
import com.github.e0ipso.tokkl.catalog.DetailLine
import com.github.e0ipso.tokkl.catalog.changeText
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.picker.projectColor
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The projects page (docs/PRD.md P2): the workspace's projects from the server, archived ones in a
 * section of their own, with New project, and Archive or Restore on each row. A tap on a row opens
 * its form, which lists the project's tasks (P5). Each change goes to the server at once. While
 * the phone is offline or the server out of reach, the page lists the projects on the phone and
 * says that changing them needs the server, with every action off. Coming back online, or to the
 * foreground, loads the projects again.
 */
@Composable
fun ProjectsScreen(model: ProjectsModel, modifier: Modifier = Modifier) {
    val offline = rememberOffline()
    CatalogPageEffects(model, offline)
    val state by model.state.collectAsState()
    val cachedProjects by model.cachedProjects.collectAsState(initial = emptyList())
    val cachedClients by model.cachedClients.collectAsState(initial = emptyList())
    val tasks by model.tasks.state.collectAsState()
    TaskListEffect(model.tasks, (state.overlay as? ProjectForm.Edit)?.project?.id)
    val workspace = model.workspace()
    BackHandler(enabled = state.overlay != null) { model.closeForm() }
    ProjectsContent(
        state = state,
        offline = offline,
        cachedProjects = cachedProjects.filter { workspace == null || it.workspaceId == workspace },
        clients = cachedClients.filter { workspace == null || it.workspaceId == workspace },
        actions = ProjectsActions(
            onRetry = model::retry,
            onNew = model::newProject,
            onEdit = model::edit,
            onSetActive = model::setActive,
            onSave = model::save,
            onCloseForm = model::closeForm,
            onDoneShown = model::doneShown,
        ),
        tasks = tasks,
        taskActions = TaskActions(
            onRetry = model.tasks::retry,
            onCreate = model.tasks::create,
            onAskToEdit = model.tasks::askToEdit,
            onSave = model.tasks::save,
            onAskToDelete = model.tasks::askToDelete,
            onDelete = model.tasks::delete,
            onDismiss = model.tasks::dismiss,
            onDoneShown = model.tasks::doneShown,
        ),
        modifier = modifier,
    )
}

/** What the page's controls do. */
internal class ProjectsActions(
    val onRetry: () -> Unit = {},
    val onNew: () -> Unit = {},
    val onEdit: (Project) -> Unit = {},
    val onSetActive: (Project, Boolean) -> Unit = { _, _ -> },
    val onSave: (ProjectFields) -> Unit = {},
    val onCloseForm: () -> Unit = {},
    val onDoneShown: () -> Unit = {},
)

/** [ProjectsScreen] without its model. */
@Composable
internal fun ProjectsContent(
    state: ProjectsState,
    offline: Boolean,
    cachedProjects: List<CachedProject>,
    clients: List<CachedClient>,
    actions: ProjectsActions,
    modifier: Modifier = Modifier,
    tasks: TasksState = TasksState(),
    taskActions: TaskActions = TaskActions(),
) {
    val serverNeeded = offline || state.loadProblem == ServerProblem.Unreachable
    val canChange = state.canChange && !offline
    val done = tasks.done ?: state.done
    val doneShown = {
        actions.onDoneShown()
        taskActions.onDoneShown()
    }
    val form = state.overlay
    val title = when (form) {
        null -> R.string.nav_projects
        ProjectForm.New -> R.string.project_form_new
        is ProjectForm.Edit -> R.string.project_form_edit
    }
    CatalogPage(
        title = stringResource(title),
        done = done?.let { changeText(it) },
        onDoneShown = doneShown,
        modifier = modifier,
        onBack = if (form != null) actions.onCloseForm else null,
    ) {
        if (form != null) {
            ProjectFormContent(
                form = form,
                projectCount = state.data?.projects?.size ?: 0,
                clients = clients,
                defaults = state.data?.defaults,
                canSave = canChange,
                sending = state.sending,
                problem = state.changeProblem,
                serverNeeded = serverNeeded,
                onRetry = actions.onRetry,
                onSave = actions.onSave,
                onCancel = actions.onCloseForm,
                tasks = { project -> ProjectTasks(tasks, project, offline, taskActions) },
            )
        } else {
            ProjectList(
                state = state,
                serverNeeded = serverNeeded,
                canChange = canChange,
                rows = state.data?.projects?.map { it.toRow() } ?: cachedRows(cachedProjects, clients),
                actions = actions,
            )
        }
    }
}

/** A project as the list shows it. [project] is the server's, and null for a row from the phone, which offers no action. */
internal data class ProjectRow(
    val id: Long,
    val name: String,
    val color: String?,
    val clientName: String?,
    val active: Boolean,
    val billable: Boolean,
    val project: Project? = null,
)

private fun Project.toRow() = ProjectRow(id, name, color, clientName, active, billable, project = this)

/** The projects on the phone, with their clients' names, sorted as the server sorts them. */
private fun cachedRows(projects: List<CachedProject>, clients: List<CachedClient>): List<ProjectRow> {
    val names = clients.associate { it.id to it.name }
    return projects.map { ProjectRow(it.id, it.name, it.color, it.clientId?.let(names::get), it.active, it.billable) }
}

@Composable
private fun ProjectList(
    state: ProjectsState,
    serverNeeded: Boolean,
    canChange: Boolean,
    rows: List<ProjectRow>,
    actions: ProjectsActions,
) {
    TokklButton(onClick = actions.onNew, enabled = canChange) { Text(stringResource(R.string.projects_new)) }
    Column(
        modifier = Modifier.padding(top = TokklTheme.spacing.betweenGroups),
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
    ) {
        CatalogNotices(
            serverNeeded = serverNeeded,
            neededTitle = stringResource(R.string.projects_server_needed_title),
            neededMessage = stringResource(R.string.projects_server_needed_message),
            loadProblem = state.loadProblem,
            loading = state.loading && state.data == null,
            loadingText = stringResource(R.string.projects_loading),
            changeProblem = state.changeProblem,
            onRetry = actions.onRetry,
        )
        val (active, archived) = rows.partition { it.active }
        if (state.data != null && rows.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                Text(stringResource(R.string.projects_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.projects_empty_message), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (active.isNotEmpty()) ProjectRows(active, canChange, actions)
        if (archived.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.projects_archived),
                    style = TokklTheme.sectionHeadingTextStyle,
                    modifier = Modifier
                        .padding(bottom = TokklTheme.spacing.inGroup)
                        .semantics { heading() },
                )
                ProjectRows(archived, canChange, actions)
            }
        }
    }
}

@Composable
private fun ProjectRows(rows: List<ProjectRow>, canChange: Boolean, actions: ProjectsActions) {
    Column {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            ProjectListRow(row, canChange, actions)
        }
    }
}

/**
 * One project: its colour beside its name, then its client and whether it is billable or
 * archived, in words. The name side opens the form, and a button archives or restores it. Both
 * stay off for a row from the phone and while nothing can change.
 */
@Composable
private fun ProjectListRow(row: ProjectRow, canChange: Boolean, actions: ProjectsActions) {
    val project = row.project
    val enabled = canChange && project != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.projects_edit),
                    role = Role.Button,
                    onClick = { if (project != null) actions.onEdit(project) },
                )
                .padding(vertical = TokklTheme.spacing.space2),
        ) {
            ProjectColorDot(row.color)
            Column {
                Text(row.name, style = MaterialTheme.typography.bodyLarge)
                DetailLine(
                    listOfNotNull(
                        row.clientName ?: stringResource(R.string.projects_no_client),
                        if (row.billable) stringResource(R.string.projects_billable) else null,
                        if (!row.active) stringResource(R.string.projects_archived) else null,
                    ),
                )
            }
        }
        val label = stringResource(if (row.active) R.string.projects_archive_named else R.string.projects_restore_named, row.name)
        TokklGhostButton(
            onClick = { if (project != null) actions.onSetActive(project, !row.active) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text(stringResource(if (row.active) R.string.projects_archive else R.string.projects_restore))
        }
    }
}

/** The project's colour, beside its name, which carries the meaning; a hollow space when it has none. */
@Composable
internal fun ProjectColorDot(hex: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = TokklTheme.spacing.space3)
            .size(TokklTheme.spacing.space4)
            .background(projectColor(hex) ?: Color.Transparent, CircleShape),
    )
}
