package com.github.e0ipso.tokkl.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.problemText
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The timer panel's timer-templates icon (docs/PRD.md T5). It opens a popup of the cached
 * templates in [state], in saved order. Choosing one dismisses the popup and calls [onStart].
 * Save as template stores [timerFields]. Manage templates calls [onManage]. Without the
 * capability, nothing shows. [onSave]'s callback hears how the save went, as [FavoritesModel.add]
 * reports it.
 */
@Composable
fun TemplatesPopup(
    state: FavoritesState,
    timerFields: EntryFields?,
    onStart: (EntryFields) -> Unit,
    onSave: (EntryFields, (FavoritesProblem?) -> Unit) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = rememberOffline(),
) {
    if (!state.available) return
    var open by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (!open) saved = false }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_templates),
                contentDescription = stringResource(R.string.favorites_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TokklTheme.spacing.space3, vertical = TokklTheme.spacing.space2),
                )
            } else {
                for (template in state.favorites) {
                    val name = templateName(template)
                    val project = template.labels.project.takeIf { template.fields.description.isNotEmpty() }
                    val spoken = templateStartSpoken(template)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(name)
                                if (project != null) {
                                    Text(
                                        text = project,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                        onClick = {
                            open = false
                            onStart(template.fields)
                        },
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
                    )
                }
                HorizontalDivider()
            }
            TemplateFeedback(state, offline, saved)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites_save)) },
                enabled = timerFields != null && !offline && !state.saving && !state.full,
                onClick = {
                    val fields = timerFields ?: return@DropdownMenuItem
                    onSave(fields) { problem -> if (problem == null) saved = true }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites_manage)) },
                onClick = {
                    open = false
                    onManage()
                },
            )
        }
    }
}

/** The description, else the project, else Untitled. */
@Composable
internal fun templateName(template: FavoriteItem): String =
    template.fields.description.ifEmpty { template.labels.project ?: stringResource(R.string.favorites_untitled) }

/** What TalkBack reads for a template: its name, and its project when the name is the description. */
@Composable
internal fun templateSpoken(name: String, project: String?): String =
    project?.let { stringResource(R.string.favorites_with_project, name, it) } ?: name

/** What TalkBack reads for starting [template]. */
@Composable
internal fun templateStartSpoken(template: FavoriteItem): String {
    val name = templateName(template)
    val project = template.labels.project.takeIf { template.fields.description.isNotEmpty() }
    return stringResource(R.string.favorites_start_spoken, templateSpoken(name, project))
}

/** Capacity, offline, server and save feedback for a template change. */
@Composable
internal fun TemplateFeedback(state: FavoritesState, offline: Boolean, saved: Boolean) {
    val problem = state.problem
    val padding = Modifier.padding(horizontal = TokklTheme.spacing.space3, vertical = TokklTheme.spacing.space2)
    if (problem is FavoritesProblem.Server && problem.problem != ServerProblem.Unreachable && !offline) {
        ErrorBanner(problemText(problem.problem), modifier = Modifier.padding(TokklTheme.spacing.space2))
    }
    val warning = when {
        offline || (problem as? FavoritesProblem.Server)?.problem == ServerProblem.Unreachable ->
            stringResource(R.string.favorites_offline)
        saved && problem == null -> null
        problem is FavoritesProblem.Full || state.full ->
            pluralStringResource(R.plurals.favorites_full, MAX_FAVORITES, MAX_FAVORITES)
        else -> null
    }
    if (warning != null) TemplateNotice(warning, padding)
    if (saved && problem == null) {
        Text(
            text = stringResource(R.string.history_pinned),
            style = MaterialTheme.typography.bodyMedium,
            modifier = padding.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** A sentence in a polite live region, so TalkBack reads it when it appears, after a warning icon. */
@Composable
internal fun TemplateNotice(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** A template row's project and task, when the title is not already the project name. */
@Composable
internal fun templateDetails(template: FavoriteItem): String? {
    val fields = template.fields
    val project = template.labels.project
    val parts = buildList {
        if (fields.description.isNotEmpty() && project != null) {
            val task = template.labels.task
            add(if (task == null) project else stringResource(R.string.history_project_task, project, task))
        }
        if (fields.tags.isNotEmpty()) add(fields.tags.joinToString(", "))
        if (fields.billable) add(stringResource(R.string.history_billable))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
