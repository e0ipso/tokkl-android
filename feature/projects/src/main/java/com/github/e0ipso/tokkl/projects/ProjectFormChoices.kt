package com.github.e0ipso.tokkl.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.feature.projects.R
import com.github.e0ipso.tokkl.picker.projectColor
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.core.shared.R as SharedR

/**
 * The colour, as the web app's ColorField: Toggl's 14 swatches and Custom, one choice. Each swatch
 * shows its colour with its name, and the chosen one carries a check mark and a heavier frame, so
 * the choice never rests on colour. Custom shows a field for any `#rrggbb` colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColourField(
    color: String,
    custom: Boolean,
    mistake: Int?,
    focus: FocusRequester,
    onPalette: (ProjectPalette) -> Unit,
    onCustom: () -> Unit,
    onCustomChange: (String) -> Unit,
) {
    val chosen = if (custom) null else ProjectPalette.of(color)
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        Text(
            text = stringResource(R.string.project_colour),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            modifier = Modifier.selectableGroup(),
        ) {
            for (swatch in ProjectPalette.entries) {
                ColourOption(
                    label = stringResource(swatch.label),
                    hex = swatch.hex,
                    selected = swatch == chosen,
                    onSelect = { onPalette(swatch) },
                )
            }
            ColourOption(
                label = stringResource(R.string.project_colour_custom),
                hex = if (custom) color else null,
                selected = custom,
                onSelect = onCustom,
            )
        }
        if (custom) {
            FormTextField(
                value = color,
                onValueChange = onCustomChange,
                label = R.string.project_colour_custom_field,
                mistake = mistake,
                focus = focus,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
        }
    }
}

/** One colour choice: a dot in [hex], the [label] and, when [selected], a check mark in a heavier frame. */
@Composable
private fun ColourOption(label: String, hex: String?, selected: Boolean, onSelect: () -> Unit) {
    val frame = if (selected) {
        BorderStroke(FrameWidth, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(MaterialTheme.shapes.small)
            .border(frame, MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = TokklTheme.spacing.space3),
    ) {
        Box(
            Modifier
                .size(TokklTheme.spacing.space4)
                .background(projectColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(DividerDefaults.Thickness, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (selected) Icon(painter = painterResource(SharedR.drawable.ic_check), contentDescription = null)
    }
}

/** The client, from the clients on the phone, or No client. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientField(clients: List<CachedClient>, clientId: Long?, onChoose: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val noClient = stringResource(R.string.projects_no_client)
    val chosen = clients.firstOrNull { it.id == clientId }
    val shown = chosen?.let { clientLabel(it) } ?: noClient
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.project_client)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noClient) },
                onClick = {
                    onChoose(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            for (client in clients) {
                DropdownMenuItem(
                    text = { Text(clientLabel(client)) },
                    onClick = {
                        onChoose(client.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun clientLabel(client: CachedClient): String =
    if (client.archived) stringResource(R.string.project_client_archived, client.name) else client.name
