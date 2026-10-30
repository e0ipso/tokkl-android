package com.github.e0ipso.tokkl.widget

import android.content.Context
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
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.formatDurationWithoutSeconds
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Cached Timer and Today report. The launcher ticks both clocks; redraws follow existing events. */
class TokklWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = (context.applicationContext as TokklApplication).widgetState
        val initial = states.first()
        provideContent {
            val state by states.collectAsState(initial)
            GlanceTheme(colors = WidgetColors) { TimerWidgetContent(state) }
        }
    }
}

class TokklWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TokklWidget()
}

private val WidgetColors = ColorProviders(light = TokklLightColorScheme, dark = TokklDarkColorScheme)
private val ChartBand = ColorProvider(TokklLightExtraColors.chartBand, TokklDarkExtraColors.chartBand)
private val RunningColor = ColorProvider(TokklLightExtraColors.running, TokklDarkExtraColors.running)
private val PrimaryFill = ColorProvider(TokklLightExtraColors.primaryFill, TokklDarkExtraColors.primaryFill)
private val OnPrimaryFill = ColorProvider(TokklLightExtraColors.onPrimaryFill, TokklDarkExtraColors.onPrimaryFill)
private val BillableColor = ColorProvider(TokklLightExtraColors.billable, TokklDarkExtraColors.billable)

/** Also used by native-host screenshot tests, with fixed cached state. */
@Composable
internal fun TimerWidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val kind = timerWidgetSize(size.width.value, size.height.value)
    val padding = if (size.height.value < 64 || size.width.value < 150) 2f else WidgetLayout.PADDING_DP
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.surface).cornerRadius(16.dp).padding(padding.dp)
            .clickable(actionStartActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TIMER))),
        verticalAlignment = if (kind == TimerWidgetSize.STRIP) Alignment.CenterVertically else Alignment.Top,
    ) {
        when (state) {
            WidgetState.NoAccount -> Box(
                GlanceModifier.fillMaxSize().clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java)))
                    .semantics { contentDescription = context.getString(R.string.widget_sign_in) },
                contentAlignment = Alignment.Center,
            ) { Label(context.getString(R.string.widget_sign_in), 14f) }
            is WidgetState.Ready -> when (kind) {
                TimerWidgetSize.STRIP -> TimerStrip(context, state, padding)
                else -> TimerCard(context, state, kind)
            }
        }
    }
}

@Composable
private fun TimerStrip(context: Context, state: WidgetState.Ready, padding: Float) {
    val size = LocalSize.current
    val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val button = minOf(48f, size.height.value - 2 * padding)
    val available = size.width.value - 2 * padding - button - 6
    val details = size.height.value - 2 * padding >= 68f + 44f * (scale - 1f)
    val description = description(context, state)
    val project = projectText(context, state)
    val status = if (state.paused != null) context.getString(R.string.widget_paused_label) else description
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().semantics { contentDescription = listOfNotNull(status, project).joinToString(", ") }) {
            if (details) {
                if (state.running != null || state.paused != null) {
                    ProjectLine(project ?: context.getString(R.string.widget_no_project), projectColor(state), 11f)
                }
                Label(if (state.paused != null) context.getString(R.string.widget_paused_entry, description) else description, 14f)
                if (state.running == null && state.paused == null) Label(context.getString(R.string.widget_today), 10f, muted = true)
            }
            TimerClock(context, state, if (details) 24f else 18f, available)
        }
        Spacer(GlanceModifier.width(6.dp))
        PrimaryControl(context, state, button)
    }
}

