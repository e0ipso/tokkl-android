package com.github.e0ipso.tokkl.tags

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogNotices
import com.github.e0ipso.tokkl.catalog.CatalogPage
import com.github.e0ipso.tokkl.catalog.CatalogPageEffects
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.changeText
import com.github.e0ipso.tokkl.catalog.problemText
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The tags page (docs/PRD.md P4): the workspace's tags from the server, with a field to add one,
 * and on each row a tap to rename it and Delete, which asks first and names the tag. Each change
 * goes to the server at once. While the phone is offline or the server out of reach, the page
 * lists the tags on the phone and says that changing them needs the server, with every action
 * off. Coming back online, or to the foreground, loads the tags again.
 */
@Composable
fun TagsScreen(model: TagsModel, modifier: Modifier = Modifier) {
    val offline = rememberOffline()
    CatalogPageEffects(model, offline)
    val state by model.state.collectAsState()
    val cachedTags by model.cachedTags.collectAsState(initial = emptyList())
    val workspace = model.workspace()
    TagsContent(
        state = state,
        offline = offline,
        cachedTags = cachedTags.filter { workspace == null || it.workspaceId == workspace },
        actions = TagsActions(
            onRetry = model::retry,
            onCreate = model::create,
            onAskToRename = model::askToRename,
            onRename = model::rename,
            onAskToDelete = model::askToDelete,
            onDelete = model::delete,
            onDismiss = model::dismiss,
            onDoneShown = model::doneShown,
        ),
        modifier = modifier,
    )
}

/** What the page's controls do. */
internal class TagsActions(
    val onRetry: () -> Unit = {},
    val onCreate: (String) -> Unit = {},
    val onAskToRename: (Tag) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onAskToDelete: (Tag) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDoneShown: () -> Unit = {},
)

/** [TagsScreen] without its model. */
@Composable
internal fun TagsContent(
    state: TagsState,
    offline: Boolean,
    cachedTags: List<CachedTag>,
    actions: TagsActions,
    modifier: Modifier = Modifier,
) {
    val serverNeeded = offline || state.loadProblem == ServerProblem.Unreachable
    val canChange = state.canChange && !offline
    var newName by rememberSaveable { mutableStateOf("") }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) {
        if (state.done is CatalogChange.Created) newName = ""
    }
    CatalogPage(
        title = stringResource(R.string.nav_tags),
        done = state.done?.let { changeText(it) },
        onDoneShown = actions.onDoneShown,
        modifier = modifier,
    ) {
        NewTagField(
            name = newName,
            nameMissing = nameMissing,
            enabled = canChange,
            onNameChange = {
                newName = it.take(NAME_MAX)
                nameMissing = false
            },
            onAdd = {
                if (newName.isBlank()) nameMissing = true else actions.onCreate(newName)
            },
        )
        Column(
            modifier = Modifier.padding(top = TokklTheme.spacing.betweenGroups),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
        ) {
            CatalogNotices(
                serverNeeded = serverNeeded,
                neededTitle = stringResource(R.string.tags_server_needed_title),
                neededMessage = stringResource(R.string.tags_server_needed_message),
                loadProblem = state.loadProblem,
                loading = state.loading && state.data == null,
                loadingText = stringResource(R.string.tags_loading),
                changeProblem = state.changeProblem.takeIf { state.overlay == null },
                onRetry = actions.onRetry,
            )
            val rows = state.data?.tags?.map { TagRow(it, fromServer = true) }
                ?: cachedTags.map { TagRow(Tag(it.id, it.workspaceId, it.name), fromServer = false) }
            if (state.data != null && rows.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                    Text(stringResource(R.string.tags_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tags_empty_message), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (rows.isNotEmpty()) {
                Column {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider()
                        TagListRow(row, canChange, actions)
                    }
                }
                Text(
                    text = stringResource(R.string.tags_rename_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    when (val overlay = state.overlay) {
        is TagsOverlay.Rename -> RenameDialog(
            tag = overlay.tag,
            canSave = canChange,
            problem = state.changeProblem ?: state.loadProblem,
            onRename = actions.onRename,
            onCancel = actions.onDismiss,
        )
        is TagsOverlay.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.tags_delete_title, overlay.tag.name),
            paragraphs = listOf(stringResource(R.string.tags_delete_message)),
            confirmLabel = stringResource(R.string.tags_delete_confirm),
            canConfirm = canChange,
            problem = state.changeProblem ?: state.loadProblem,
            onConfirm = actions.onDelete,
            onCancel = actions.onDismiss,
            danger = true,
        )
        null -> Unit
    }
}

/** A tag as the list shows it. A row the phone had and the server did not send ([fromServer] false) offers no action. */
private data class TagRow(val tag: Tag, val fromServer: Boolean)

/** The web app's field and Add tag button, which creates a tag at once. */
@Composable
private fun NewTagField(name: String, nameMissing: Boolean, enabled: Boolean, onNameChange: (String) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.tags_new_name)) },
            supportingText = if (nameMissing) {
                { Text(stringResource(R.string.tags_name_missing)) }
            } else {
                null
            },
            isError = nameMissing,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.fillMaxWidth(),
        )
        TokklButton(onClick = onAdd, enabled = enabled) { Text(stringResource(R.string.tags_add)) }
    }
}

/** One tag: a tap on its name renames it, and Delete asks first. Both stay off for a row from the phone and while nothing can change. */
@Composable
private fun TagListRow(row: TagRow, canChange: Boolean, actions: TagsActions) {
    val tag = row.tag
    val enabled = canChange && row.fromServer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.tags_rename),
                    role = Role.Button,
                    onClick = { actions.onAskToRename(tag) },
                )
                .padding(vertical = TokklTheme.spacing.space3),
        )
        val label = stringResource(R.string.tags_delete_named, tag.name)
        TokklGhostButton(
            onClick = { actions.onAskToDelete(tag) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text(stringResource(R.string.tags_delete))
        }
    }
}

/**
 * Renames [tag]: a field with its name, then Cancel and Save, stacked at full width as in
 * ConfirmDialog. An empty name is a mistake under the field; the same name closes the form, as
 * the web app does, with no request.
 */
@Composable
private fun RenameDialog(tag: Tag, canSave: Boolean, problem: ServerProblem?, onRename: (String) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable(tag.id) { mutableStateOf(tag.name) }
    var nameMissing by rememberSaveable(tag.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(tag.id) { focus.requestFocus() }
    val save: () -> Unit = {
        when {
            name.isBlank() -> nameMissing = true
            name.trim() == tag.name -> onCancel()
            else -> onRename(name)
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.tags_rename_title, tag.name), modifier = Modifier.semantics { heading() }) },
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
                    label = { Text(stringResource(R.string.tags_rename_field)) },
                    supportingText = if (nameMissing) {
                        { Text(stringResource(R.string.tags_name_missing)) }
                    } else {
                        null
                    },
                    isError = nameMissing,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Text(stringResource(R.string.tags_rename_note), style = MaterialTheme.typography.bodyMedium)
                problem?.let { ErrorBanner(problemText(it)) }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                TokklGhostButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.catalog_cancel), textAlign = TextAlign.Center)
                }
                TokklButton(onClick = save, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tags_rename_save), textAlign = TextAlign.Center)
                }
            }
        },
    )
}

/** The web app's limit, which Tokkl enforces too. */
private const val NAME_MAX = 255
