package com.github.e0ipso.tokkl.calendar

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reports.projectMark
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.UnsentIcon
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SmallCorner
import com.github.e0ipso.tokkl.ui.theme.SmallCornerShape
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.ZoneId

/**
 * An entry's block: its description, project and duration as far as they fit, marked when the
 * server does not have the entry yet, and all of it, with its times, to TalkBack. A block being
 * [dragged] sits in Tokkl's frame. With [onEdit], TalkBack's click on it opens the entry form,
 * where its times change as a drag would change them.
 */
@Composable
internal fun Block(
    block: CalendarBlock,
    columnWidth: Dp,
    settings: AccountSettings,
    zone: ZoneId,
    now: Instant,
    dragged: Boolean,
    onEdit: (() -> Unit)?,
) {
    val resources = LocalResources.current
    val entry = block.entry
    val scheme = MaterialTheme.colorScheme
    val runningColour = TokklTheme.extraColors.running
    val (tint, bar) = entryColours(entry)
    val laneWidth = columnWidth / block.lanes
    val height = max(HourHeight * ((block.bottom - block.top) / 60).toFloat(), TokklSpacing.space1)
    val noDescription = stringResource(R.string.history_no_description)
    val heading = entry.description.ifBlank { entry.project ?: noDescription }
    val duration = settings.formatDuration(entry.seconds(now))
    val spoken = spokenBlock(resources, entry, settings, zone, now)
    val editLabel = stringResource(R.string.entry_edit_action)
    val frame = if (dragged) Modifier.border(FrameWidth, scheme.outline, SmallCornerShape) else Modifier
    Box(
        Modifier
            .offset(x = laneWidth * block.lane + BlockGap, y = HourHeight * (block.top / 60).toFloat())
            .width((laneWidth - BlockGap * 2).coerceAtLeast(0.dp))
            .height(height)
            .clip(SmallCornerShape)
            .background(tint)
            .drawBehind {
                val barWidth = BlockBar.toPx()
                drawRect(bar, size = Size(barWidth, size.height))
                if (entry.running) {
                    // Dashes as well as the colour mark the running entry.
                    val stroke = FrameWidth.toPx()
                    val dash = TokklSpacing.space1.toPx()
                    drawRoundRect(
                        color = runningColour,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(SmallCorner.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                    )
                }
            }
            .then(frame)
            .padding(start = BlockBar + TokklTheme.spacing.space1, end = TokklTheme.spacing.space1)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (onEdit != null) {
                    onClick(label = editLabel) {
                        onEdit()
                        true
                    }
                }
            },
    ) {
        val twoLines = block.bottom - block.top >= TWO_LINE_MINUTES
        if (entry.unsent) {
            UnsentBlockText { mark -> BlockText(entry, heading, duration, twoLines, mark) }
        } else {
            BlockText(entry, heading, duration, twoLines, mark = false)
        }
    }
}

/** The tint and the start bar of an entry's block or chip. */
internal data class EntryColours(val tint: Color, val bar: Color)

/** [entry]'s project colour over surface, and its bar: the running colour while it runs, else the project's mark. */
@Composable
internal fun entryColours(entry: CalendarEntry): EntryColours {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return EntryColours(
        tint = mix(projectColour(entry.projectColor) ?: muted, MaterialTheme.colorScheme.surface, BLOCK_TINT),
        bar = if (entry.running) TokklTheme.extraColors.running else projectMark(entry.projectColor, TokklTheme.extraColors.projectMix, muted),
    )
}

/**
 * A block's text: its heading over its project and duration when [twoLines], or else its heading
 * and project on one line. With [mark], the unsent icon starts the first line.
 */
@Composable
internal fun BlockText(entry: CalendarEntry, heading: String, duration: String, twoLines: Boolean, mark: Boolean) {
    val resources = LocalResources.current
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val strong = MaterialTheme.typography.labelSmall
    val small = MaterialTheme.typography.bodySmall
    if (twoLines) {
        Column {
            MarkedLine(mark, strong) {
                Text(heading, style = strong, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = buildAnnotatedString {
                    if (entry.project != null) append(entry.project + resources.getString(R.string.calendar_block_separator))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onSurface)) { append(duration) }
                },
                style = small.copy(fontFeatureSettings = "tnum"),
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        MarkedLine(mark, small) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onSurface)) { append(heading) }
                    if (entry.description.isNotBlank() && entry.project != null) {
                        append(resources.getString(R.string.calendar_block_separator) + entry.project)
                    }
                },
                style = small,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** [line] after the unsent icon, which is as tall as a line of [style], when [mark]; else [line] alone. */
@Composable
internal fun MarkedLine(mark: Boolean, style: TextStyle, line: @Composable () -> Unit) {
    if (mark) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
            UnsentIcon(style)
            line()
        }
    } else {
        line()
    }
}

/**
 * An unsent entry's text, marked as the history marks it, by more than colour: the unsent icon
 * and the history's NOT SENT under the text where the block has room for the whole label. A block
 * too short or narrow for the words gets the icon alone, at the start of the first line, since
 * TalkBack still reads "Not sent" for it. [text] draws the text, with the icon when it gets true.
 * The block keeps "Not sent" in what TalkBack reads, so the label and icon stay out of it.
 */
@Composable
internal fun UnsentBlockText(text: @Composable (mark: Boolean) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Layout(
        content = {
            text(false)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
                UnsentIcon(TokklTheme.smallCapsLabelTextStyle)
                Text(
                    text = stringResource(R.string.history_unsent).uppercase(locale),
                    style = TokklTheme.smallCapsLabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            text(true)
        },
    ) { measurables, constraints ->
        val (plain, label, marked) = measurables
        // Each part as tall as its text; the block clips what goes past its end.
        val free = Constraints(maxWidth = constraints.maxWidth)
        val labelFits = label.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val lines = plain.measure(free)
        val words = label.measure(free)
        if (labelFits && lines.height + words.height <= constraints.maxHeight) {
            layout(constraints.maxWidth, lines.height + words.height) {
                lines.place(0, 0)
                words.place(0, lines.height)
            }
        } else {
            val withIcon = marked.measure(free)
            layout(constraints.maxWidth, constraints.constrainHeight(withIcon.height)) { withIcon.place(0, 0) }
        }
    }
}

/** What TalkBack reads for a block: description, project and task, times, duration, and whether it runs or is unsent. */
private fun spokenBlock(resources: Resources, entry: CalendarEntry, settings: AccountSettings, zone: ZoneId, now: Instant): String {
    val start = settings.formatTime(entry.start, zone)
    val times = entry.stop?.let { resources.getString(R.string.history_time_range_spoken, start, settings.formatTime(it, zone)) }
        ?: resources.getString(R.string.calendar_running_spoken, start)
    return listOfNotNull(
        entry.description.ifBlank { resources.getString(R.string.history_no_description) },
        entry.project,
        entry.task,
        times,
        spokenDuration(resources, entry.seconds(now)),
        if (entry.unsent) resources.getString(R.string.history_unsent) else null,
    ).joinToString(", ")
}
