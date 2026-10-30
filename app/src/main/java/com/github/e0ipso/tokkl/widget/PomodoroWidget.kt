package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.color.ColorProvider
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Server-owned session controls. Only the launcher's Chronometer ticks between state changes. */
class PomodoroWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = (context.applicationContext as TokklApplication).pomodoroWidgetState
        val initial = states.first()
        provideContent {
            val state by states.collectAsState(initial)
            GlanceTheme(colors = PomodoroColors) { PomodoroWidgetContent(state) }
        }
    }
}

class PomodoroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PomodoroWidget()
}

internal val PomodoroColors = ColorProviders(light = TokklLightColorScheme, dark = TokklDarkColorScheme)
private val Running = ColorProvider(TokklLightExtraColors.running, TokklDarkExtraColors.running)
private val PrimaryFill = ColorProvider(TokklLightExtraColors.primaryFill, TokklDarkExtraColors.primaryFill)
private val OnPrimary = ColorProvider(TokklLightExtraColors.onPrimaryFill, TokklDarkExtraColors.onPrimaryFill)
// The light secondary fill is 3.06:1 on white. Use a darker teal for small text (4.9:1).
private val RestText = ColorProvider(Color(0xFF007A7A), TokklDarkColorScheme.secondary)

/** The same content is rendered in a native RemoteViews host for deterministic layout references. */
@Composable
internal fun PomodoroWidgetContent(state: PomodoroWidgetState, now: Instant = Instant.now(), ticking: Boolean = true) {
    val context = LocalContext.current
    val bounds = LocalSize.current
    val large = bounds.width.value >= WidgetLayout.LARGE_WIDTH_DP && bounds.height.value >= WidgetLayout.LARGE_HEIGHT_DP
    val card = bounds.width.value >= WidgetLayout.MEDIUM_WIDTH_DP && bounds.height.value >= WidgetLayout.MEDIUM_HEIGHT_DP
    val tiny = bounds.height.value < 80 || bounds.width.value < 180
    val padding = if (tiny) 2f else if (large) 16f else 12f
    val width = bounds.width.value - padding * 2
    val height = bounds.height.value - padding * 2
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.surface)
        .cornerRadius(16.dp).clickable(openPomodoro(context)).padding(padding.dp)) {
        when (state) {
            PomodoroWidgetState.NoAccount -> Message(context.getString(R.string.widget_sign_in), tiny)
            PomodoroWidgetState.Unsupported -> Message(context.getString(R.string.pomodoro_widget_unsupported), tiny)
            is PomodoroWidgetState.Idle -> if (card) IdleCard(context, state, width, height, large) else IdleStrip(context, state, width, tiny)
            is PomodoroWidgetState.Active -> if (card) SessionCard(context, state, width, height, large, now, ticking) else SessionStrip(context, state, width, height, tiny, now, ticking)
        }
    }
}

private fun openPomodoro(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(WidgetNavigation.OPEN_POMODORO, true),
)

@Composable
private fun Message(message: String, tiny: Boolean) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Label(message, if (tiny) 6f else if (LocalContext.current.resources.configuration.fontScale > 1.5f && LocalSize.current.height.value < 160f) 9f else 14f, lines = if (tiny) 2 else 4, description = message)
    }
}

@Composable
private fun IdleStrip(context: Context, state: PomodoroWidgetState.Idle, width: Float, tiny: Boolean) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Label(context.getString(R.string.pomodoro_widget_idle), if (tiny) 8f else if (context.resources.configuration.fontScale > 1.5f) 12f else 14f)
            if (!tiny) {
                Label(defaultsLine(context, state), 12f, muted = true,
                    description = defaultsDescription(context, state))
            }
        }
        Icon(context.getString(R.string.pomodoro_start), R.drawable.ic_play, startPomodoroWidget(state), if (tiny) 36f else 48f)
    }
}

@Composable
private fun SessionStrip(context: Context, state: PomodoroWidgetState.Active, width: Float, height: Float, tiny: Boolean, now: Instant, ticking: Boolean) {
    val button = if (tiny) 36f else 48f
    val scale = context.resources.configuration.fontScale
    val phase = phaseLabel(context, state)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            val heading = if (state.session.phase.kind == PhaseKind.WORK && !tiny) context.getString(R.string.pomodoro_widget_phase_number, phase, state.session.number, state.session.longRestEvery) else phase
            Label(heading, if (tiny) 6f else if (scale > 1.5f && state.ended) 8f else if (scale > 1.5f) 11f else 14f, color = phaseColor(state),
                description = "$phase, ${number(context, state)}")
            Clock(context, state, width - button - 6f, if (tiny) 9f else 26f, now, ticking)
            if (!tiny) PhaseBand(context, state)
        }
        Spacer(GlanceModifier.width(6.dp))
        Icon(nextLabel(context, state), R.drawable.ic_widget_next, phasePomodoroWidget(state), button, if (state.ended) Running else PrimaryFill)
    }
}

