package com.github.e0ipso.tokkl.entry

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.ui.FillScreenWindow
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The project, task and tag picker for an entry in [workspaceId], as [EntityPicker] takes it.
 */
typealias EntryFormPicker =
    @Composable (workspaceId: Long, original: Picked, picked: Picked, onPickedChange: (Picked) -> Unit, modifier: Modifier) -> Unit

/**
 * The entry form from [model], over the cached projects, tasks and tags of [cache] in its picker:
 * a running segment or an entry from the history to edit, or a past entry to add. [onClose] leaves
 * it after Save has queued the change, on Back, on Cancel, and when the form is not open, as after the
 * process was restarted.
 */
@Composable
fun EntryFormScreen(
    model: EntryFormModel,
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
internal class EntryFormActions(
    val onDescriptionChange: (String) -> Unit = {},
    val onPick: (Picked) -> Unit = {},
    val onBillableChange: (Boolean) -> Unit = {},
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
 * fields as the web app's EntryEditor.tsx lists them, then Cancel and Save. It scrolls under the
 * bar, and makes room for the keyboard, so every field can be reached at the largest font scale.
 */
@Composable
internal fun EntryFormContent(state: EntryFormState, actions: EntryFormActions, picker: EntryFormPicker, modifier: Modifier = Modifier) {
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
                is EntryFormState.NoAccountTime -> Notice(stringResource(R.string.entry_form_unknown_time_zone, state.timeZone))
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
        DateField(state.date, words, enabled = !state.saving, onDateChange = actions.onDateChange)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            verticalArrangement = Arrangement.spacedBy(spacing.inGroup),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val fieldModifier = Modifier
                .widthIn(min = TimeFieldMinWidth)
                .weight(1f)
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
                TokklGhostButton(onClick = actions.onDelete, enabled = !state.saving) {
                    Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            TokklGhostButton(onClick = actions.onClose, enabled = !state.saving) { Text(stringResource(R.string.entry_cancel)) }
            TokklButton(onClick = actions.onSave, enabled = canChange) { Text(stringResource(R.string.entry_save)) }
        }
    }
}

/**
 * A time or duration field, with its label above the value and an example while it is empty.
 * Losing focus calls [onDone], so the other times can follow it. A [mistake] shows under the
 * field as text, which TalkBack reads with the field and announces when it appears.
 */
@Composable
private fun TimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    @StringRes label: Int,
    @StringRes hint: Int,
    mistake: TimeMistake?,
    enabled: Boolean,
    imeAction: ImeAction,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(hint)) },
        supportingText = mistake?.let {
            {
                Text(stringResource(it.text), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        },
        isError = mistake != null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = imeAction),
        modifier = modifier.onFocusChanged { focus ->
            if (focused && !focus.isFocused) onDone()
            focused = focus.isFocused
        },
    )
}

private val TimeMistake.text: Int
    @StringRes get() = when (this) {
        TimeMistake.TIMES -> R.string.entry_mistake_times
        TimeMistake.MANUAL_TIMES -> R.string.entry_mistake_manual_times
        TimeMistake.DURATION -> R.string.entry_mistake_duration
        TimeMistake.END_BEFORE_START -> R.string.entry_mistake_end_before_start
        TimeMistake.TOO_LONG -> R.string.entry_mistake_too_long
        TimeMistake.TOO_SHORT -> R.string.entry_mistake_too_short
    }

/**
 * The date, a button that opens Material's date picker, under its label. TalkBack reads it as
 * one button: "Date: Mon, 19 Oct 2026".
 */
@Composable
private fun DateField(date: LocalDate, words: DateWords, enabled: Boolean, onDateChange: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val text = dateText(date, words, LocalResources.current)
    val spoken = stringResource(R.string.entry_date_spoken, text)
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
        Text(
            text = stringResource(R.string.entry_date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The button says it, so TalkBack reads the date once.
            modifier = Modifier.clearAndSetSemantics {},
        )
        TokklGhostButton(
            onClick = { picking = true },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = spoken },
        ) {
            Text(text)
        }
    }
    if (picking && enabled) {
        DateDialog(
            initial = date,
            onPick = {
                picking = false
                if (it != date) onDateChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Mon, 19 Oct 2026. */
private fun dateText(date: LocalDate, words: DateWords, resources: Resources): String =
    resources.getString(R.string.entry_date_value, words.weekday(date), words.shortWithYear(date))

/**
 * Material's date picker over the whole screen, as the reports' custom range is, since a dialog
 * leaves its month grid no room at large font scales. Dates go in and out as UTC midnights, the
 * picker's own convention, so no zone of the phone touches them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.utcMillis())
    val picked = state.selectedDateMillis?.let(::utcDate)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FillScreenWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = TokklTheme.spacing.space1),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.entry_cancel))
                    }
                    Text(
                        text = stringResource(R.string.entry_date),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) {
                        Text(stringResource(R.string.entry_picker_done))
                    }
                }
                DatePicker(state = state, title = null, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

/** The narrowest a time field gets before the next one wraps below it. */
private val TimeFieldMinWidth = 120.dp
