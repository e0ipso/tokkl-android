package com.github.e0ipso.tokkl.clients

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogNotices
import com.github.e0ipso.tokkl.catalog.CatalogPage
import com.github.e0ipso.tokkl.catalog.CatalogPageEffects
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.catalog.DetailLine
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.changeText
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The clients page (docs/PRD.md P3): the workspace's clients from the server, archived ones in a
 * section of their own and each marked Archived in words, with New client, and Archive or Restore
 * on each row. Archiving asks first, since it archives the client's projects too; restoring
 * brings them back. A tap on a row opens its form. Each change goes to the server at once. While
 * the phone is offline or the server out of reach, the page lists the clients on the phone and
 * says that changing them needs the server, with every action off. Coming back online, or to the
 * foreground, loads the clients again.
 */
@Composable
fun ClientsScreen(model: ClientsModel, modifier: Modifier = Modifier) {
    val offline = rememberOffline()
    CatalogPageEffects(model, offline)
    val state by model.state.collectAsState()
    val cachedClients by model.cachedClients.collectAsState(initial = emptyList())
    val workspace = model.workspace()
    BackHandler(enabled = state.overlay is ClientsOverlay.New || state.overlay is ClientsOverlay.Edit) { model.dismiss() }
    ClientsContent(
        state = state,
        offline = offline,
        cachedClients = cachedClients.filter { workspace == null || it.workspaceId == workspace },
        actions = ClientsActions(
            onRetry = model::retry,
            onNew = model::newClient,
            onEdit = model::edit,
            onAskToArchive = model::askToArchive,
            onArchive = model::archive,
            onRestore = model::restore,
            onSave = model::save,
            onDismiss = model::dismiss,
            onDoneShown = model::doneShown,
        ),
        modifier = modifier,
    )
}

/** What the page's controls do. */
internal class ClientsActions(
    val onRetry: () -> Unit = {},
    val onNew: () -> Unit = {},
    val onEdit: (Client) -> Unit = {},
    val onAskToArchive: (Client) -> Unit = {},
    val onArchive: () -> Unit = {},
    val onRestore: (Client) -> Unit = {},
    val onSave: (String, String) -> Unit = { _, _ -> },
    val onDismiss: () -> Unit = {},
    val onDoneShown: () -> Unit = {},
)

/** [ClientsScreen] without its model. */
@Composable
internal fun ClientsContent(
    state: ClientsState,
    offline: Boolean,
    cachedClients: List<CachedClient>,
    actions: ClientsActions,
    modifier: Modifier = Modifier,
) {
    val serverNeeded = offline || state.loadProblem == ServerProblem.Unreachable
    val canChange = state.canChange && !offline
    val formOpen = state.overlay is ClientsOverlay.New || state.overlay is ClientsOverlay.Edit
    val title = when (state.overlay) {
        ClientsOverlay.New -> R.string.client_form_new
        is ClientsOverlay.Edit -> R.string.client_form_edit
        is ClientsOverlay.ConfirmArchive, null -> R.string.nav_clients
    }
    CatalogPage(
        title = stringResource(title),
        done = state.done?.let { clientChangeText(it) },
        onDoneShown = actions.onDoneShown,
        modifier = modifier,
        onBack = if (formOpen) actions.onDismiss else null,
    ) {
        when (val overlay = state.overlay) {
            ClientsOverlay.New, is ClientsOverlay.Edit -> ClientFormContent(
                client = (overlay as? ClientsOverlay.Edit)?.client,
                canSave = canChange,
                sending = state.sending,
                problem = state.changeProblem,
                serverNeeded = serverNeeded,
                onRetry = actions.onRetry,
                onSave = actions.onSave,
                onCancel = actions.onDismiss,
            )
            is ClientsOverlay.ConfirmArchive, null -> ClientList(
                state = state,
                serverNeeded = serverNeeded,
                canChange = canChange,
                rows = state.data?.let { data -> data.clients.map { ClientRow(it, data.projectCount(it)) } }
                    ?: cachedClients.map { ClientRow(Client(it.id, it.workspaceId, it.name, it.archived), projects = null, fromServer = false) },
                actions = actions,
            )
        }
    }
    val confirm = state.overlay as? ClientsOverlay.ConfirmArchive
    if (confirm != null) {
        ArchiveQuestion(
            client = confirm.client,
            projects = state.data?.projectCount(confirm.client) ?: 0,
            canConfirm = canChange,
            problem = state.changeProblem ?: state.loadProblem,
            onConfirm = actions.onArchive,
            onCancel = actions.onDismiss,
        )
    }
}

