package com.github.e0ipso.tokkl.favorites

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.catalog.CatalogPage
import com.github.e0ipso.tokkl.entry.EntryFieldsEditor
import com.github.e0ipso.tokkl.entry.EntryPicker
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.picked
import com.github.e0ipso.tokkl.entry.withPicked
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * More > Timer templates (docs/PRD.md T5). Ordered rows, and New or Edit forms for the
 * description, project, task, tags and billable. Save and Cancel, Delete, and Move up and
 * down. Changes need the server: offline, or while a save is on its way, the actions are off.
 * Cancel drops the form and leaves the saved list as it was. A failed save leaves that list
 * too, and says why. The project picker uses [workspaceId], the account's default workspace;
 * a template does not store a workspace of its own.
 */
@Composable
fun TemplatesScreen(
    model: FavoritesModel,
    cache: AccountCache,
    workspaceId: Long?,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val projects by cache.projects.collectAsState(initial = emptyList())
    val tasks by cache.tasks.collectAsState(initial = emptyList())
    val offline = rememberOffline()
    val savedText = stringResource(R.string.favorites_saved)
    var done by remember { mutableStateOf<String?>(null) }
    TemplatesContent(
        state = state,
        projects = projects,
        tasks = tasks,
        offline = offline,
        done = done,
        onDoneShown = { done = null },
        onAdd = { fields, onDone ->
            model.add(fields) { problem ->
                if (problem == null) done = savedText
                onDone(problem)
            }
        },
        onReplace = { index, fields, onDone ->
            model.replace(index, fields) { problem ->
                if (problem == null) done = savedText
                onDone(problem)
            }
        },
        onRemove = model::remove,
        onMove = model::move,
        picker = { original, picked, onPicked, pickerModifier ->
            if (workspaceId != null) EntityPicker(cache, workspaceId, original, picked, onPicked, pickerModifier)
        },
        modifier = modifier,
    )
}

/**
 * [TemplatesScreen] without its model. [picker] supplies the project, task and tag picker.
 * [done] is the snackbar after a save the server stored.
 */
@Composable
fun TemplatesContent(
    state: FavoritesState,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    offline: Boolean,
    done: String?,
    onDoneShown: () -> Unit,
    onAdd: (EntryFields, (FavoritesProblem?) -> Unit) -> Unit,
    onReplace: (Int, EntryFields, (FavoritesProblem?) -> Unit) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (index: Int, later: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    picker: EntryPicker = { _, _, _, _ -> },
) {
    var draft by remember { mutableStateOf<TemplateDraft?>(null) }
    val editing = draft
    val canChange = !offline && !state.saving
    CatalogPage(
        title = stringResource(R.string.nav_templates),
        done = done,
        onDoneShown = onDoneShown,
        modifier = modifier,
    ) {
        TemplateFeedback(state, offline, saved = false)
        if (editing == null) {
            if (state.favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for ((index, template) in state.favorites.withIndex()) {
                TemplateRow(
                    template = template,
                    canMoveUp = canChange && index > 0,
                    canMoveDown = canChange && index < state.favorites.lastIndex,
                    canChange = canChange,
                    onMoveUp = { onMove(index, false) },
                    onMoveDown = { onMove(index, true) },
                    onEdit = { draft = TemplateDraft(index, template.fields, template.fields.picked) },
                    onDelete = { onRemove(index) },
                )
            }
            TokklGhostButton(
                onClick = { draft = TemplateDraft(index = null, fields = EntryFields(), original = Picked()) },
                enabled = canChange && !state.full,
            ) {
                Text(stringResource(R.string.favorites_new))
            }
        } else {
            TemplateForm(
                draft = editing,
                projects = projects,
                tasks = tasks,
                enabled = canChange,
                canSave = canChange && (editing.index != null || !state.full),
                canCancel = !state.saving,
                picker = picker,
                onDraft = { draft = it },
                onCancel = { if (!state.saving) draft = null },
                onSave = {
                    val finish: (FavoritesProblem?) -> Unit = { problem -> if (problem == null) draft = null }
                    val index = editing.index
                    if (index == null) onAdd(editing.fields, finish) else onReplace(index, editing.fields, finish)
                },
            )
        }
    }
}

/**
 * A template being created, when [index] is null, or the saved one at [index] being edited.
 * [original] is what the form opened with, so the picker can still offer an archived project.
 */
private data class TemplateDraft(val index: Int?, val fields: EntryFields, val original: Picked)

@Composable
private fun TemplateRow(
    template: FavoriteItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canChange: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val name = templateName(template)
    val details = templateDetails(template)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        if (details != null) {
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowButton(R.drawable.ic_chevron_up, stringResource(R.string.favorites_move_up, name), canMoveUp, onMoveUp)
            RowButton(R.drawable.ic_chevron_down, stringResource(R.string.favorites_move_down, name), canMoveDown, onMoveDown)
            val editSpoken = stringResource(R.string.favorites_edit_spoken, name)
            TextButton(
                onClick = onEdit,
                enabled = canChange,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = editSpoken },
            ) {
                Text(stringResource(R.string.favorites_edit))
            }
            RowButton(R.drawable.ic_close, stringResource(R.string.favorites_delete, name), canChange, onDelete)
        }
    }
}

@Composable
private fun TemplateForm(
    draft: TemplateDraft,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    enabled: Boolean,
    canSave: Boolean,
    canCancel: Boolean,
    picker: EntryPicker,
    onDraft: (TemplateDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val fields = draft.fields
    EntryFieldsEditor(
        fields = fields,
        labels = entryLabels(fields, projects, tasks),
        original = draft.original,
        descriptionHint = stringResource(R.string.favorites_description_hint),
        onDescriptionChange = { text -> onDraft(draft.copy(fields = fields.copy(description = text))) },
        onDescriptionDone = {},
        onPick = { picked -> onDraft(draft.copy(fields = fields.withPicked(picked, projects))) },
        onBillableChange = { billable -> onDraft(draft.copy(fields = fields.copy(billable = billable))) },
        picker = picker,
        enabled = enabled,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
        TokklButton(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.favorites_save_action)) }
        TokklGhostButton(onClick = onCancel, enabled = canCancel) { Text(stringResource(R.string.favorites_cancel)) }
    }
}

@Composable
private fun RowButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = description)
    }
}