@Composable
private fun ColumnScope.TimerCard(context: Context, state: WidgetState.Ready, kind: TimerWidgetSize) {
    val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val large = kind == TimerWidgetSize.LARGE
    val width = LocalSize.current.width.value - 2 * WidgetLayout.PADDING_DP
    val active = state.running != null || state.paused != null
    Row(GlanceModifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription = listOfNotNull(description(context, state), projectText(context, state), state.paused?.let { context.getString(R.string.widget_paused_label) }).joinToString(", ")
    }, verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Label(if (state.paused != null) context.getString(R.string.widget_paused_entry, description(context, state)) else description(context, state), if (large) 17f else 16f)
            if (large && scale < 1.5f && active) ProjectLine(projectText(context, state) ?: context.getString(R.string.widget_no_project), projectColor(state), 12f)
        }
        Spacer(GlanceModifier.width(8.dp))
        if (state.running?.pausable == true) {
            Control(context.getString(R.string.widget_pause_description), R.drawable.ic_pause, pauseTimer(state.running.target), 48f, GlanceTheme.colors.surfaceVariant, GlanceTheme.colors.onSurface)
            Spacer(GlanceModifier.width(8.dp))
        } else if (state.paused != null) {
            Control(context.getString(R.string.widget_stop_description), R.drawable.ic_stop, stopTimer(state.paused.target), 48f, GlanceTheme.colors.surfaceVariant, GlanceTheme.colors.onSurface)
            Spacer(GlanceModifier.width(8.dp))
        }
        PrimaryControl(context, state, 48f)
    }
    if (active) {
        Row(GlanceModifier.fillMaxWidth().height(WidgetLayout.clockRowHeight(large, scale).dp), verticalAlignment = Alignment.CenterVertically) {
            TimerClock(context, state, if (large) 34f else 22f, if (large) width else width * .55f)
            if (!large) {
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.defaultWeight()) { ProjectLine(projectText(context, state) ?: context.getString(R.string.widget_no_project), projectColor(state), 12f) }
            }
        }
    }
    Spacer(GlanceModifier.height(4.dp))
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline)) {}
    Spacer(GlanceModifier.height(5.dp))
    val day = state.day
    val reportAction = actionStartActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TODAY))
    val templates = large && state.favorites.isNotEmpty()
    val count = timerWidgetProjectRows(LocalSize.current.height.value, kind, scale, active, templates)
    val segments = timerWidgetSegments(day?.projects.orEmpty())
    Column(GlanceModifier.fillMaxWidth().clickable(reportAction)) {
        if (large) {
            Row(GlanceModifier.fillMaxWidth().height(WidgetLayout.reportHeadingHeight(scale).dp).semantics { contentDescription = context.getString(R.string.widget_open_report_description) }, verticalAlignment = Alignment.CenterVertically) {
                Label(context.getString(R.string.widget_today), 11f, muted = true)
                if (scale < 1.5f) {
                    Spacer(GlanceModifier.width(8.dp))
                    Label(day?.date?.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).orEmpty(), 11f, muted = true)
                }
                Spacer(GlanceModifier.defaultWeight())
                Label(context.getString(R.string.widget_open_report), 11f, color = GlanceTheme.colors.primary)
            }
            Row(GlanceModifier.fillMaxWidth().height(WidgetLayout.statsHeight(scale).dp)) {
                Column(GlanceModifier.defaultWeight()) {
                    Label(context.getString(R.string.widget_total), 10f, muted = true)
                    DayClock(context, day, 16f, width / 3 - 6)
                }
                Column(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(BillableColor)
                        Spacer(GlanceModifier.width(4.dp))
                        Label(context.getString(R.string.widget_billable), 10f, muted = true)
                    }
                    StaticDuration(context, day?.formattedBillable(Instant.now()) ?: formatDuration(0), 16f, width / 3 - 6)
                }
                Column(GlanceModifier.defaultWeight()) {
                    Label(context.getString(R.string.widget_entries), 10f, muted = true)
                    StaticDuration(context, (day?.entryCount ?: 0).toString(), 16f, width / 3 - 6)
                }
            }
        } else {
            Row(GlanceModifier.fillMaxWidth().height(WidgetLayout.todayRowHeight(scale).dp).semantics { contentDescription = context.getString(R.string.widget_open_report_description) }, verticalAlignment = Alignment.CenterVertically) {
                Label(context.getString(R.string.widget_today), 11f, muted = true)
                Spacer(GlanceModifier.defaultWeight())
                DayClock(context, day, 18f, width - 64 * scale)
            }
            StackedBar(context, segments, day, width)
            Spacer(GlanceModifier.height(6.dp))
        }
        val billableRow = !active && !large && count > 0
        segments.take(if (billableRow) count - 1 else count).forEachIndexed { index, project ->
            val other = index == WidgetLayout.MAX_SEGMENTS - 1 && day!!.projects.size > WidgetLayout.MAX_SEGMENTS
            ProjectRow(context, project, day, other, large, width, scale)
        }
        if (billableRow) Label(context.getString(R.string.widget_billable_total, day?.formattedBillable(Instant.now()) ?: formatDuration(0)), 12f, muted = true)
    }
    if (templates) {
        Spacer(GlanceModifier.defaultWeight())
        TemplateChips(context, state.favorites, width, scale)
    }
}

@Composable
private fun PrimaryControl(context: Context, state: WidgetState.Ready, size: Float) {
    when {
        state.running != null -> Control(context.getString(R.string.widget_stop_description), R.drawable.ic_stop, stopTimer(state.running.target), size, GlanceTheme.colors.error, GlanceTheme.colors.onError)
        state.paused != null -> Control(context.getString(R.string.widget_resume_description), R.drawable.ic_play, resumeTimer(state.paused.target), size, PrimaryFill, OnPrimaryFill)
        else -> Control(context.getString(R.string.widget_start_description), R.drawable.ic_play, startTimer(), size, PrimaryFill, OnPrimaryFill)
    }
}

@Composable
private fun Control(label: String, icon: Int, action: Action, size: Float, background: ColorProvider, foreground: ColorProvider) {
    SquareIconButton(ImageProvider(icon), label, action, GlanceModifier.size(size.dp), backgroundColor = background, contentColor = foreground)
}

@Composable
private fun TimerClock(context: Context, state: WidgetState.Ready, size: Float, width: Float) {
    val running = state.running
    if (running != null) {
        Elapsed(context, running.start, running.accumulatedSeconds, size, width, context.getString(R.string.widget_elapsed))
    } else {
        val text = if (state.paused != null) formatDuration(state.paused.seconds, state.day?.durationFormat ?: DurationFormat.IMPROVED)
        else state.day?.formattedTotal(Instant.now()) ?: formatDuration(0)
        StaticDuration(context, text, size, width)
    }
}

@Composable
private fun DayClock(context: Context, day: WidgetDayReport?, size: Float, width: Float) {
    if (day?.running != null) {
        Elapsed(context, day.running.start, day.stoppedSeconds, size, width, context.getString(R.string.widget_today_total))
    } else {
        StaticDuration(context, day?.formattedTotal(Instant.now()) ?: formatDuration(0), size, width)
    }
}

/** Measure at the effective system font scale, then give the RemoteViews child its exact width. */
private fun clockPaint(context: Context, size: Float): Paint = Paint().apply {
    typeface = context.resources.getFont(R.font.anybody_wdth_wght)
    fontVariationSettings = "'wght' 600, 'wdth' 85"
    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, context.resources.displayMetrics)
    fontFeatureSettings = "tnum"
}

private fun fittedClockSize(context: Context, sample: String, requested: Float, width: Float): Float {
    val density = context.resources.displayMetrics.density
    var size = requested
    while (size > 6f && clockPaint(context, size).measureText(sample) + 4 > width * density) size -= .5f
    return size
}

@Composable
private fun Elapsed(context: Context, start: Instant, accumulated: Long, requested: Float, maxWidth: Float, label: String) {
    val now = Instant.now()
    val seconds = accumulated + java.time.Duration.between(start, now).seconds.coerceAtLeast(0)
    // Reserve the next hour's wider form as well as the time drawn now.
    val sample = "0".repeat(maxOf(2, (seconds / 3600 + 1).toString().length)) + ":00:00"
    val size = fittedClockSize(context, sample, requested, maxWidth)
    val paint = clockPaint(context, size)
    val density = context.resources.displayMetrics.density
    val width = minOf(maxWidth, (paint.measureText(sample) + 4) / density)
    val height = (paint.fontMetrics.bottom - paint.fontMetrics.top) / density + 4
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(R.id.widget_chronometer, chronometerBase(start, now, SystemClock.elapsedRealtime(), accumulated), null, true)
        setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, size)
        setContentDescription(R.id.widget_chronometer, label)
        setColorInt(R.id.widget_chronometer, "setTextColor", TokklLightColorScheme.onSurface.toArgb(), TokklDarkColorScheme.onSurface.toArgb())
    }
    AndroidRemoteViews(views, modifier = GlanceModifier.width(width.dp).height(height.dp).semantics { contentDescription = label })
}

@Composable
private fun StaticDuration(context: Context, text: String, requested: Float, width: Float, color: ColorProvider = GlanceTheme.colors.onSurface) {
    val maxWidth = width.coerceAtLeast(1f)
    val size = fittedClockSize(context, text, requested, maxWidth)
    val paint = clockPaint(context, size)
    val density = context.resources.displayMetrics.density
    val views = RemoteViews(context.packageName, R.layout.widget_duration).apply {
        setTextViewText(R.id.widget_duration, text)
        setTextViewTextSize(R.id.widget_duration, TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(R.id.widget_duration, color.getColor(context).toArgb())
    }
    AndroidRemoteViews(views, modifier = GlanceModifier.width(minOf(maxWidth, (paint.measureText(text) + 4) / density).dp)
        .height(((paint.fontMetrics.bottom - paint.fontMetrics.top) / density + 4).dp))
}

@Composable
private fun ProjectLine(text: String, color: String?, size: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(projectColor(color))
        Spacer(GlanceModifier.width(5.dp))
        Label(text, size, muted = true)
    }
}

@Composable
private fun Dot(color: ColorProvider) {
    Image(ImageProvider(R.drawable.widget_dot), null, GlanceModifier.size(8.dp), colorFilter = ColorFilter.tint(color))
}

@Composable
private fun StackedBar(context: Context, projects: List<WidgetDayProject>, day: WidgetDayReport?, width: Float) {
    val total = projects.sumOf { it.stoppedSeconds }.coerceAtLeast(1)
    val label = projects.mapIndexed { index, project ->
        val name = projectName(context, project, index == 4 && day!!.projects.size > 5)
        val duration = project.formattedStopped.ifEmpty { formatDurationWithoutSeconds(project.stoppedSeconds, day?.durationFormat ?: DurationFormat.IMPROVED) }
        if (project.running) context.getString(R.string.widget_project_running, name, duration) else "$name, $duration"
    }.joinToString("; ")
    Row(GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp).background(ChartBand).semantics { contentDescription = label }) {
        projects.forEach { project ->
            if (project.stoppedSeconds > 0) Box(GlanceModifier.width((width * project.stoppedSeconds / total).dp).height(8.dp).background(projectColor(project.projectColor))) {}
        }
    }
}

@Composable
private fun ProjectRow(context: Context, project: WidgetDayProject, day: WidgetDayReport?, other: Boolean, large: Boolean, width: Float, scale: Float) {
    val name = projectName(context, project, other)
    val duration = project.formattedStopped.ifEmpty { formatDurationWithoutSeconds(project.stoppedSeconds, day?.durationFormat ?: DurationFormat.IMPROVED) }
    val label = if (project.running) context.getString(R.string.widget_project_running, name, duration) else "$name, $duration"
    Column(GlanceModifier.fillMaxWidth().height(((if (large) 28f else 18f) * scale).dp).semantics { contentDescription = label }) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!large) { Dot(projectColor(project.projectColor)); Spacer(GlanceModifier.width(5.dp)) }
            Text(name, GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Label(duration, 12f, muted = true)
            if (project.running) { Spacer(GlanceModifier.width(4.dp)); Dot(RunningColor) }
        }
        if (large) {
            val total = day?.stoppedSeconds?.coerceAtLeast(1) ?: 1
            Box(GlanceModifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp).background(ChartBand)) {
                if (project.stoppedSeconds > 0) Box(GlanceModifier.width((width * project.stoppedSeconds / total).dp).height(5.dp).background(projectColor(project.projectColor))) {}
            }
        }
    }
}

@Composable
private fun TemplateChips(context: Context, favorites: List<WidgetFavorite>, width: Float, scale: Float) {
    val count = minOf(favorites.size, (width / (96 * scale)).toInt().coerceAtLeast(1))
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        favorites.take(count).forEachIndexed { index, favorite ->
            if (index > 0) Spacer(GlanceModifier.width(6.dp))
            val description = favorite.fields.description.ifBlank { context.getString(R.string.widget_no_description) }
            val project = favorite.project?.let { name -> favorite.task?.let { context.getString(R.string.widget_project_task, name, it) } ?: name }
            val label = if (project == null) context.getString(R.string.widget_start_favorite, description) else context.getString(R.string.widget_start_favorite_project, description, project)
            Row(GlanceModifier.defaultWeight().height(48.dp).background(ImageProvider(R.drawable.widget_chip_background), colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant))
                .clickable(startTimer(favorite.fields)).padding(horizontal = 8.dp).semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_play), null, GlanceModifier.size(12.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
                Spacer(GlanceModifier.width(4.dp))
                Label(description, 12f)
            }
        }
    }
}

private fun projectName(context: Context, project: WidgetDayProject, other: Boolean): String =
    if (other) context.getString(R.string.widget_other) else project.project ?: context.getString(R.string.widget_no_project)

@Composable
private fun projectColor(color: String?): ColorProvider = color?.let {
    runCatching { ColorProvider(Color(android.graphics.Color.parseColor(it))) }.getOrNull()
} ?: GlanceTheme.colors.outline

private fun projectColor(state: WidgetState.Ready): String? = state.running?.projectColor ?: state.paused?.projectColor
private fun projectText(context: Context, state: WidgetState.Ready): String? {
    val project = state.running?.project ?: state.paused?.project ?: return null
    val task = state.running?.task ?: state.paused?.task
    return if (task == null) project else context.getString(R.string.widget_project_task, project, task)
}
private fun description(context: Context, state: WidgetState.Ready): String =
    (state.running?.description ?: state.paused?.description)?.ifBlank { context.getString(R.string.widget_no_description) } ?: context.getString(R.string.widget_idle)

@Composable
private fun Label(text: String, size: Float, muted: Boolean = false, color: ColorProvider = if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface) {
    Text(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}
