package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// The rows of a choice: the control, a gap of the spacing scale's space3 and the label, in a row
// that fills the width, is at least Material's minimum interactive size tall and takes a tap
// anywhere across it. The control's own click handler stays null, so TalkBack reads the row as
// one item with the control's role. The label takes [LocalTextStyle] unless given a style.

/**
 * One choice of several, with a radio button. Put the rows of one choice in a parent with
 * `selectableGroup`. The selected button differs in shape, not only in colour.
 */
@Composable
fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    OptionRow(
        label = label,
        textStyle = textStyle,
        modifier = modifier,
        selection = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
    }
}

/** A choice that is on or off, with a checkbox. */
@Composable
fun CheckboxOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    OptionRow(
        label = label,
        textStyle = textStyle,
        modifier = modifier,
        selection = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** [selection] makes the row selectable or toggleable, with its role. */
@Composable
private fun OptionRow(
    label: String,
    textStyle: TextStyle,
    modifier: Modifier,
    selection: Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .then(selection),
    ) {
        control()
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Text(text = label, style = textStyle)
    }
}
