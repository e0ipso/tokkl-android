package com.github.e0ipso.tokkl.reports

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.time.jsTrim
import com.github.e0ipso.tokkl.ui.CheckboxOptionRow
import com.github.e0ipso.tokkl.ui.FillScreenWindow
import com.github.e0ipso.tokkl.ui.RadioOptionRow
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * The Filters button under the range. It names every active filter in words, so the filters show
 * as text and not only as a changed control, and it opens the filter sheet. The sheet offers
 * [options], which come from the phone's cache, so opening it sends nothing.
 */
@Composable
internal fun FilterControl(
    filters: ReportFilters,
    options: FilterOptions,
    onApply: (ReportFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val changeLabel = stringResource(R.string.reports_filters_change)
    OutlinedButton(
        onClick = { open = true },
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .semantics { onClick(label = changeLabel) { open = true; true } },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.reports_filters), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            for (line in filterLines(filters, options)) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (open) {
        FilterSheet(
            initial = filters,
            options = options,
            onApply = { open = false; onApply(it) },
            onDismiss = { open = false },
        )
    }
}

/**
 * One line per active filter, such as "Project: Website, Internal", "Tag: not meeting" or
 * "Billable only". A filter that holds an id the cache no longer has reads as the web app's
 * "2 selected".
 */
@Composable
private fun filterLines(filters: ReportFilters, options: FilterOptions): List<String> = buildList {
    idLine(R.string.reports_filter_project, R.string.reports_without_project, filters.projects, filters.projectsNot, options.projects)?.let(::add)
    idLine(R.string.reports_filter_client, R.string.reports_filter_without_client, filters.clients, filters.clientsNot, options.clients)?.let(::add)
    idLine(R.string.reports_filter_tag, R.string.reports_filter_without_tags, filters.tags, filters.tagsNot, options.tags)?.let(::add)
    when (filters.billable) {
        BillableFilter.ALL -> Unit
        BillableFilter.BILLABLE, BillableFilter.NON_BILLABLE -> add(stringResource(BILLABLE_LABELS.getValue(filters.billable)))
    }
}

@Composable
private fun idLine(@StringRes name: Int, @StringRes without: Int, ids: List<Long?>, negated: Boolean, options: List<FilterOption>): String? {
    if (ids.isEmpty()) return null
    val names = options.associate { it.id to it.name }
    val withoutName = stringResource(without)
    val picked = ids.map { id -> if (id == null) withoutName else names[id] }
    val value = if (picked.any { it == null }) {
        pluralStringResource(R.plurals.reports_filter_selected, ids.size, ids.size)
    } else {
        picked.filterNotNull().joinToString(", ")
    }
    return stringResource(if (negated) R.string.reports_filter_line_not else R.string.reports_filter_line, stringResource(name), value)
}

/**
 * The filters on a full screen, as the custom range, since a long list of projects needs the
 * room. Each list starts with its "without" choice, as in the web app, under the web app's "Is"
 * and "Is not" where the server honours them. The search field above the lists narrows the
 * projects, clients and tags by name, as the web app's "Find project…" does, and asks nothing.
 * Nothing applies until Apply, so the report is asked for once; the close button leaves the
 * filters as they were.
 */
@Composable
private fun FilterSheet(initial: ReportFilters, options: FilterOptions, onApply: (ReportFilters) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable(stateSaver = FILTERS_SAVER) { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.jsTrim()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FillScreenWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = TokklTheme.spacing.space1),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.reports_cancel))
                    }
                    Text(
                        text = stringResource(R.string.reports_filters),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    TextButton(onClick = { onApply(if (options.negation) draft else draft.withoutNegation()) }) {
                        Text(stringResource(R.string.reports_apply))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.reports_filter_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .screenContent()
                        .fillMaxWidth()
                        .padding(bottom = TokklTheme.spacing.inGroup),
                )
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    idSection(
                        key = "project",
                        heading = R.string.reports_filter_project,
                        without = R.string.reports_without_project,
                        options = options.projects.matching(search, draft.projects),
                        picked = draft.projects,
                        negated = draft.projectsNot.takeIf { options.negation },
                        onNegatedChange = { draft = draft.copy(projectsNot = it) },
                        onToggle = { draft = draft.copy(projects = draft.projects.toggled(it)) },
                    )
                    idSection(
                        key = "client",
                        heading = R.string.reports_filter_client,
                        without = R.string.reports_filter_without_client,
                        options = options.clients.matching(search, draft.clients),
                        picked = draft.clients,
                        negated = draft.clientsNot.takeIf { options.negation },
                        onNegatedChange = { draft = draft.copy(clientsNot = it) },
                        onToggle = { draft = draft.copy(clients = draft.clients.toggled(it)) },
                    )
                    idSection(
                        key = "tag",
                        heading = R.string.reports_filter_tag,
                        without = R.string.reports_filter_without_tags,
                        options = options.tags.matching(search, draft.tags),
                        picked = draft.tags,
                        negated = draft.tagsNot.takeIf { options.negation },
                        onNegatedChange = { draft = draft.copy(tagsNot = it) },
                        onToggle = { draft = draft.copy(tags = draft.tags.toggled(it)) },
                    )
                    item(key = "billable") {
                        Column(Modifier.screenContent()) {
                            SectionHeading(R.string.reports_filter_billable)
                            // Radio buttons: the selected one differs in shape, not only in colour.
                            Column(Modifier.selectableGroup()) {
                                for (choice in BillableFilter.entries) {
                                    RadioOptionRow(
                                        label = stringResource(BILLABLE_LABELS.getValue(choice)),
                                        selected = draft.billable == choice,
                                        onSelect = { draft = draft.copy(billable = choice) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "clear") {
                        TextButton(
                            onClick = { draft = ReportFilters() },
                            enabled = draft != ReportFilters(),
                            modifier = Modifier
                                .screenContent()
                                .padding(vertical = TokklTheme.spacing.inGroup),
                        ) {
                            Text(stringResource(R.string.reports_filters_clear))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A heading, "Is" and "Is not" unless [negated] is null, the "without" choice and a checkbox for
 * each option, each ticked when [picked] holds its id.
 */
private fun LazyListScope.idSection(
    key: String,
    @StringRes heading: Int,
    @StringRes without: Int,
    options: List<FilterOption>,
    picked: List<Long?>,
    negated: Boolean?,
    onNegatedChange: (Boolean) -> Unit,
    onToggle: (Long?) -> Unit,
) {
    item(key = "$key-heading") { SectionHeading(heading, Modifier.screenContent()) }
    if (negated != null) {
        item(key = "$key-mode") { ModeSwitch(heading, negated, onNegatedChange, Modifier.screenContent()) }
    }
    item(key = "$key-none") {
        CheckboxOptionRow(
            label = stringResource(without),
            checked = null in picked,
            onCheckedChange = { onToggle(null) },
            modifier = Modifier.screenContent(),
        )
    }
    items(options, key = { "$key-${it.id}" }) { option ->
        CheckboxOptionRow(
            label = option.name,
            checked = option.id in picked,
            onCheckedChange = { onToggle(option.id) },
            modifier = Modifier.screenContent(),
        )
    }
}

/**
 * The web app's "Is" and "Is not" for the filter named [filter]. Each segment is a radio button
 * to TalkBack, read with the filter's name, and the chosen one carries a tick, so its colour is
 * not the only sign.
 */
@Composable
private fun ModeSwitch(@StringRes filter: Int, negated: Boolean, onNegatedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(filter)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(bottom = TokklTheme.spacing.inGroup)) {
        for ((index, mode) in listOf(false, true).withIndex()) {
            val description = stringResource(
                if (mode) R.string.reports_filter_mode_not_description else R.string.reports_filter_mode_is_description,
                name,
            )
            SegmentedButton(
                selected = negated == mode,
                onClick = { onNegatedChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.semantics { contentDescription = description },
                label = { Text(stringResource(if (mode) R.string.reports_filter_mode_not else R.string.reports_filter_mode_is)) },
            )
        }
    }
}

@Composable
private fun SectionHeading(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = TokklTheme.sectionHeadingTextStyle,
        modifier = modifier
            .padding(top = TokklTheme.spacing.betweenGroups, bottom = TokklTheme.spacing.space1)
            .semantics { heading() },
    )
}

/** The web app's Billable choices. */
private val BILLABLE_LABELS = mapOf(
    BillableFilter.ALL to R.string.reports_filter_billable_all,
    BillableFilter.BILLABLE to R.string.reports_filter_billable_only,
    BillableFilter.NON_BILLABLE to R.string.reports_filter_non_billable_only,
)

/** Ids are positive on Tokkl and Toggl, so 0 can stand for null, the "without" choice, in a saved state. */
private val FILTERS_SAVER: Saver<ReportFilters, Any> = listSaver(
    save = {
        listOf(it.projects.saved(), it.clients.saved(), it.tags.saved(), it.billable.ordinal, booleanArrayOf(it.projectsNot, it.clientsNot, it.tagsNot))
    },
    restore = {
        val negated = it[4] as BooleanArray
        ReportFilters(
            projects = (it[0] as LongArray).restored(),
            clients = (it[1] as LongArray).restored(),
            tags = (it[2] as LongArray).restored(),
            billable = BillableFilter.entries[it[3] as Int],
            projectsNot = negated[0],
            clientsNot = negated[1],
            tagsNot = negated[2],
        )
    },
)

private fun List<Long?>.saved(): LongArray = LongArray(size) { this[it] ?: 0L }

private fun LongArray.restored(): List<Long?> = map { id -> id.takeIf { it != 0L } }