@Composable
private fun ColumnScope.IdleCard(context: Context, state: PomodoroWidgetState.Idle, width: Float, height: Float, large: Boolean) {
    val scale = context.resources.configuration.fontScale
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(context.getString(R.string.pomodoro_title), 16f, modifier = GlanceModifier.defaultWeight())
        Label(context.getString(R.string.pomodoro_widget_idle), 12f, muted = true)
    }
    Spacer(GlanceModifier.height(10.dp))
    val defaults = state.defaults
    Row(GlanceModifier.fillMaxWidth().semantics { contentDescription = defaultsDescription(context, state) }) {
        listOf(R.string.pomodoro_widget_work to defaults.workMinutes, R.string.pomodoro_widget_short to defaults.shortRestMinutes,
            R.string.pomodoro_widget_long to defaults.longRestMinutes, R.string.pomodoro_widget_every to defaults.longRestEvery).forEach { (label, value) ->
            Column(GlanceModifier.defaultWeight()) {
                Label(context.getString(label), if (scale > 1.5f) 9f else 11f, muted = true)
                Label(value.toString(), if (scale > 1.5f) 18f else 22f)
            }
        }
    }
    if (height >= 230 || scale <= 1.5f) {
        Spacer(GlanceModifier.height(10.dp))
        Label(context.getString(R.string.pomodoro_widget_last, entryLine(context, state.entry)), 13f, muted = true)
        if (large) EntryChips(context, state.entry, width, scale)
    }
    Spacer(GlanceModifier.defaultWeight())
    Actions(context.getString(R.string.pomodoro_start), startPomodoroWidget(state),
        context.getString(R.string.pomodoro_widget_edit), context.getString(R.string.pomodoro_widget_edit_description), openPomodoro(context), width, scale)
}

@Composable
private fun ColumnScope.SessionCard(context: Context, state: PomodoroWidgetState.Active, width: Float, height: Float, large: Boolean, now: Instant, ticking: Boolean) {
    val scale = context.resources.configuration.fontScale
    // RemoteViews allows at most ten children per Column. Group the details so Glance never
    // silently drops the action row, including at the largest font.
    Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        if (scale <= 1.5f && !(state.ended && state.session.phase.kind != PhaseKind.WORK)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(phaseLabel(context, state), if (large) 18f else 16f, color = phaseColor(state))
                Spacer(GlanceModifier.width(8.dp))
                Label(number(context, state), 12f, muted = true, modifier = GlanceModifier.defaultWeight())
                Cycle(context, state)
            }
        } else {
            Column(GlanceModifier.fillMaxWidth()) {
                Label(phaseLabel(context, state), 16f, color = phaseColor(state))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(number(context, state), if (scale > 1.5f) 11f else 12f, muted = true, modifier = GlanceModifier.defaultWeight())
                    if (scale <= 1.5f) Cycle(context, state)
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Clock(context, state, width, if (large && scale > 1.5f) 28f else if (large) 52f else 40f, now, ticking)
        if (scale <= 1.5f) {
            Label(if (state.ended) context.getString(R.string.pomodoro_overtime) else
                context.getString(R.string.pomodoro_widget_left_of, formatDuration(Duration.between(state.start, state.end).seconds).removePrefix("0:")), 12f, muted = true)
        }
        Spacer(GlanceModifier.height(6.dp))
        PhaseBand(context, state)
        Column(GlanceModifier.fillMaxWidth()) {
            if (large) {
                Spacer(GlanceModifier.height(10.dp))
                if (scale <= 1.5f) Label(context.getString(R.string.pomodoro_widget_this), 11f, muted = true)
                Label(state.entry.fields.description.ifBlank { context.getString(R.string.widget_no_description) }, if (scale > 1.5f) 12f else 15f)
                EntryChips(context, state.entry, width, scale)
            } else if (scale <= 1.5f) {
                Spacer(GlanceModifier.height(8.dp))
                Label(if (state.ended) context.getString(R.string.pomodoro_alarm_waiting) else entryLine(context, state.entry), 13f, muted = true)
            }
        }
    }
    Actions(nextLabel(context, state), phasePomodoroWidget(state),
        context.getString(if (large) R.string.pomodoro_end else R.string.pomodoro_widget_end), context.getString(R.string.pomodoro_end),
        phasePomodoroWidget(state, end = true), width, scale, state.ended)
}

