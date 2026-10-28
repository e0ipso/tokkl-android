package com.github.e0ipso.tokkl.projects

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogNotices
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.catalog.DetailLine
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.problemText
import com.github.e0ipso.tokkl.ui.CheckboxOptionRow
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.TokklIconButton

/** What the task list's controls do. */
internal class TaskActions(
    val onRetry: () -> Unit = {},
    val onCreate: (String) -> Unit = {},
    val onAskToEdit: (ProjectTask) -> Unit = {},
    val onSave: (name: String, done: Boolean) -> Unit = { _, _ -> },
    val onAskToDelete: (ProjectTask) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDoneShown: () -> Unit = {},
)

/**
 * Opens [project]'s task list while its form is on screen, and closes it when the form goes, but
 * not for a configuration change, so turning the phone sends no request.
 */
@Composable
internal fun TaskListEffect(tasks: TasksModel, project: Long?) {
    val activity = LocalActivity.current
    DisposableEffect(tasks, project) {
        if (project != null) tasks.open(project)
        onDispose { if (project != null && activity?.isChangingConfigurations != true) tasks.close() }
    }
}

/**
 * The tasks of [project] in its form (docs/PRD.md P5), as the web app's ProjectTasks: a field to
 * add one while the project is active, then the open tasks and the done ones. A tap on a task
 * opens a form that renames it and marks it done, and Delete asks first and names the task. Each
 * change goes to the server at once. While the phone is [offline] or the server out of reach,
 * every action is off and the list says that changing tasks needs the server.
 */
@Composable
internal fun ProjectTasks(state: TasksState, project: Project, offline: Boolean, actions: TaskActions) {
    val serverNeeded = offline || state.loadProblem == ServerProblem.Unreachable
    val canChange = state.canChange && !offline
    var newName by rememberSaveable(project.id) { mutableStateOf("") }
    var nameMissing by rememberSaveable(project.id) { mutableStateOf(false) }
    LaunchedEffect(state.done) {
        if (state.done is CatalogChange.Created) newName = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        Text(
            text = stringResource(R.string.project_tasks),
            style = TokklTheme.sectionHeadingTextStyle,
            modifier = Modifier.semantics { heading() },
        )
        if (project.active) {
            NewTaskField(
                name = newName,
                nameMissing = nameMissing,
                enabled = canChange,
                onNameChange = {
                    newName = it.take(NAME_MAX)
                    nameMissing = false
                },
                onAdd = { if (newName.isBlank()) nameMissing = true else actions.onCreate(newName) },
            )
        } else {
            Text(
                text = stringResource(R.string.project_tasks_archived),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CatalogNotices(
            serverNeeded = serverNeeded,
            neededTitle = stringResource(R.string.project_tasks_server_needed_title),
            neededMessage = stringResource(R.string.project_tasks_server_needed_message),
            loadProblem = state.loadProblem,
            loading = state.loading && state.data == null,
            loadingText = stringResource(R.string.project_tasks_loading),
            changeProblem = state.changeProblem.takeIf { state.overlay == null },
            onRetry = actions.onRetry,
        )
        val tasks = state.data?.tasks
        if (tasks != null && tasks.isEmpty()) {
            Text(stringResource(R.string.project_tasks_empty), style = MaterialTheme.typography.bodyLarge)
        }
        if (!tasks.isNullOrEmpty()) {
            Column {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider()
                    TaskRow(task, canChange, actions)
                }
            }
        }
    }
    when (val overlay = state.overlay) {
        is TaskOverlay.Edit -> TaskDialog(
            task = overlay.task,
            canSave = canChange,
            problem = state.changeProblem ?: state.loadProblem,
            onSave = actions.onSave,
            onCancel = actions.onDismiss,
        )
        is TaskOverlay.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.project_task_delete_title, overlay.task.name),
            paragraphs = listOf(stringResource(R.string.project_task_delete_message)),
            confirmLabel = stringResource(R.string.project_task_delete_confirm),
            canConfirm = canChange,
            problem = state.changeProblem ?: state.loadProblem,
            onConfirm = actions.onDelete,
            onCancel = actions.onDismiss,
            danger = true,
        )
        null -> Unit
    }
}

/** The web app's field and Add task button, which adds a task at once. */
@Composable
private fun NewTaskField(name: String, nameMissing: Boolean, enabled: Boolean, onNameChange: (String) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.project_tasks_new_name)) },
            supportingText = if (nameMissing) {
                { Text(stringResource(R.string.project_task_name_missing)) }
            } else {
                null
            },
            isError = nameMissing,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.fillMaxWidth(),
        )
        TokklButton(onClick = onAdd, enabled = enabled) { Text(stringResource(R.string.project_tasks_add)) }
    }
}

/** One task, with Done under a done one: a tap on it opens its form, and Delete asks first. Both stay off while nothing can change. */
@Composable
private fun TaskRow(task: ProjectTask, canChange: Boolean, actions: TaskActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        modifier = Modifier.fillMaxWidth().padding(vertical = TokklTheme.spacing.space1),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(
                    enabled = canChange,
                    onClickLabel = stringResource(R.string.project_task_edit),
                    role = Role.Button,
                    onClick = { actions.onAskToEdit(task) },
                )
                .padding(vertical = TokklTheme.spacing.space2),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(task.name, style = MaterialTheme.typography.bodyLarge)
            if (!task.active) DetailLine(listOf(stringResource(R.string.project_task_done)))
        }
        val label = stringResource(R.string.project_task_delete_named, task.name)
        TokklIconButton(
            icon = R.drawable.ic_nav_trash,
            description = label,
            onClick = { actions.onAskToDelete(task) },
            enabled = canChange,
            danger = true,
        )
    }
}

/**
 * Edits [task]: a field with its name and Done, then Cancel and Save, stacked at full width as in
 * ConfirmDialog. An empty name is a mistake under the field; no change closes the form with no
 * request.
 */
@Composable
private fun TaskDialog(
    task: ProjectTask,
    canSave: Boolean,
    problem: ServerProblem?,
    onSave: (name: String, done: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(task.id) { mutableStateOf(task.name) }
    var done by rememberSaveable(task.id) { mutableStateOf(!task.active) }
    var nameMissing by rememberSaveable(task.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(task.id) { focus.requestFocus() }
    val save: () -> Unit = {
        when {
            name.isBlank() -> nameMissing = true
            name.trim() == task.name && done == !task.active -> onCancel()
            else -> onSave(name, done)
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.project_task_edit_title, task.name), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(NAME_MAX)
                        nameMissing = false
                    },
                    label = { Text(stringResource(R.string.project_task_name)) },
                    supportingText = if (nameMissing) {
                        { Text(stringResource(R.string.project_task_name_missing)) }
                    } else {
                        null
                    },
                    isError = nameMissing,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                CheckboxOptionRow(label = stringResource(R.string.project_task_done), checked = done, onCheckedChange = { done = it })
                problem?.let { ErrorBanner(problemText(it)) }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                TokklGhostButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.catalog_cancel), textAlign = TextAlign.Center)
                }
                TokklButton(onClick = save, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.project_task_save), textAlign = TextAlign.Center)
                }
            }
        },
    )
}

/** The web app's limit, which Tokkl enforces too. */
private const val NAME_MAX = 255
