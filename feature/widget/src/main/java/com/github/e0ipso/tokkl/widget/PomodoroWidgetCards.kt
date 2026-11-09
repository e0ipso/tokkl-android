package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.unit.ColorProvider
import com.github.e0ipso.tokkl.feature.widget.R
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.time.formatDuration
import java.time.Duration
import java.time.Instant
import com.github.e0ipso.tokkl.core.ui.R as UiR

// The Pomodoro widget's cards at medium and large sizes, with their action row and icon buttons.

@Composable
internal fun ColumnScope.IdleCard(
    context: Context,
    state: PomodoroWidgetState.Idle,
    width: Float,
    height: Float,
    large: Boolean,
) {
    val scale = context.resources.configuration.fontScale
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PomodoroLabel(context.getString(UiR.string.pomodoro_title), 16f, modifier = GlanceModifier.defaultWeight())
        PomodoroLabel(context.getString(R.string.pomodoro_widget_idle), 12f, muted = true)
    }
    Spacer(GlanceModifier.height(10.dp))
    val defaults = state.defaults
    Row(GlanceModifier.fillMaxWidth().semantics { contentDescription = defaultsDescription(context, state) }) {
        listOf(
            R.string.pomodoro_widget_work to defaults.workMinutes,
            R.string.pomodoro_widget_short to defaults.shortRestMinutes,
            R.string.pomodoro_widget_long to defaults.longRestMinutes,
            R.string.pomodoro_widget_every to defaults.longRestEvery,
        ).forEach { (label, value) ->
            Column(GlanceModifier.defaultWeight()) {
                PomodoroLabel(context.getString(label), if (scale > 1.5f) 9f else 11f, muted = true)
                PomodoroLabel(value.toString(), if (scale > 1.5f) 18f else 22f)
            }
        }
    }
    if (height >= 230 || scale <= 1.5f) {
        Spacer(GlanceModifier.height(10.dp))
        PomodoroLabel(
            context.getString(R.string.pomodoro_widget_last, entryLine(context, state.entry)),
            13f,
            muted = true,
        )
        if (large) EntryChips(context, state.entry, width, scale)
    }
    Spacer(GlanceModifier.defaultWeight())
    Actions(
        context.getString(UiR.string.pomodoro_start),
        startPomodoroWidget(state),
        context.getString(R.string.pomodoro_widget_edit),
        context.getString(R.string.pomodoro_widget_edit_description),
        openPomodoro(context),
        width,
        scale,
    )
}