@Composable
private fun Actions(primary: String, action: Action, secondary: String, secondaryDescription: String, secondaryAction: Action, width: Float, scale: Float, ended: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // At 200%, keep the full action name for TalkBack and use an icon where the words cannot fit.
        if (scale > 1.5f) {
            Icon(primary, if (primary == LocalContext.current.getString(R.string.pomodoro_start)) R.drawable.ic_play else R.drawable.ic_widget_next,
                action, 48f, if (ended) Running else PrimaryFill)
            Spacer(GlanceModifier.defaultWeight())
            Icon(secondaryDescription, if (secondaryDescription == LocalContext.current.getString(R.string.pomodoro_end)) R.drawable.ic_close else R.drawable.ic_edit,
                secondaryAction, 48f, GlanceTheme.colors.surface, GlanceTheme.colors.onSurface)
        } else {
            Box(GlanceModifier.defaultWeight().height(48.dp).cornerRadius(12.dp).background(if (ended) Running else PrimaryFill)
                .clickable(action).semantics { contentDescription = primary }, contentAlignment = Alignment.Center) {
                Label(primary, 14f, color = OnPrimary)
            }
            Spacer(GlanceModifier.width(8.dp))
            val context = LocalContext.current
            val metrics = context.resources.displayMetrics
            val labelPaint = Paint().apply { textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, metrics) }
            val secondaryWidth = (labelPaint.measureText(secondary) / metrics.density + 32f).coerceAtLeast(48f)
            Box(GlanceModifier.width(secondaryWidth.dp).height(48.dp).background(ImageProvider(R.drawable.pomodoro_widget_outline)).padding(horizontal = 12.dp)
                .clickable(secondaryAction).semantics { contentDescription = secondaryDescription }, contentAlignment = Alignment.Center) {
                Label(secondary, 14f)
            }
        }
    }
}

@Composable
private fun Icon(label: String, icon: Int, action: Action, size: Float, background: ColorProvider = PrimaryFill, foreground: ColorProvider = OnPrimary) {
    SquareIconButton(ImageProvider(icon), label, action, modifier = GlanceModifier.size(size.dp), backgroundColor = background, contentColor = foreground)
}

@Composable
private fun PhaseBand(context: Context, state: PomodoroWidgetState.Active) {
    // The launcher ticks the native clock, but cannot tick a Glance progress bar. Use the
    // phase colour without a frozen elapsed percentage or a periodic redraw job.
    val description = phaseLabel(context, state)
    Box(GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp).background(GlanceTheme.colors.surfaceVariant)
        .semantics { contentDescription = description }) {
        Box(GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp)
            .background(if (state.ended) Running else if (state.session.phase.kind == PhaseKind.WORK) PrimaryFill else GlanceTheme.colors.secondary)) {}
    }
}

@Composable
private fun Cycle(context: Context, state: PomodoroWidgetState.Active) {
    val session = state.session
    val description = context.getString(R.string.pomodoro_widget_cycle, session.number, session.longRestEvery, session.phase.completed)
    Row(GlanceModifier.semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        // One child per work/rest pair keeps the RemoteViews child limit even at every 12.
        val dot = if (session.longRestEvery > 6) 4f else 8f
        (0 until session.longRestEvery.coerceIn(1, 12)).chunked(2).forEach { indices ->
            Row(verticalAlignment = Alignment.CenterVertically) { indices.forEach { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(dot.dp).cornerRadius(dot.dp).background(
                    if (index < session.phase.completed || index + 1 == session.number) PrimaryFill else GlanceTheme.colors.surfaceVariant)) {}
                Spacer(GlanceModifier.width(3.dp))
                Box(GlanceModifier.size((dot / 2).dp).cornerRadius(dot.dp).background(
                    if (index + 1 < session.number || (index + 1 == session.number && session.phase.kind != PhaseKind.WORK)) GlanceTheme.colors.secondary else GlanceTheme.colors.surfaceVariant)) {}
                Spacer(GlanceModifier.width(3.dp))
            }
            } }
        }
    }
}

@Composable
private fun EntryChips(context: Context, entry: PomodoroWidgetEntry, width: Float, scale: Float) {
    Spacer(GlanceModifier.height(8.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val project = projectLine(context, entry)
        if (project != null) {
            Row(GlanceModifier.defaultWeight().cornerRadius(13.dp).background(GlanceTheme.colors.surfaceVariant).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val color = runCatching { ColorProvider(Color(android.graphics.Color.parseColor(entry.projectColor))) }.getOrElse { GlanceTheme.colors.onSurfaceVariant }
                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(color)) {}
                Spacer(GlanceModifier.width(5.dp))
                Label(project, 12f, modifier = GlanceModifier.defaultWeight())
            }
        }
        if (scale <= 1.5f && entry.fields.tags.isNotEmpty()) {
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.defaultWeight().cornerRadius(13.dp).background(GlanceTheme.colors.surfaceVariant).padding(6.dp)) {
                Label(entry.fields.tags.joinToString(", "), 12f)
            }
        }
        if (scale <= 1.5f && entry.fields.billable) {
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.cornerRadius(13.dp).background(GlanceTheme.colors.surfaceVariant).padding(6.dp)) {
                Label(context.getString(R.string.entry_billable), 12f)
            }
        }
    }
    // Large text spends a separate row on tags/billable instead of squeezing them into chips.
    if (scale > 1.5f && (entry.fields.tags.isNotEmpty() || entry.fields.billable)) {
        Label((entry.fields.tags + if (entry.fields.billable) listOf(context.getString(R.string.entry_billable)) else emptyList()).joinToString(" · "), 11f, muted = true)
    }
}

