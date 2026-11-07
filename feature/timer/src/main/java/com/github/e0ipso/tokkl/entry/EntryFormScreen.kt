package com.github.e0ipso.tokkl.entry

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.entryfields.EntryFieldsEditor
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.DateWords
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.TokklIconButton
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.LocalDate
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The project, task and tag picker for an entry in [workspaceId], as [EntityPicker] takes it.
 */
typealias EntryFormPicker =
    @Composable (
        workspaceId: Long,
        original: Picked,
        picked: Picked,
        onPickedChange: (Picked) -> Unit,
        modifier: Modifier,
    ) -> Unit

/**
 * The entry form from [model], over the cached projects, tasks and tags of [cache] in its picker:
 * a running segment or an entry from the history to edit, or a past entry to add. [onClose] leaves
 * it after Save has queued the change, on Back, on Cancel, and when the form is not open, as after the
 * process was restarted.
 */
@Composable
fun EntryFormScreen(
    model: EntryFormViewModel,
    cache: AccountCache,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    canRestore: () -> Boolean = { false },
    onDeleted: (CachedTimeEntry, Boolean) -> Unit = { _, _ -> },
) {
    LaunchedEffect(model) { if (!model.isOpen) onClose() }
    val state by model.state.collectAsState()
    val close = { if ((state as? EntryFormState.Open)?.saving != true) onClose() }
    BackHandler(onBack = close)
    LaunchedEffect(state) { if (state == EntryFormState.Saved) onClose() }
    EntryFormContent(
        state = state,
        actions = EntryFormActions(
            onDescriptionChange = model::setDescription,
            onPick = model::pick,
            onBillableChange = model::setBillable,
            onNoTimesChange = model::setNoTimes,
            onDateChange = model::setDate,
            onStartChange = model::setStart,
            onEndChange = model::setEnd,
            onDurationChange = model::setDuration,
            onTimesDone = model::timesDone,
            onDurationDone = model::durationDone,
            onSave = model::save,
            onDelete = { model.delete(canRestore(), onDeleted) },
            onConfirmDelete = { model.confirmDelete(onDeleted) },
            onCancelDelete = model::cancelDelete,
            onClose = close,
        ),
        picker = { workspaceId, original, picked, onPickedChange, pickerModifier ->
            EntityPicker(cache, workspaceId, original, picked, onPickedChange, pickerModifier)
        },
        modifier = modifier,
    )
}

/** What the form's controls do. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class EntryFormActions(
    val onDescriptionChange: (String) -> Unit = {},
    val onPick: (Picked) -> Unit = {},
    val onBillableChange: (Boolean) -> Unit = {},
    val onNoTimesChange: (Boolean) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onStartChange: (String) -> Unit = {},
    val onEndChange: (String) -> Unit = {},
    val onDurationChange: (String) -> Unit = {},
    val onTimesDone: () -> Unit = {},
    val onDurationDone: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * [EntryFormScreen] without its model: the top bar with the back button and the title, then the
 * fields as the web app's EntryEditor.tsx lists them, then Cancel and Save. With No times on, the
 * date and the duration take the place of the times. It scrolls under the bar, and makes room for
 * the keyboard, so every field can be reached at the largest font scale.
 */
