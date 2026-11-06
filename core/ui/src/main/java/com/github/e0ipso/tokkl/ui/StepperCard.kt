package com.github.e0ipso.tokkl.ui

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.github.e0ipso.tokkl.core.ui.R
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The unit of a stepper card's number: [compact] on the line under it and [spoken] for
 * TalkBack, both plurals of the number.
 */
enum class StepperUnit(@PluralsRes val compact: Int, @PluralsRes val spoken: Int) {
    MINUTES(R.plurals.stepper_unit_minutes, R.plurals.stepper_spoken_minutes),
    WORK_PHASES(R.plurals.stepper_unit_work_phases, R.plurals.stepper_spoken_work_phases),
}

// The number's text field and the buttons. They sit under the card's one semantics node, so only
// the unmerged tree has them. The app's instrumented tests find them by these tags.
@VisibleForTesting
const val STEPPER_VALUE_TAG = "stepper_value"

@VisibleForTesting
const val STEPPER_DECREASE_TAG = "stepper_decrease"

@VisibleForTesting
const val STEPPER_INCREASE_TAG = "stepper_increase"

/** A disabled button's frame, at the alpha Material gives disabled content. */
private const val DisabledFrameAlpha = 0.38f

/**
 * A whole number to type, or to step by one within [range], such as a Pomodoro length: [label]
 * on top, the number large and centred, a line with the unit and range, and minus and plus
 * buttons, in Tokkl's 2dp frame with the large corners. The card has no fixed height, so its
 * text wraps at large font scales.
 *
 * [value] is the caller's raw draft, and [onChange] gets each change as typed. Nothing clamps a
 * draft while it is typed, and the caller's own rule decides whether it accepts one. The card
 * only tells drafts apart to show them: for an empty draft, text that is not a whole number or a
 * number out of range, the frame, number and line turn danger, and the line names the limit,
 * such as `max 180`, or the range for a draft with no number. A step from a number in range adds
 * or takes one and stops at the range's ends. A step from any other draft lands in the range: on
 * its end for a number past it, on its start otherwise.
 *
 * TalkBack reads the card as one node, such as "Work, 25 minutes, 1 to 180", with Increase and
 * Decrease as actions in place of the buttons. The node's double tap focuses the number's
 * numeric text field and opens the keyboard, and its set-text action sets the draft, so TalkBack
 * users can type. The node carries no editable text of its own: that would make it an edit box,
 * and Android's accessibility checks refuse an edit box a content description. With [enabled]
 * false the card takes no typing, steps or actions, and TalkBack reads it as disabled.
 */
@Composable
fun StepperCard(
    label: String,
    value: String,
    range: IntRange,
    unit: StepperUnit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val draft = StepperDraft.of(value, range)
    val invalid = draft !is StepperDraft.InRange
    val colors = MaterialTheme.colorScheme
    val spoken = spokenCard(label, value, draft, range, unit)
    val typeLabel = stringResource(R.string.stepper_type_action)
    val increaseLabel = stringResource(R.string.stepper_increase)
    val decreaseLabel = stringResource(R.string.stepper_decrease)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    val step = { delta: Int -> onChange(draft.stepped(range, delta).toString()) }
    val type = {
        focusRequester.requestFocus()
        keyboard?.show()
        true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
        modifier = modifier
            // One node for the whole card. Clearing drops the text field's own semantics, so the
            // card sets again what typing needs: focus, a double tap and set-text.
            .clearAndSetSemantics {
                contentDescription = spoken
                if (enabled) {
                    focused = editing
                    onClick(label = typeLabel, action = type)
                    requestFocus(action = type)
                    setText { text ->
                        onChange(text.text)
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(increaseLabel) { step(1); true },
                        CustomAccessibilityAction(decreaseLabel) { step(-1); true },
                    )
                } else {
                    disabled()
                }
            }
            .border(FrameWidth, if (invalid) colors.error else colors.outline, MaterialTheme.shapes.medium)
            .padding(TokklTheme.spacing.space3),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        StepperValue(
            value = value,
            enabled = enabled,
            color = if (invalid) colors.error else colors.onSurface,
            focusRequester = focusRequester,
            onFocusChange = { editing = it },
            onChange = onChange,
        )
        Text(
            text = unitLine(draft, range, unit),
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) colors.error else colors.onSurfaceVariant,
            fontWeight = if (invalid) FontWeight.Bold else null,
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            modifier = Modifier.padding(top = TokklTheme.spacing.space1),
        ) {
            StepButton(R.drawable.ic_minus, STEPPER_DECREASE_TAG, enabled) { step(-1) }
            StepButton(R.drawable.ic_plus, STEPPER_INCREASE_TAG, enabled) { step(1) }
        }
    }
}

/**
 * The number as a numeric text field. Its own copy of the text keeps the cursor where the user
 * puts it; a draft from outside, such as a step, puts the cursor at the end.
 */
