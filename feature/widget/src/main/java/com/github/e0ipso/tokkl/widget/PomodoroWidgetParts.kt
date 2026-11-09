package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.graphics.Paint
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.e0ipso.tokkl.feature.widget.R
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
import java.time.Duration
import java.time.Instant
import com.github.e0ipso.tokkl.core.entryfields.R as FieldsR
import com.github.e0ipso.tokkl.core.ui.R as UiR

// The parts of the Pomodoro widget that its strips and cards share: the phase band, the cycle dots,
// the entry chips, the native countdown, the labels and their text.

// The light secondary fill is 3.06:1 on white. Use a darker teal for small text (4.9:1).
private val RestText = ColorProvider(Color(0xFF007A7A), TokklDarkColorScheme.secondary)

@Composable
internal fun PhaseBand(context: Context, state: PomodoroWidgetState.Active) {
    // The launcher ticks the native clock, but cannot tick a Glance progress bar. Use the
    // phase colour without a frozen elapsed percentage or a periodic redraw job.
    val description = phaseLabel(context, state)
    Box(
        GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp).background(GlanceTheme.colors.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        Box(
            GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp)
                .background(
                    if (state.ended) {
                        RunningColor
                    } else if (state.session.phase.kind ==
                        PhaseKind.WORK
                    ) {
                        PrimaryFill
                    } else {
                        GlanceTheme.colors.secondary
                    },
                ),
        ) {}
    }
}

@Composable
internal fun Cycle(context: Context, state: PomodoroWidgetState.Active) {
    val session = state.session
    val description = context.getString(
        R.string.pomodoro_widget_cycle,
        session.number,
        session.longRestEvery,
        session.phase.completed,
    )
    Row(GlanceModifier.semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        // One child per work/rest pair keeps the RemoteViews child limit even at every 12.
        val dot = if (session.longRestEvery > 6) 4f else 8f
        for (indices in (0 until session.longRestEvery.coerceIn(1, 12)).chunked(2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                indices.forEach { index ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier.size(dot.dp).cornerRadius(dot.dp).background(
                                if (index < session.phase.completed ||
                                    index + 1 == session.number
                                ) {
                                    PrimaryFill
                                } else {
                                    GlanceTheme.colors.surfaceVariant
                                },
                            ),
                        ) {}
                        Spacer(GlanceModifier.width(3.dp))
                        Box(
                            GlanceModifier.size((dot / 2).dp).cornerRadius(dot.dp).background(
                                if (index + 1 < session.number ||
                                    (index + 1 == session.number && session.phase.kind != PhaseKind.WORK)
                                ) {
                                    GlanceTheme.colors.secondary
                                } else {
                                    GlanceTheme.colors.surfaceVariant
                                },
                            ),
                        ) {}
                        Spacer(GlanceModifier.width(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ColumnScope.EntryChips(context: Context, entry: PomodoroWidgetEntry, width: Float, scale: Float) {
    Spacer(GlanceModifier.height(8.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val project = projectLine(context, entry)
        if (project != null) {
            Row(
                GlanceModifier.defaultWeight().cornerRadius(
                    13.dp,
                ).background(GlanceTheme.colors.surfaceVariant).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = runCatching {
                    ColorProvider(
                        Color(android.graphics.Color.parseColor(entry.projectColor)),
                    )
                }.getOrElse { GlanceTheme.colors.onSurfaceVariant }
                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(color)) {}
                Spacer(GlanceModifier.width(5.dp))
                PomodoroLabel(project, 12f, modifier = GlanceModifier.defaultWeight())
            }
        }
        if (scale <= 1.5f && entry.fields.tags.isNotEmpty()) {
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier.defaultWeight().cornerRadius(
                    13.dp,
                ).background(GlanceTheme.colors.surfaceVariant).padding(6.dp),
            ) {
                PomodoroLabel(entry.fields.tags.joinToString(", "), 12f)
            }
        }
        if (scale <= 1.5f && entry.fields.billable) {
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.cornerRadius(13.dp).background(GlanceTheme.colors.surfaceVariant).padding(6.dp)) {
                PomodoroLabel(context.getString(FieldsR.string.entry_billable), 12f)
            }
        }
    }
    // Large text spends a separate row on tags/billable instead of squeezing them into chips.
    if (scale > 1.5f && (entry.fields.tags.isNotEmpty() || entry.fields.billable)) {
        PomodoroLabel(
            (
                entry.fields.tags + if (entry.fields.billable) {
                    listOf(
                        context.getString(FieldsR.string.entry_billable),
                    )
                } else {
                    emptyList()
                }
                ).joinToString(" · "),
            11f,
            muted = true,
        )
    }
}

@Composable
internal fun PomodoroClock(
    context: Context,
    state: PomodoroWidgetState.Active,
    width: Float,
    requested: Float,
    now: Instant,
    ticking: Boolean,
) {
    val clock = pomodoroWidgetClock(state, now, SystemClock.elapsedRealtime())
    val metrics = context.resources.displayMetrics
    val paint = Paint().apply {
        typeface = context.resources.getFont(
            UiR.font.anybody_wdth_wght,
        )
        fontFeatureSettings = "tnum"
    }
    var sp = requested
    while (sp > 6f) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        if (paint.measureText(if (state.ended) "+00:00:00" else "0:00:00") + 2 <= width * metrics.density) break
        sp -= 0.5f
    }
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(R.id.widget_chronometer, clock.base, if (state.ended) "+%s" else null, ticking)
        setChronometerCountDown(R.id.widget_chronometer, clock.countDown)
        setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, sp)
        setColorInt(
            R.id.widget_chronometer,
            "setTextColor",
            (if (state.ended) TokklLightExtraColors.running else TokklLightColorScheme.onSurface).toArgb(),
            (if (state.ended) TokklDarkExtraColors.running else TokklDarkColorScheme.onSurface).toArgb(),
        )
        setContentDescription(
            R.id.widget_chronometer,
            context.getString(if (state.ended) UiR.string.pomodoro_overtime else UiR.string.pomodoro_remaining),
        )
        if (!ticking) {
            val seconds = Duration.between(
                if (state.ended) state.end else now,
                if (state.ended) now else state.end,
            ).seconds.coerceAtLeast(0)
            setTextViewText(
                R.id.widget_chronometer,
                (if (state.ended) "+" else "") + formatDuration(seconds).removePrefix("0:"),
            )
        }
    }
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
    val clockHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top) / metrics.density + 4f
    AndroidRemoteViews(
        views,
        modifier = GlanceModifier.fillMaxWidth().height(clockHeight.dp).semantics {
            contentDescription = context.getString(
                if (state.ended) UiR.string.pomodoro_overtime else UiR.string.pomodoro_remaining,
            )
        },
    )
}

