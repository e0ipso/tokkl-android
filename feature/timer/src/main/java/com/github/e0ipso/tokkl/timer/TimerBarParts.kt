package com.github.e0ipso.tokkl.timer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklDangerButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.UnsentIcon
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * "Running" in the running colour beside a dot, and while the server lacks a change to the entry,
 * the unsent icon and NOT SENT, as the history marks it. TalkBack reads the words.
 */
@Composable
internal fun RunningLabel(unsent: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val colour = TokklTheme.extraColors.running
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
        Canvas(Modifier.size(TokklTheme.spacing.space2)) { drawCircle(colour) }
        Text(stringResource(SharedR.string.timer_running), style = MaterialTheme.typography.labelLarge, color = colour)
        if (unsent) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
                UnsentIcon(TokklTheme.smallCapsLabelTextStyle)
                Text(
                    text = stringResource(R.string.history_unsent).uppercase(locale),
                    style = TokklTheme.smallCapsLabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The running time, which TalkBack reads in words: "1 hour, 5 minutes elapsed". */
@Composable
internal fun RunningClock(elapsedSeconds: Long, onEdit: () -> Unit) {
    val spoken = stringResource(R.string.timer_elapsed_spoken, spokenDuration(LocalResources.current, elapsedSeconds))
    val editLabel = stringResource(R.string.timer_edit_running)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEdit)
            .semantics { contentDescription = "$spoken. $editLabel" },
    ) {
        Text(text = formatDuration(elapsedSeconds), style = TokklTheme.clockTextStyle)
    }
}

/** The clock at zero while no timer runs, which TalkBack skips, since Start says what it does. */
@Composable
internal fun IdleClock() {
    Text(
        text = formatDuration(0),
        style = TokklTheme.clockTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** "Paused" in the muted colour, as the web app says it beside the frozen clock. */
@Composable
internal fun PausedLabel() {
    Text(stringResource(SharedR.string.timer_paused), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The paused total, which stands still, and which TalkBack reads as "1 hour elapsed, paused". */
@Composable
internal fun PausedClock(seconds: Long) {
    val spoken = stringResource(R.string.timer_paused_elapsed_spoken, spokenDuration(LocalResources.current, seconds))
    Text(
        text = formatDuration(seconds),
        style = TokklTheme.clockTextStyle,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

/** How a [TimerButton] looks: Start and Resume on primary, Pause outlined, Stop on danger. */
internal enum class TimerButtonLook { Primary, Neutral, Danger }

/** A timer button in its [look]: an icon and a word, and the whole action to TalkBack. */
@Composable
internal fun TimerButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes spoken: Int,
    look: TimerButtonLook,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(spoken)
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(TokklTheme.spacing.space5))
        Text(stringResource(label), modifier = Modifier.padding(start = TokklTheme.spacing.space2))
    }
    val semantics = Modifier.semantics { contentDescription = description }
    when (look) {
        TimerButtonLook.Primary -> TokklButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
        TimerButtonLook.Neutral -> TokklGhostButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
        TimerButtonLook.Danger -> TokklDangerButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
    }
}

/** Effective Room rows include queued changes and remaps; the pause-chain clock is not added. */
