package com.github.e0ipso.tokkl.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.catalog.CatalogNotices
import com.github.e0ipso.tokkl.catalog.CatalogPage
import com.github.e0ipso.tokkl.catalog.CatalogPageEffects
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.catalog.DetailLine
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.formatTime
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The trash page (docs/PRD.md H7), as the web app's TrashPage: the deleted entries from the
 * server, the most recently deleted first, each with Restore and Delete forever, and Empty trash,
 * which asks first. Every change goes to the server at once. The trash lives only on the server,
 * so while the phone is offline or the server out of reach, the page says it needs the server and
 * every action is off. Coming back online, or to the foreground, loads the trash again.
 */
@Composable
fun TrashScreen(model: TrashModel, modifier: Modifier = Modifier) {
    val offline = rememberOffline()
    CatalogPageEffects(model, offline)
    val state by model.state.collectAsState()
    val settings by model.settings.collectAsState()
    TrashContent(
        state = state,
        offline = offline,
        settings = settings,
        actions = TrashActions(
            onRetry = model::retry,
            onRestore = model::restore,
            onPurge = model::purge,
            onAskToEmpty = model::askToEmpty,
            onEmpty = model::empty,
            onDismiss = model::dismiss,
            onDoneShown = model::doneShown,
        ),
        modifier = modifier,
    )
}

/** What the page's controls do. */
internal class TrashActions(
    val onRetry: () -> Unit = {},
    val onRestore: (Long) -> Unit = {},
    val onPurge: (Long) -> Unit = {},
    val onAskToEmpty: () -> Unit = {},
    val onEmpty: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDoneShown: () -> Unit = {},
)

/** [TrashScreen] without its model. */
@Composable
internal fun TrashContent(
    state: TrashState,
    offline: Boolean,
    settings: AccountSettings?,
    actions: TrashActions,
    modifier: Modifier = Modifier,
) {
    val serverNeeded = offline || state.loadProblem == ServerProblem.Unreachable
    val canChange = state.canChange && !offline
    // Entries show only once loaded: the phone keeps no copy of the trash.
    val entries = state.entries.takeUnless { serverNeeded }
    val time = remember(settings) { settings?.time() }
    val hasNotices = serverNeeded || state.loadProblem != null ||
        (state.loading && state.entries == null) || (state.changeProblem != null && !state.confirmingEmpty)
    CatalogPage(
        title = stringResource(R.string.nav_trash),
        done = state.done?.let { changeText(it) },
        onDoneShown = actions.onDoneShown,
        modifier = modifier,
        lazyContent = {
            item(key = "intro") {
                Column {
                    Text(
                        text = stringResource(R.string.trash_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TokklGhostButton(
                        onClick = actions.onAskToEmpty,
                        enabled = canChange && !entries.isNullOrEmpty(),
                        modifier = Modifier.padding(top = TokklTheme.spacing.inGroup),
                    ) {
                        Text(stringResource(R.string.trash_empty_trash))
                    }
                }
            }
            item(key = "notices") {
                Column(Modifier.padding(top = TokklTheme.spacing.betweenGroups)) {
                    CatalogNotices(
                        serverNeeded = serverNeeded,
                        neededTitle = stringResource(R.string.trash_server_needed_title),
                        neededMessage = stringResource(R.string.trash_server_needed_message),
                        loadProblem = state.loadProblem,
                        loading = state.loading && state.entries == null,
                        loadingText = stringResource(R.string.trash_loading),
                        changeProblem = state.changeProblem.takeIf { !state.confirmingEmpty },
                        onRetry = actions.onRetry,
                    )
                    if (hasNotices && entries != null) Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
                }
            }
            if (entries != null && entries.isEmpty()) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                        Text(stringResource(R.string.trash_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.trash_empty_message), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (!entries.isNullOrEmpty()) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                    Column {
                        if (index > 0) HorizontalDivider()
                        TrashRow(entry, time, settings?.durationFormat ?: DurationFormat.IMPROVED, settings?.twelveHour == true, canChange, actions)
                    }
                }
                if (entries.size >= TrashServer.LIST_LIMIT) {
                    item(key = "limit") {
                        Text(
                            text = stringResource(R.string.trash_limit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = TokklTheme.spacing.betweenGroups),
                        )
                    }
                }
            }
        },
    )
    if (state.confirmingEmpty) {
        val count = state.entries?.size ?: 0
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_confirm_title),
            paragraphs = listOf(
                if (count >= TrashServer.LIST_LIMIT) {
                    stringResource(R.string.trash_empty_confirm_every)
                } else {
                    pluralStringResource(R.plurals.trash_empty_confirm_message, count, count)
                },
            ),
            confirmLabel = stringResource(R.string.trash_empty_trash),
            canConfirm = canChange,
            problem = state.changeProblem ?: state.loadProblem,
            onConfirm = actions.onEmpty,
            onCancel = actions.onDismiss,
            danger = true,
        )
    }
}

