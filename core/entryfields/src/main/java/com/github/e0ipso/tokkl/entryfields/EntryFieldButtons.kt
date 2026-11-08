package com.github.e0ipso.tokkl.entryfields

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.core.entryfields.R
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.FillScreenWindow
import com.github.e0ipso.tokkl.ui.projectMark
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The project and task, or "Project" when the entry has none. TalkBack reads "Project: none" then. */
@Composable
internal fun ProjectButton(fields: EntryFields, labels: EntryLabels, enabled: Boolean, onClick: () -> Unit) {
    val name = when {
        fields.projectId == null -> null
        labels.project == null -> stringResource(R.string.entry_unknown_project)
        labels.task == null -> labels.project
        else -> stringResource(R.string.entry_project_task, labels.project, labels.task)
    }
    val spoken = name?.let {
        stringResource(
            R.string.entry_project_spoken,
            it,
        )
    } ?: stringResource(R.string.entry_no_project_spoken)
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
            FieldIcon(UiR.drawable.ic_nav_projects)
        } else {
            Canvas(Modifier.size(TokklTheme.spacing.space3)) { drawCircle(dot) }
        }
        FieldText(name ?: stringResource(R.string.entry_project))
    }
}

/** The tags, or "Tags" when the entry has none. TalkBack reads "Tags: none" then. */
@Composable
internal fun TagsButton(tags: List<String>, enabled: Boolean, onClick: () -> Unit) {
    val names = tags.joinToString(", ").takeIf { tags.isNotEmpty() }
    val spoken = names?.let {
        stringResource(
            R.string.entry_tags_spoken,
            it,
        )
    } ?: stringResource(R.string.entry_no_tags_spoken)
    FieldButton(
        active = names != null,
        enabled = enabled,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken },
    ) {
        FieldIcon(UiR.drawable.ic_nav_tags)
        FieldText(names ?: stringResource(R.string.entry_tags))
    }
}

/** Billable, a checkbox to TalkBack. A tick replaces the dollar sign when on, so colour is not the only sign. */
@Composable
internal fun BillableToggle(billable: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FieldButton(
        active = billable,
        enabled = enabled,
        modifier = Modifier.toggleable(
            value = billable,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onChange,
        ),
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
    modifier: Modifier = Modifier,
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
internal fun PickerDialog(original: Picked, initial: Picked, picker: EntryPicker, onClose: (Picked) -> Unit) {
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