@Composable
private fun Clock(context: Context, state: PomodoroWidgetState.Active, width: Float, requested: Float, now: Instant, ticking: Boolean) {
    val clock = pomodoroWidgetClock(state, now, SystemClock.elapsedRealtime())
    val metrics = context.resources.displayMetrics
    val paint = Paint().apply { typeface = context.resources.getFont(R.font.anybody_wdth_wght); fontFeatureSettings = "tnum" }
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
        setColorInt(R.id.widget_chronometer, "setTextColor",
            (if (state.ended) TokklLightExtraColors.running else TokklLightColorScheme.onSurface).toArgb(),
            (if (state.ended) TokklDarkExtraColors.running else TokklDarkColorScheme.onSurface).toArgb())
        setContentDescription(R.id.widget_chronometer, context.getString(if (state.ended) R.string.pomodoro_overtime else R.string.pomodoro_remaining))
        if (!ticking) {
            val seconds = Duration.between(if (state.ended) state.end else now, if (state.ended) now else state.end).seconds.coerceAtLeast(0)
            setTextViewText(R.id.widget_chronometer, (if (state.ended) "+" else "") + formatDuration(seconds).removePrefix("0:"))
        }
    }
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
    val clockHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top) / metrics.density + 4f
    AndroidRemoteViews(views, modifier = GlanceModifier.fillMaxWidth().height(clockHeight.dp).semantics {
        contentDescription = context.getString(if (state.ended) R.string.pomodoro_overtime else R.string.pomodoro_remaining)
    })
}

@Composable
private fun Label(text: String, size: Float, modifier: GlanceModifier = GlanceModifier, muted: Boolean = false,
                  color: ColorProvider = if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                  lines: Int = 1, description: String? = null) {
    Text(text, modifier = if (description == null) modifier else modifier.semantics { contentDescription = description },
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium), maxLines = lines)
}

private fun phaseLabel(context: Context, state: PomodoroWidgetState.Active) = context.getString(when (state.session.phase.kind) {
    PhaseKind.WORK -> if (state.ended) R.string.pomodoro_widget_work_done else R.string.pomodoro_work
    PhaseKind.SHORT_REST -> if (state.ended) R.string.pomodoro_widget_short_done else R.string.pomodoro_short_rest
    PhaseKind.LONG_REST -> if (state.ended) R.string.pomodoro_widget_long_done else R.string.pomodoro_long_rest
})

private fun phaseColor(state: PomodoroWidgetState.Active) = if (state.ended) Running else if (state.session.phase.kind == PhaseKind.WORK) ColorProvider(TokklLightColorScheme.primary, TokklDarkColorScheme.primary) else RestText
private fun number(context: Context, state: PomodoroWidgetState.Active) = context.getString(R.string.pomodoro_number, state.session.number, state.session.longRestEvery)
private fun nextLabel(context: Context, state: PomodoroWidgetState.Active) = context.getString(when (state.session.upcomingPhase) {
    PhaseKind.WORK -> R.string.pomodoro_start_work
    PhaseKind.SHORT_REST -> R.string.pomodoro_start_short_rest
    PhaseKind.LONG_REST -> R.string.pomodoro_start_long_rest
})
private fun defaultsLine(context: Context, state: PomodoroWidgetState.Idle) = state.defaults.run { context.getString(R.string.pomodoro_widget_defaults, workMinutes, shortRestMinutes, longRestMinutes, longRestEvery) }
private fun defaultsDescription(context: Context, state: PomodoroWidgetState.Idle) = state.defaults.run { context.getString(R.string.pomodoro_widget_defaults_description, workMinutes, shortRestMinutes, longRestMinutes, longRestEvery) }
private fun projectLine(context: Context, entry: PomodoroWidgetEntry) = entry.project?.let { project -> entry.task?.let { context.getString(R.string.widget_project_task, project, it) } ?: project }
private fun entryLine(context: Context, entry: PomodoroWidgetEntry): String {
    val text = entry.fields.description.ifBlank { context.getString(R.string.widget_no_description) }
    return projectLine(context, entry)?.let { context.getString(R.string.pomodoro_widget_entry_project, text, it) } ?: text
}
