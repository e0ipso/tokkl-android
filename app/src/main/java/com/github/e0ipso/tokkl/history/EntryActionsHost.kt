package com.github.e0ipso.tokkl.history

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.session.AccountSettings

/**
 * The messages, the question and the dialog of the history's entry actions: a snackbar for
 * [EntryActionsState.notice], such as "Entry deleted" with Undo or "Entry duplicated", the question
 * before a delete that cannot be undone, and the split dialog, which needs the account's [settings].
 * The snackbar is a polite live region, as Material's SnackbarHost makes each one, so TalkBack
 * reads the message and its Undo button when it appears. A snackbar with Undo stays for
 * Material's long duration, which grows as Android's accessibility timeout asks.
 */
@Composable
internal fun EntryActionsHost(
    state: EntryActionsState,
    onUndo: (Long) -> Unit,
    onNoticeDone: (EntryNotice) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
    settings: AccountSettings? = null,
    onSplitTextChange: (String) -> Unit = {},
    onConfirmSplit: () -> Unit = {},
    onCancelSplit: () -> Unit = {},
) {
    val host = remember { SnackbarHostState() }
    val notice = state.notice
    val message = notice?.let { noticeText(it) }
    val undoLabel = stringResource(R.string.history_undo)
    val undo by rememberUpdatedState(onUndo)
    val done by rememberUpdatedState(onNoticeDone)
    LaunchedEffect(notice) {
        if (notice == null || message == null) return@LaunchedEffect
        val undoes = notice.undoes()
        val result = host.showSnackbar(
            message = message,
            actionLabel = undoLabel.takeIf { undoes != null },
            withDismissAction = true,
            duration = if (undoes != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && undoes != null) undo(undoes) else done(notice)
    }
    SnackbarHost(host, modifier)
    if (state.confirmDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_confirm_title),
            paragraphs = listOf(stringResource(R.string.history_delete_confirm_message)),
            confirmLabel = stringResource(R.string.history_delete_confirm),
            canConfirm = true,
            problem = null,
            onConfirm = onConfirmDelete,
            onCancel = onCancelDelete,
            danger = true,
        )
    }
    val split = state.split
    if (split != null && settings != null) {
        SplitEntryDialog(split, settings, onTextChange = onSplitTextChange, onConfirm = onConfirmSplit, onCancel = onCancelSplit)
    }
}

/** The entry whose delete Undo takes back, or null when the notice offers no Undo. */
private fun EntryNotice.undoes(): Long? = when (this) {
    is EntryNotice.Deleted -> entryId.takeIf { undo }
    is EntryNotice.UndoNeedsServer -> entryId
    is EntryNotice.UndoFailed, is EntryNotice.Duplicated, is EntryNotice.Split -> null
}

@Composable
private fun noticeText(notice: EntryNotice): String = when (notice) {
    is EntryNotice.Deleted -> stringResource(R.string.history_deleted)
    is EntryNotice.UndoNeedsServer -> stringResource(R.string.history_undo_needs_server)
    is EntryNotice.UndoFailed -> notice.status?.let { stringResource(R.string.history_undo_failed_status, it) }
        ?: stringResource(R.string.history_undo_failed)
    is EntryNotice.Duplicated -> stringResource(R.string.history_duplicated)
    is EntryNotice.Split -> stringResource(R.string.history_split_done)
}