@Composable
internal fun PomodoroLabel(
    text: String,
    size: Float,
    modifier: GlanceModifier = GlanceModifier,
    muted: Boolean = false,
    color: ColorProvider = if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
    lines: Int = 1,
    description: String? = null,
) {
    Text(
        text,
        modifier = if (description == null) modifier else modifier.semantics { contentDescription = description },
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium),
        maxLines = lines,
    )
}

internal fun phaseLabel(context: Context, state: PomodoroWidgetState.Active) = context.getString(
    when (state.session.phase.kind) {
        PhaseKind.WORK -> if (state.ended) R.string.pomodoro_widget_work_done else UiR.string.pomodoro_work

        PhaseKind.SHORT_REST -> {
            if (state.ended) R.string.pomodoro_widget_short_done else UiR.string.pomodoro_short_rest
        }

        PhaseKind.LONG_REST -> {
            if (state.ended) R.string.pomodoro_widget_long_done else UiR.string.pomodoro_long_rest
        }
    },
)

internal fun phaseColor(state: PomodoroWidgetState.Active) = if (state.ended) {
    RunningColor
} else if (state.session.phase.kind ==
    PhaseKind.WORK
) {
    ColorProvider(
        TokklLightColorScheme.primary,
        TokklDarkColorScheme.primary,
    )
} else {
    RestText
}
internal fun number(context: Context, state: PomodoroWidgetState.Active) = context.getString(
    UiR.string.pomodoro_number,
    state.session.number,
    state.session.longRestEvery,
)
internal fun nextLabel(context: Context, state: PomodoroWidgetState.Active) = context.getString(
    when (state.session.upcomingPhase) {
        PhaseKind.WORK -> UiR.string.pomodoro_start_work
        PhaseKind.SHORT_REST -> UiR.string.pomodoro_start_short_rest
        PhaseKind.LONG_REST -> UiR.string.pomodoro_start_long_rest
    },
)
internal fun defaultsLine(context: Context, state: PomodoroWidgetState.Idle) = state.defaults.run {
    context.getString(
        R.string.pomodoro_widget_defaults,
        workMinutes,
        shortRestMinutes,
        longRestMinutes,
        longRestEvery,
    )
}
internal fun defaultsDescription(context: Context, state: PomodoroWidgetState.Idle) = state.defaults.run {
    context.getString(
        R.string.pomodoro_widget_defaults_description,
        workMinutes,
        shortRestMinutes,
        longRestMinutes,
        longRestEvery,
    )
}
private fun projectLine(context: Context, entry: PomodoroWidgetEntry) = entry.project?.let { project ->
    entry.task?.let {
        context.getString(
            R.string.widget_project_task,
            project,
            it,
        )
    } ?: project
}
internal fun entryLine(context: Context, entry: PomodoroWidgetEntry): String {
    val text = entry.fields.description.ifBlank { context.getString(R.string.widget_no_description) }
    return projectLine(
        context,
        entry,
    )?.let { context.getString(R.string.pomodoro_widget_entry_project, text, it) } ?: text
}
