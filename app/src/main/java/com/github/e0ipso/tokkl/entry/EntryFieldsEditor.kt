package com.github.e0ipso.tokkl.entry

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.reports.projectMark
import com.github.e0ipso.tokkl.ui.FillScreenWindow
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The project, task and tag picker for one entry, such as picker/EntityPicker for the entry's
 * workspace: [original] is what the entry had when editing started, [picked] what it has now.
 */
typealias EntryPicker = @Composable (original: Picked, picked: Picked, onPickedChange: (Picked) -> Unit, modifier: Modifier) -> Unit

/**
 * The fields of one time entry that the user picks (docs/PRD.md T1, H2), as the web app's
 * TimerBar.tsx lays them out: the description, a button for the project and task and one for the
 * tags, which both open [picker] (P1), and billable. The timer edits its draft and the running
 * entry with it, and the manual entry and edit screens can share it.
 *
 * They flow in rows, as the web app's bar wraps: the description takes the rest of its row and at
 * least [DescriptionMinWidth], and the buttons, then [trailing], such as the timer's clock and
 * Start, follow it on the same row when they fit and wrap below when they do not.
 *
 * While the description field has focus it keeps what is typed, whatever [fields] says, so a
 * refresh cannot take letters back. It hands every change to [onDescriptionChange], and calls
 * [onDescriptionDone] when it loses focus or the keyboard's Done is pressed. The picker opens in a
 * full-screen dialog, and closing it hands what was picked to [onPick], once, when it changed.
 *
 * @param original what the entry had when editing started, for the picker's archived items.
 * @param descriptionHint the description field's placeholder.
 * @param enabled false while the entry cannot change yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryFieldsEditor(
    fields: EntryFields,
    labels: EntryLabels,
    original: Picked,
    descriptionHint: String,
    onDescriptionChange: (String) -> Unit,
    onDescriptionDone: () -> Unit,
    onPick: (Picked) -> Unit,
    onBillableChange: (Boolean) -> Unit,
    picker: EntryPicker,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable FlowRowScope.() -> Unit = {},
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val spacing = TokklTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        verticalArrangement = Arrangement.spacedBy(spacing.inGroup),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        DescriptionField(
            description = fields.description,
            hint = descriptionHint,
            enabled = enabled,
            onChange = onDescriptionChange,
            onDone = onDescriptionDone,
            modifier = Modifier
                .widthIn(min = DescriptionMinWidth)
                .weight(1f),
        )
        ProjectButton(fields, labels, enabled, onClick = { picking = true })
        TagsButton(fields.tags, enabled, onClick = { picking = true })
        BillableToggle(fields.billable, enabled, onBillableChange)
        trailing()
    }
    if (picking && enabled) {
        PickerDialog(original = original, initial = fields.picked, picker = picker) { picked ->
            picking = false
            if (picked != fields.picked) onPick(picked)
        }
    }
}

/**
 * The description, on one line. It shows [description] until it has focus, then what is typed,
 * which starts from [description], and on losing focus hands on anything typed that [description]
 * does not have yet.
 */
@Composable
private fun DescriptionField(
    description: String,
    hint: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(TextFieldValue(description)) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = if (focused) typed else TextFieldValue(description, TextRange(description.length)),
        onValueChange = { next ->
            val changed = next.text != typed.text
            typed = next
            if (changed) onChange(next.text)
        },
        label = { Text(stringResource(R.string.entry_description)) },
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.onFocusChanged { focus ->
            when {
                focus.isFocused && !focused -> typed = TextFieldValue(description, TextRange(description.length))
                !focus.isFocused && focused -> {
                    if (typed.text != description) onChange(typed.text)
                    onDone()
                }
            }
            focused = focus.isFocused
        },
    )
}

/** The project and task, or "Project" when the entry has none. TalkBack reads "Project: none" then. */
@Composable
private fun ProjectButton(fields: EntryFields, labels: EntryLabels, enabled: Boolean, onClick: () -> Unit) {
    val name = when {
        fields.projectId == null -> null
        labels.project == null -> stringResource(R.string.entry_unknown_project)
        labels.task == null -> labels.project
        else -> stringResource(R.string.entry_project_task, labels.project, labels.task)
    }
    val spoken = name?.let { stringResource(R.string.entry_project_spoken, it) } ?: stringResource(R.string.entry_no_project_spoken)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = projectMark(labels.projectColor, TokklTheme.extraColors.projectMix, muted)
    FieldButton(
        active = name != null,
        enabled = enabled,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken },
    ) {
        if (name == null) {
            FieldIcon(R.drawable.ic_nav_projects)
        } else {
            Canvas(Modifier.size(TokklTheme.spacing.space3)) { drawCircle(dot) }
        }
        FieldText(name ?: stringResource(R.string.entry_project))
    }
}

/** The tags, or "Tags" when the entry has none. TalkBack reads "Tags: none" then. */
@Composable
private fun TagsButton(tags: List<String>, enabled: Boolean, onClick: () -> Unit) {
    val names = tags.joinToString(", ").takeIf { tags.isNotEmpty() }
    val spoken = names?.let { stringResource(R.string.entry_tags_spoken, it) } ?: stringResource(R.string.entry_no_tags_spoken)
    FieldButton(
        active = names != null,
        enabled = enabled,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken },
    ) {
        FieldIcon(R.drawable.ic_nav_tags)
        FieldText(names ?: stringResource(R.string.entry_tags))
    }
}

/** Billable, a checkbox to TalkBack. A tick replaces the dollar sign when on, so colour is not the only sign. */
@Composable
private fun BillableToggle(billable: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FieldButton(
        active = billable,
        enabled = enabled,
        modifier = Modifier.toggleable(value = billable, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
    ) {
        FieldIcon(if (billable) R.drawable.ic_check else R.drawable.ic_dollar)
        FieldText(stringResource(R.string.entry_billable))
    }
}

/**
 * A pill with Tokkl's 2dp frame, as the web app's `.timer-attr` chips: in primary when [active],
 * the entry has the field set, and in the outline colour otherwise. The whole pill is the touch
 * target, at least 48dp high. [modifier] makes it clickable or toggleable.
 */
@Composable
private fun FieldButton(
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val frame = when {
        !enabled -> colors.outlineVariant
        active -> colors.primary
        else -> colors.outline
    }
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        border = BorderStroke(FrameWidth, frame),
        modifier = Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            modifier = modifier
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .padding(horizontal = TokklTheme.spacing.space3),
        ) {
            content()
        }
    }
}

/** The narrowest the description gets before the buttons after it wrap to the next row, as in the web app. */
val DescriptionMinWidth = 280.dp

@Composable
private fun FieldIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(TokklTheme.spacing.space5))
}

@Composable
private fun FieldText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The picker over the whole screen, under a title and Done. Back and Done both close it with what
 * was picked, which the picker shows as it changes.
 */
@Composable
private fun PickerDialog(original: Picked, initial: Picked, picker: EntryPicker, onClose: (Picked) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = { onClose(picked) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FillScreenWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .imePadding()
                    .padding(horizontal = TokklTheme.spacing.compactMargin, vertical = TokklTheme.spacing.space2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.entry_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    TextButton(onClick = { onClose(picked) }) {
                        Text(stringResource(R.string.entry_picker_done))
                    }
                }
                picker(original, picked, { picked = it }, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