/** The web app's messages, which say that archiving and restoring a client do the same to its projects. */
@Composable
private fun clientChangeText(change: CatalogChange): String = when (change) {
    is CatalogChange.Archived -> stringResource(R.string.clients_archived_done, change.name)
    is CatalogChange.Restored -> stringResource(R.string.clients_restored_done, change.name)
    else -> changeText(change)
}

/**
 * A client as the list shows it, with its number of [projects], null when unknown. A row the
 * phone had and the server did not send ([fromServer] false) offers no action.
 */
private data class ClientRow(val client: Client, val projects: Int?, val fromServer: Boolean = true)

@Composable
private fun ClientList(
    state: ClientsState,
    serverNeeded: Boolean,
    canChange: Boolean,
    rows: List<ClientRow>,
    actions: ClientsActions,
) {
    TokklButton(onClick = actions.onNew, enabled = canChange) { Text(stringResource(R.string.clients_new)) }
    Column(
        modifier = Modifier.padding(top = TokklTheme.spacing.betweenGroups),
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
    ) {
        CatalogNotices(
            serverNeeded = serverNeeded,
            neededTitle = stringResource(R.string.clients_server_needed_title),
            neededMessage = stringResource(R.string.clients_server_needed_message),
            loadProblem = state.loadProblem,
            loading = state.loading && state.data == null,
            loadingText = stringResource(R.string.clients_loading),
            changeProblem = state.changeProblem.takeIf { state.overlay == null },
            onRetry = actions.onRetry,
        )
        if (state.data != null && rows.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                Text(stringResource(R.string.clients_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.clients_empty_message), style = MaterialTheme.typography.bodyLarge)
            }
        }
        val (active, archived) = rows.partition { !it.client.archived }
        if (active.isNotEmpty()) ClientRows(active, canChange, actions)
        if (archived.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.clients_archived),
                    style = TokklTheme.sectionHeadingTextStyle,
                    modifier = Modifier
                        .padding(bottom = TokklTheme.spacing.inGroup)
                        .semantics { heading() },
                )
                ClientRows(archived, canChange, actions)
            }
        }
    }
}

@Composable
private fun ClientRows(rows: List<ClientRow>, canChange: Boolean, actions: ClientsActions) {
    Column {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            ClientListRow(row, canChange, actions)
        }
    }
}

/**
 * One client: its name, then its notes, its number of projects and, when archived, the word
 * Archived. The name side opens the form, and a button archives or restores it. Both stay off for
 * a row from the phone and while nothing can change.
 */
@Composable
private fun ClientListRow(row: ClientRow, canChange: Boolean, actions: ClientsActions) {
    val client = row.client
    val enabled = canChange && row.fromServer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.clients_edit),
                    role = Role.Button,
                    onClick = { actions.onEdit(client) },
                )
                .padding(vertical = TokklTheme.spacing.space2),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(client.name, style = MaterialTheme.typography.bodyLarge)
            client.notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = NOTES_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DetailLine(
                listOfNotNull(
                    row.projects?.let { pluralStringResource(R.plurals.clients_projects, it, it) },
                    if (client.archived) stringResource(R.string.clients_archived) else null,
                ),
            )
        }
        val label = stringResource(if (client.archived) R.string.clients_restore_named else R.string.clients_archive_named, client.name)
        TokklGhostButton(
            onClick = { if (client.archived) actions.onRestore(client) else actions.onAskToArchive(client) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text(stringResource(if (client.archived) R.string.clients_restore else R.string.clients_archive))
        }
    }
}

/** The web app's question before archiving [client], which says what happens to its [projects]. */
@Composable
private fun ArchiveQuestion(
    client: Client,
    projects: Int,
    canConfirm: Boolean,
    problem: ServerProblem?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.clients_archive_title, client.name),
        paragraphs = listOf(
            if (projects == 0) {
                stringResource(R.string.clients_archive_no_projects)
            } else {
                pluralStringResource(R.plurals.clients_archive_projects, projects, projects)
            },
            stringResource(R.string.clients_archive_restore_note),
        ),
        confirmLabel = stringResource(R.string.clients_archive),
        canConfirm = canConfirm,
        problem = problem,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

private const val NOTES_LINES = 2