@Composable
internal fun ColumnScope.SessionCard(
    context: Context,
    state: PomodoroWidgetState.Active,
    width: Float,
    height: Float,
    large: Boolean,
    now: Instant,
    ticking: Boolean,
) {
    val scale = context.resources.configuration.fontScale
    // RemoteViews allows at most ten children per Column. Group the details so Glance never
    // silently drops the action row, including at the largest font.
    Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        if (scale <= 1.5f && !(state.ended && state.session.phase.kind != PhaseKind.WORK)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PomodoroLabel(phaseLabel(context, state), if (large) 18f else 16f, color = phaseColor(state))
                Spacer(GlanceModifier.width(8.dp))
                PomodoroLabel(number(context, state), 12f, muted = true, modifier = GlanceModifier.defaultWeight())
                Cycle(context, state)
            }
        } else {
            Column(GlanceModifier.fillMaxWidth()) {
                PomodoroLabel(phaseLabel(context, state), 16f, color = phaseColor(state))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PomodoroLabel(
                        number(context, state),
                        if (scale > 1.5f) 11f else 12f,
                        muted = true,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (scale <= 1.5f) Cycle(context, state)
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        PomodoroClock(
            context,
            state,
            width,
            if (large && scale > 1.5f) {
                28f
            } else if (large) {
                52f
            } else {
                40f
            },
            now,
            ticking,
        )
        if (scale <= 1.5f) {
            PomodoroLabel(
                if (state.ended) {
                    context.getString(UiR.string.pomodoro_overtime)
                } else {
                    context.getString(
                        R.string.pomodoro_widget_left_of,
                        formatDuration(Duration.between(state.start, state.end).seconds).removePrefix("0:"),
                    )
                },
                12f,
                muted = true,
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        PhaseBand(context, state)
        Column(GlanceModifier.fillMaxWidth()) {
            if (large) {
                Spacer(GlanceModifier.height(10.dp))
                if (scale <= 1.5f) PomodoroLabel(context.getString(R.string.pomodoro_widget_this), 11f, muted = true)
                PomodoroLabel(
                    state.entry.fields.description.ifBlank { context.getString(R.string.widget_no_description) },
                    if (scale > 1.5f) 12f else 15f,
                )
                EntryChips(context, state.entry, width, scale)
            } else if (scale <= 1.5f) {
                Spacer(GlanceModifier.height(8.dp))
                PomodoroLabel(
                    if (state.ended) {
                        context.getString(
                            UiR.string.pomodoro_alarm_waiting,
                        )
                    } else {
                        entryLine(context, state.entry)
                    },
                    13f,
                    muted = true,
                )
            }
        }
    }
    Actions(
        nextLabel(context, state),
        phasePomodoroWidget(state),
        context.getString(if (large) UiR.string.pomodoro_end else R.string.pomodoro_widget_end),
        context.getString(UiR.string.pomodoro_end),
        phasePomodoroWidget(state, end = true),
        width,
        scale,
        state.ended,
    )
}

@Composable
private fun Actions(
    primary: String,
    action: Action,
    secondary: String,
    secondaryDescription: String,
    secondaryAction: Action,
    width: Float,
    scale: Float,
    ended: Boolean = false,
) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // At 200%, keep the full action name for TalkBack and use an icon where the words cannot fit.
        if (scale > 1.5f) {
            PomodoroIcon(
                primary,
                if (primary == LocalContext.current.getString(
                        UiR.string.pomodoro_start,
                    )
                ) {
                    UiR.drawable.ic_play
                } else {
                    R.drawable.ic_widget_next
                },
                action,
                48f,
                if (ended) RunningColor else PrimaryFill,
            )
            Spacer(GlanceModifier.defaultWeight())
            PomodoroIcon(
                secondaryDescription,
                if (secondaryDescription == LocalContext.current.getString(
                        UiR.string.pomodoro_end,
                    )
                ) {
                    UiR.drawable.ic_close
                } else {
                    UiR.drawable.ic_edit
                },
                secondaryAction,
                48f,
                GlanceTheme.colors.surface,
                GlanceTheme.colors.onSurface,
            )
        } else {
            Box(
                GlanceModifier.defaultWeight().height(
                    48.dp,
                ).cornerRadius(12.dp).background(if (ended) RunningColor else PrimaryFill)
                    .clickable(action).semantics { contentDescription = primary },
                contentAlignment = Alignment.Center,
            ) {
                PomodoroLabel(primary, 14f, color = OnPrimaryFill)
            }
            Spacer(GlanceModifier.width(8.dp))
            val context = LocalContext.current
            val metrics = context.resources.displayMetrics
            val labelPaint = Paint().apply {
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    14f,
                    metrics,
                )
            }
            val secondaryWidth = (labelPaint.measureText(secondary) / metrics.density + 32f).coerceAtLeast(48f)
            Box(
                GlanceModifier.width(
                    secondaryWidth.dp,
                ).height(
                    48.dp,
                ).background(ImageProvider(R.drawable.pomodoro_widget_outline)).padding(horizontal = 12.dp)
                    .clickable(
                        secondaryAction,
                    ).semantics { contentDescription = secondaryDescription },
                contentAlignment = Alignment.Center,
            ) {
                PomodoroLabel(secondary, 14f)
            }
        }
    }
}

@Composable
internal fun PomodoroIcon(
    label: String,
    icon: Int,
    action: Action,
    size: Float,
    background: ColorProvider = PrimaryFill,
    foreground: ColorProvider = OnPrimaryFill,
) {
    SquareIconButton(
        ImageProvider(icon),
        label,
        action,
        modifier = GlanceModifier.size(size.dp),
        backgroundColor = background,
        contentColor = foreground,
    )
}