@Composable
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
fun EntryFormContent(
    state: EntryFormState,
    actions: EntryFormActions,
    picker: EntryFormPicker,
    modifier: Modifier = Modifier,
) {
    val editing = (state as? EntryFormState.Open)?.editing ?: true
    val openState = state as? EntryFormState.Open
    if (openState?.confirmDelete == true) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_confirm_title),
            paragraphs = listOf(stringResource(R.string.history_delete_confirm_message)),
            confirmLabel = stringResource(R.string.history_delete_confirm),
            canConfirm = !openState.saving,
            problem = null,
            onConfirm = actions.onConfirmDelete,
            onCancel = actions.onCancelDelete,
            danger = true,
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val title = when {
                (state as? EntryFormState.Open)?.running == true -> R.string.entry_form_edit_running
                editing -> R.string.entry_form_edit
                else -> R.string.entry_form_new
            }
            TokklTopBar(stringResource(title), onBack = actions.onClose)
        },
        contentWindowInsets = ScrollingScreenInsets,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                // The keyboard covers the bottom bar, whose height the frame has consumed, so this
                // adds the rest of the keyboard's height while a field has focus.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
        ) {
            when (state) {
                EntryFormState.Closed, EntryFormState.Loading, EntryFormState.Saved -> Unit

                EntryFormState.Missing -> Notice(stringResource(R.string.entry_form_missing))

                is EntryFormState.NoAccountTime -> Notice(
                    stringResource(R.string.entry_form_unknown_time_zone, state.timeZone),
                )

                is EntryFormState.Open -> Fields(state, actions, picker)
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Fields(state: EntryFormState.Open, actions: EntryFormActions, picker: EntryFormPicker) {
    val spacing = TokklTheme.spacing
    val words = DateWords(LocalResources.current)
    val canChange = state.workspaceId != null && !state.saving
    Column(verticalArrangement = Arrangement.spacedBy(spacing.betweenGroups)) {
        EntryFieldsEditor(
            fields = state.fields,
            labels = state.labels,
            original = state.original,
            descriptionHint = stringResource(R.string.entry_form_description_hint),
            onDescriptionChange = actions.onDescriptionChange,
            onDescriptionDone = {},
            onPick = actions.onPick,
            onBillableChange = actions.onBillableChange,
            picker = { original, picked, onPickedChange, pickerModifier ->
                state.workspaceId?.let { picker(it, original, picked, onPickedChange, pickerModifier) }
            },
            enabled = canChange,
        )
        if (state.noTimesOffered) {
            NoTimesSwitch(state.noTimes, enabled = !state.saving, onChange = actions.onNoTimesChange)
        }
        DateField(state.date, words, enabled = !state.saving, onDateChange = actions.onDateChange)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            verticalArrangement = Arrangement.spacedBy(spacing.inGroup),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val fieldModifier = Modifier
                .widthIn(min = TimeFieldMinWidth)
                .weight(1f)
            if (state.noTimes) {
                TimeTextField(
                    value = state.duration,
                    onValueChange = actions.onDurationChange,
                    onDone = actions.onDurationDone,
                    label = R.string.entry_duration,
                    hint = R.string.entry_no_time_duration_hint,
                    mistake = state.mistakes[TimeField.DURATION],
                    enabled = !state.saving,
                    imeAction = ImeAction.Done,
                    modifier = fieldModifier,
                )
            } else {
                TimeTextField(
                    value = state.start,
                    onValueChange = actions.onStartChange,
                    onDone = actions.onTimesDone,
                    label = R.string.entry_start,
                    hint = R.string.entry_start_hint,
                    mistake = state.mistakes[TimeField.START],
                    enabled = !state.saving,
                    imeAction = if (state.running) ImeAction.Done else ImeAction.Next,
                    modifier = fieldModifier,
                )
                if (!state.running) {
                    TimeTextField(
                        value = state.end,
                        onValueChange = actions.onEndChange,
                        onDone = actions.onTimesDone,
                        label = R.string.entry_end,
                        hint = R.string.entry_end_hint,
                        mistake = state.mistakes[TimeField.END],
                        enabled = !state.saving,
                        imeAction = ImeAction.Next,
                        modifier = fieldModifier,
                    )
                    TimeTextField(
                        value = state.duration,
                        onValueChange = actions.onDurationChange,
                        onDone = actions.onDurationDone,
                        label = R.string.entry_duration,
                        hint = R.string.entry_duration_hint,
                        mistake = state.mistakes[TimeField.DURATION],
                        enabled = !state.saving,
                        imeAction = ImeAction.Done,
                        modifier = fieldModifier,
                    )
                }
            }
        }
        state.endsOn?.let { day ->
            Text(
                text = stringResource(R.string.entry_ends_on, dateText(day, words, LocalResources.current)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.deleteFailed) {
            Text(
                stringResource(R.string.entry_delete_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.editing && !state.running) {
                TokklIconButton(
                    icon = UiR.drawable.ic_nav_trash,
                    description = stringResource(R.string.history_delete),
                    onClick = actions.onDelete,
                    enabled = !state.saving,
                    danger = true,
                )
            }
            TokklGhostButton(
                onClick = actions.onClose,
                enabled = !state.saving,
            ) { Text(stringResource(R.string.entry_cancel)) }
            TokklButton(onClick = actions.onSave, enabled = canChange) { Text(stringResource(R.string.entry_save)) }
        }
    }
}