@Composable
private fun StepperValue(
    value: String,
    enabled: Boolean,
    color: Color,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onChange: (String) -> Unit,
) {
    var edited by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (edited.text == value) edited else TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = shown,
        onValueChange = {
            edited = it
            if (it.text != value) onChange(it.text)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TokklTheme.stepperValueTextStyle.copy(color = color, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(color),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .testTag(STEPPER_VALUE_TAG),
    )
}

/** A minus or plus button, a 48dp square in Tokkl's frame. The card's actions stand in for it in TalkBack. */
@Composable
private fun StepButton(@DrawableRes icon: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = colors.onSurface),
        border = BorderStroke(FrameWidth, if (enabled) colors.outline else colors.onSurface.copy(alpha = DisabledFrameAlpha)),
        modifier = Modifier.size(TokklTheme.spacing.space8).testTag(tag),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(TokklTheme.spacing.space6))
    }
}

/** The line under the number: its unit and the range, or the limit a draft misses. */
@Composable
private fun unitLine(draft: StepperDraft, range: IntRange, unit: StepperUnit): String = when (draft) {
    is StepperDraft.InRange ->
        stringResource(R.string.stepper_unit_range, pluralStringResource(unit.compact, draft.number), range.first, range.last)
    is StepperDraft.AboveMax -> stringResource(R.string.stepper_limit_max, range.last)
    is StepperDraft.BelowMin -> stringResource(R.string.stepper_limit_min, range.first)
    StepperDraft.Empty, StepperDraft.NotANumber -> stringResource(R.string.stepper_limit_range, range.first, range.last)
}

/** What TalkBack reads for the card: its label, the number with its unit, and the range or the limit missed. */
@Composable
private fun spokenCard(label: String, value: String, draft: StepperDraft, range: IntRange, unit: StepperUnit): String = when (draft) {
    is StepperDraft.InRange ->
        stringResource(R.string.stepper_spoken_in_range, label, spokenNumber(unit, draft.number.toLong(), value), range.first, range.last)
    is StepperDraft.AboveMax ->
        stringResource(R.string.stepper_spoken_above_max, label, spokenNumber(unit, draft.number, value), range.last)
    is StepperDraft.BelowMin ->
        stringResource(R.string.stepper_spoken_below_min, label, spokenNumber(unit, draft.number, value), range.first)
    StepperDraft.Empty -> stringResource(R.string.stepper_spoken_empty, label, range.first, range.last)
    StepperDraft.NotANumber -> stringResource(R.string.stepper_spoken_not_a_number, label, range.first, range.last)
}

/** [number] with its unit, or the typed [value] for a whole number too long to parse. */
@Composable
private fun spokenNumber(unit: StepperUnit, number: Long?, value: String): String {
    val quantity = number?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: Int.MAX_VALUE
    return pluralStringResource(unit.spoken, quantity, number?.toString() ?: value)
}

/** How a stepper card shows a draft. Whether the caller accepts it stays the caller's rule. */
internal sealed interface StepperDraft {
    data class InRange(val number: Int) : StepperDraft

    /** Under the range. [number] is null for a whole number too long for a Long. */
    data class BelowMin(val number: Long?) : StepperDraft

    /** Over the range. [number] is null for a whole number too long for a Long. */
    data class AboveMax(val number: Long?) : StepperDraft

    data object Empty : StepperDraft

    /** Text that is no whole number, such as "2x" or "-". */
    data object NotANumber : StepperDraft

    companion object {
        fun of(value: String, range: IntRange): StepperDraft {
            if (value.isEmpty()) return Empty
            val number = value.toLongOrNull() ?: return beyondLong(value) ?: NotANumber
            return when {
                number < range.first -> BelowMin(number)
                number > range.last -> AboveMax(number)
                else -> InRange(number.toInt())
            }
        }

        /** A signed whole number too long for a Long: below any range with a minus, above it otherwise. */
        private fun beyondLong(value: String): StepperDraft? {
            val negative = value.startsWith('-')
            val digits = if (negative || value.startsWith('+')) value.substring(1) else value
            if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
            return if (negative) BelowMin(null) else AboveMax(null)
        }
    }
}

/**
 * The number a step of [delta] lands on, always in [range]: one more or less from a number in
 * range, stopping at the ends, the range's end from a number past it, and its start from any
 * other draft. The sum is a Long, so a range ending at Int.MAX_VALUE cannot overflow.
 */
internal fun StepperDraft.stepped(range: IntRange, delta: Int): Int = when (this) {
    is StepperDraft.InRange -> (number.toLong() + delta).coerceIn(range.first.toLong(), range.last.toLong()).toInt()
    is StepperDraft.AboveMax -> range.last
    is StepperDraft.BelowMin, StepperDraft.Empty, StepperDraft.NotANumber -> range.first
}
