package com.github.e0ipso.tokkl.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.flow.combine

/**
 * The project, task and tag picker for one entry (docs/PRD.md P1), which the timer and the entry
 * edit screens share. It reads only the offline [cache], so search and selection work with no
 * connection. Offline it says that creating a project, client or task needs one. A tag typed by
 * name joins the entry by name, online or offline, and the queue sends it so the server creates
 * it. Give the picker a bounded height: its list scrolls.
 *
 * @param original what the entry had when editing started. Its archived project and task, and
 *   its done task, stay on offer with an "Archived" label; no other archived item is offered.
 * @param picked what the entry has now.
 */
@Composable
fun EntityPicker(
    cache: AccountCache,
    workspaceId: Long,
    original: Picked,
    picked: Picked,
    onPickedChange: (Picked) -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = rememberOffline(),
) {
    val rows = remember(cache) {
        combine(cache.projects, cache.clients, cache.tasks, cache.tags) { projects, clients, tasks, tags ->
            PickerSource(projects, clients, tasks, tags)
        }
    }
    val source by rows.collectAsState(initial = PickerSource())
    var query by rememberSaveable { mutableStateOf("") }
    EntityPickerContent(
        options = pickerOptions(workspaceId, original, picked, query, source),
        query = query,
        onQueryChange = { query = it },
        offline = offline,
        onPickProject = { onPickedChange(picked.withProject(it)) },
        onPickTask = { onPickedChange(picked.withTask(it)) },
        onToggleTag = { onPickedChange(picked.withTagToggled(it)) },
        onAddTag = {
            onPickedChange(picked.withTagToggled(it))
            query = ""
        },
        modifier = modifier,
    )
}

@Composable
private fun EntityPickerContent(
    options: PickerOptions,
    query: String,
    onQueryChange: (String) -> Unit,
    offline: Boolean,
    onPickProject: (Long?) -> Unit,
    onPickTask: (CachedTask) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    modifier: Modifier,
) {
    val spacing = TokklTheme.spacing
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (offline) {
            Text(
                text = stringResource(R.string.picker_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.inGroup),
            )
        }
        LazyColumn(Modifier.weight(1f).padding(top = spacing.inGroup)) {
            item(key = "projects") { SectionHeading(stringResource(R.string.picker_project)) }
            projectOptions(options, onPickProject, onPickTask)
            item(key = "tags") {
                SectionHeading(stringResource(R.string.picker_tags), Modifier.padding(top = spacing.betweenGroups))
            }
            items(options.tags, key = { "tag:${it.name}" }) { tag ->
                ChoiceRow(selected = tag.selected, multiple = true, onClick = { onToggleTag(tag.name) }) {
                    Labelled(tag.name, note = if (tag.cached) null else stringResource(R.string.picker_new_tag))
                }
            }
            options.newTag?.let { name ->
                item(key = "add-tag") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                            .clickable(role = Role.Button, onClick = { onAddTag(name) }),
                    ) {
                        Text(stringResource(R.string.picker_add_tag, name), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** "No project", then each client's projects with their tasks under them, as one choice. */
private fun LazyListScope.projectOptions(options: PickerOptions, onPickProject: (Long?) -> Unit, onPickTask: (CachedTask) -> Unit) {
    if (options.noProject) {
        item(key = "no-project") {
            ChoiceRow(selected = options.noProjectSelected, multiple = false, onClick = { onPickProject(null) }) {
                ProjectDot(color = null)
                Labelled(stringResource(R.string.picker_no_project), note = null)
            }
        }
    }
    if (options.groups.isEmpty() && !options.noProject) {
        item(key = "no-match") {
            Text(
                text = stringResource(R.string.picker_no_match),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = TokklTheme.spacing.inGroup),
            )
        }
    }
    options.groups.forEach { group ->
        group.clientName?.let { client ->
            item(key = "client:$client") {
                Labelled(
                    text = client,
                    note = if (group.clientArchived) stringResource(R.string.picker_archived) else null,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(top = TokklTheme.spacing.inGroup)
                        .semantics(mergeDescendants = true) { heading() },
                )
            }
        }
        group.projects.forEach { option ->
            item(key = "project:${option.project.id}") {
                ChoiceRow(selected = option.selected, multiple = false, onClick = { onPickProject(option.project.id) }) {
                    ProjectDot(projectColor(option.project.color))
                    Labelled(option.project.name, note = archivedNote(option.archived))
                }
            }
            option.tasks.forEach { task ->
                item(key = "task:${task.task.id}") {
                    ChoiceRow(
                        selected = task.selected,
                        multiple = false,
                        indent = TokklTheme.spacing.space6,
                        onClick = { onPickTask(task.task) },
                    ) {
                        Labelled(task.task.name, note = archivedNote(task.archived))
                    }
                }
            }
        }
    }
}

@Composable
private fun archivedNote(archived: Boolean): String? = if (archived) stringResource(R.string.picker_archived) else null

/**
 * One option: a radio button, or a checkbox when [multiple], then the label. The whole row is the
 * touch target and one TalkBack item, which reads the label and the selected or checked state.
 */
@Composable
private fun ChoiceRow(
    selected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit,
    indent: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val selection = if (multiple) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .then(selection)
            .padding(start = indent),
    ) {
        if (multiple) Checkbox(checked = selected, onCheckedChange = null) else RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        content()
    }
}

/** [text], with [note] under it, such as "Archived", so a state never rests on colour. */
@Composable
private fun Labelled(
    text: String,
    note: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Column(modifier.padding(vertical = TokklTheme.spacing.space1)) {
        Text(text, style = style)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(bottom = TokklTheme.spacing.space1)
            .semantics { heading() },
    )
}

/** The project's colour beside its name, which carries the meaning; none for "No project". */
@Composable
private fun ProjectDot(color: Color?) {
    Box(
        Modifier
            .size(TokklTheme.spacing.space3)
            .background(color ?: Color.Transparent, CircleShape),
    )
    Spacer(Modifier.width(TokklTheme.spacing.space2))
}

/** A `#rrggbb` project colour, or null when it is not one. */
internal fun projectColor(hex: String?): Color? {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