/** What [change] did, in the web app's words. */
@Composable
private fun changeText(change: TrashChange): String = when (change) {
    TrashChange.Restored -> stringResource(R.string.trash_restored)
    TrashChange.Purged -> stringResource(R.string.trash_purged)
    is TrashChange.Emptied -> pluralStringResource(R.plurals.trash_emptied, change.count, change.count)
}

/**
 * One deleted entry: its description, then its project, when it started, how long it lasted and
 * when it was deleted, in words, which TalkBack reads as one item. Without [time], the account's
 * timezone, the dates and times stay out. Under them Restore and Delete forever, which name the
 * entry to TalkBack and wrap onto two lines when the font is large.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrashRow(
    entry: TrashEntry,
    time: AccountTime?,
    format: DurationFormat,
    twelveHour: Boolean,
    canChange: Boolean,
    actions: TrashActions,
) {
    val resources = LocalResources.current
    val words = remember(resources) { DateWords(resources) }
    val described = !entry.description.isNullOrBlank()
    val name = if (described) entry.description.orEmpty() else stringResource(R.string.trash_no_description)
    val details = buildList {
        add(entry.projectName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.trash_no_project))
        if (time != null) {
            parseInstant(entry.start)?.let { add(stringResource(R.string.trash_started, date(it, time, words), formatTime(it, time.zone, twelveHour))) }
        }
        add(if (entry.stop == null || entry.duration < 0) stringResource(R.string.trash_running) else formatDuration(entry.duration, format))
        if (time != null) {
            entry.deletedAt?.let(::parseInstant)?.let { add(stringResource(R.string.trash_deleted_at, date(it, time, words), formatTime(it, time.zone, twelveHour))) }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TokklTheme.spacing.space2),
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (described) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DetailLine(details)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
        ) {
            val restoreLabel = stringResource(R.string.trash_restore_named, name)
            TokklGhostButton(
                onClick = { actions.onRestore(entry.id) },
                enabled = canChange,
                modifier = Modifier.semantics { contentDescription = restoreLabel },
            ) {
                Text(stringResource(R.string.trash_restore))
            }
            val purgeLabel = stringResource(R.string.trash_purge_named, name)
            TokklGhostButton(
                onClick = { actions.onPurge(entry.id) },
                enabled = canChange,
                modifier = Modifier.semantics { contentDescription = purgeLabel },
            ) {
                Text(stringResource(R.string.trash_purge))
            }
        }
    }
}

/** The day of [instant] in the account's zone, with the year when it is not this year's. */
private fun date(instant: Instant, time: AccountTime, words: DateWords): String {
    val day = time.local(instant).toLocalDate()
    return if (day.year == time.today().year) words.short(day) else words.shortWithYear(day)
}

/** Tokkl writes `+00:00` and Toggl `Z`; both parse here. Null for anything else. */
private fun parseInstant(text: String): Instant? =
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }
