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
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.formatDurationWithoutSeconds
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import java.time.Instant
import com.github.e0ipso.tokkl.core.ui.R as UiR

@Composable
internal fun PrimaryControl(context: Context, state: WidgetState.Ready, size: Float) {
    when {
        state.running != null -> Control(
            context.getString(R.string.widget_stop_description),
            UiR.drawable.ic_stop,
            stopTimer(state.running.target),
            size,
            GlanceTheme.colors.error,
            GlanceTheme.colors.onError,
        )

        state.paused != null -> Control(
            context.getString(R.string.widget_resume_description),
            UiR.drawable.ic_play,
            resumeTimer(state.paused.target),
            size,
            PrimaryFill,
            OnPrimaryFill,
        )

        else -> Control(
            context.getString(R.string.widget_start_description),
            UiR.drawable.ic_play,
            startTimer(state.account),
            size,
            PrimaryFill,
            OnPrimaryFill,
        )
    }
}

@Composable
internal fun Control(
    label: String,
    icon: Int,
    action: Action,
    size: Float,
    background: ColorProvider,
    foreground: ColorProvider,
) {
    SquareIconButton(
        ImageProvider(icon),
        label,
        action,
        GlanceModifier.size(size.dp),
        backgroundColor = background,
        contentColor = foreground,
    )
}

@Composable
internal fun TimerClock(context: Context, state: WidgetState.Ready, size: Float, width: Float) {
    val running = state.running
    if (running != null) {
        Elapsed(
            context,
            running.start,
            running.accumulatedSeconds,
            size,
            width,
            context.getString(R.string.widget_elapsed),
        )
    } else {
        val text = if (state.paused != null) {
            formatDuration(state.paused.seconds, state.day?.durationFormat ?: DurationFormat.IMPROVED)
        } else {
            state.day?.formattedTotal(Instant.now()) ?: formatDuration(0)
        }
        StaticDuration(context, text, size, width)
    }
}

@Composable
internal fun DayClock(context: Context, day: WidgetDayReport?, size: Float, width: Float) {
    if (day?.running != null) {
        Elapsed(
            context,
            day.running.start,
            day.stoppedSeconds,
            size,
            width,
            context.getString(R.string.widget_today_total),
        )
    } else {
        StaticDuration(context, day?.formattedTotal(Instant.now()) ?: formatDuration(0), size, width)
    }
}

/** Measure at the effective system font scale, then give the RemoteViews child its exact width. */
internal fun clockPaint(context: Context, size: Float): Paint = Paint().apply {
    typeface = context.resources.getFont(UiR.font.anybody_wdth_wght)
    fontVariationSettings = "'wght' 600, 'wdth' 85"
    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, context.resources.displayMetrics)
    fontFeatureSettings = "tnum"
}

private fun fittedClockSize(context: Context, sample: String, requested: Float, width: Float): Float {
    val density = context.resources.displayMetrics.density
    var size = requested
    while (size > MIN_CLOCK_SIZE && clockPaint(context, size).measureText(sample) + CLOCK_SLACK_PX > width * density) {
        size -= CLOCK_SIZE_STEP
    }
    return size
}

@Composable
internal fun Elapsed(
    context: Context,
    start: Instant,
    accumulated: Long,
    requested: Float,
    maxWidth: Float,
    label: String,
) {
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
        setChronometer(
            R.id.widget_chronometer,
            chronometerBase(start, now, SystemClock.elapsedRealtime(), accumulated),
            null,
            true,
        )
        setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, size)
        setContentDescription(R.id.widget_chronometer, label)
        setColorInt(
            R.id.widget_chronometer,
            "setTextColor",
            TokklLightColorScheme.onSurface.toArgb(),
            TokklDarkColorScheme.onSurface.toArgb(),
        )
    }
    AndroidRemoteViews(
        views,
        modifier = GlanceModifier.width(width.dp).height(height.dp).semantics { contentDescription = label },
    )
}

@Composable
internal fun StaticDuration(
    context: Context,
    text: String,
    requested: Float,
    width: Float,
    color: ColorProvider = GlanceTheme.colors.onSurface,
) {
    val maxWidth = width.coerceAtLeast(1f)
    val size = fittedClockSize(context, text, requested, maxWidth)
    val paint = clockPaint(context, size)
    val density = context.resources.displayMetrics.density
    val views = RemoteViews(context.packageName, R.layout.widget_duration).apply {
        setTextViewText(R.id.widget_duration, text)
        setTextViewTextSize(R.id.widget_duration, TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(R.id.widget_duration, color.getColor(context).toArgb())
    }
    AndroidRemoteViews(
        views,
        modifier = GlanceModifier.width(minOf(maxWidth, (paint.measureText(text) + 4) / density).dp)
            .height(((paint.fontMetrics.bottom - paint.fontMetrics.top) / density + 4).dp),
    )
}

@Composable
internal fun ProjectLine(text: String, color: String?, size: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(projectColor(color))
        Spacer(GlanceModifier.width(5.dp))
        Label(text, size, muted = true)
    }
}

@Composable
internal fun Dot(color: ColorProvider) {
    Image(ImageProvider(R.drawable.widget_dot), null, GlanceModifier.size(8.dp), colorFilter = ColorFilter.tint(color))
}

@Composable
internal fun StackedBar(context: Context, projects: List<WidgetDayProject>, day: WidgetDayReport?, width: Float) {
    val total = projects.sumOf { it.stoppedSeconds }.coerceAtLeast(1)
    val label = projects.mapIndexed { index, project ->
        val name = projectName(context, project, index == 4 && day?.projects.orEmpty().size > 5)
        val duration = project.formattedStopped.ifEmpty {
            formatDurationWithoutSeconds(project.stoppedSeconds, day?.durationFormat ?: DurationFormat.IMPROVED)
        }
        if (project.running) context.getString(R.string.widget_project_running, name, duration) else "$name, $duration"
    }.joinToString("; ")
    Row(
        GlanceModifier.fillMaxWidth().height(
            8.dp,
        ).cornerRadius(4.dp).background(ChartBand).semantics { contentDescription = label },
    ) {
        projects.forEach { project ->
            if (project.stoppedSeconds > 0) {
                Box(
                    GlanceModifier.width(
                        (width * project.stoppedSeconds / total).dp,
                    ).height(8.dp).background(projectColor(project.projectColor)),
                ) {}
            }
        }
    }
}

@Composable
internal fun ProjectRow(
    context: Context,
    project: WidgetDayProject,
    day: WidgetDayReport?,
    other: Boolean,
    large: Boolean,
    width: Float,
    scale: Float,
) {
    val name = projectName(context, project, other)
    val duration = project.formattedStopped.ifEmpty {
        formatDurationWithoutSeconds(project.stoppedSeconds, day?.durationFormat ?: DurationFormat.IMPROVED)
    }
    val label = if (project.running) {
        context.getString(R.string.widget_project_running, name, duration)
    } else {
        "$name, $duration"
    }
    Column(
        GlanceModifier.fillMaxWidth().height(
            ((if (large) 28f else 18f) * scale).dp,
        ).semantics { contentDescription = label },
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!large) {
                Dot(projectColor(project.projectColor))
                Spacer(GlanceModifier.width(5.dp))
            }
            Text(
                name,
                GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Label(duration, 12f, muted = true)
            if (project.running) {
                Spacer(GlanceModifier.width(4.dp))
                Dot(RunningColor)
            }
        }
        if (large) {
            val total = day?.stoppedSeconds?.coerceAtLeast(1) ?: 1
            Box(GlanceModifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp).background(ChartBand)) {
                if (project.stoppedSeconds > 0) {
                    Box(
                        GlanceModifier.width(
                            (width * project.stoppedSeconds / total).dp,
                        ).height(5.dp).background(projectColor(project.projectColor)),
                    ) {}
                }
            }
        }
    }
}

@Composable
internal fun TemplateChips(
    context: Context,
    account: String,
    favorites: List<WidgetFavorite>,
    width: Float,
    scale: Float,
) {
    val count = minOf(favorites.size, (width / (96 * scale)).toInt().coerceAtLeast(1))
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        favorites.take(count).forEachIndexed { index, favorite ->
            if (index > 0) Spacer(GlanceModifier.width(6.dp))
            val description = favorite.fields.description.ifBlank { context.getString(R.string.widget_no_description) }
            val project = favorite.project?.let { name ->
                favorite.task?.let {
                    context.getString(R.string.widget_project_task, name, it)
                } ?: name
            }
            val label = if (project == null) {
                context.getString(R.string.widget_start_favorite, description)
            } else {
                context.getString(R.string.widget_start_favorite_project, description, project)
            }
            Row(
                GlanceModifier.defaultWeight().height(48.dp).background(
                    ImageProvider(R.drawable.widget_chip_background),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
                )
                    .clickable(startTimer(account, favorite.fields)).padding(
                        horizontal = 8.dp,
                    ).semantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    ImageProvider(UiR.drawable.ic_play),
                    null,
                    GlanceModifier.size(12.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                )
                Spacer(GlanceModifier.width(4.dp))
                Label(description, 12f)
            }
        }
    }
}

private fun projectName(context: Context, project: WidgetDayProject, other: Boolean): String = if (other) {
    context.getString(R.string.widget_other)
} else {
    project.project ?: context.getString(R.string.widget_no_project)
}

@Composable
internal fun projectColor(color: String?): ColorProvider = color?.let {
    runCatching { ColorProvider(Color(android.graphics.Color.parseColor(it))) }.getOrNull()
} ?: GlanceTheme.colors.outline

internal fun projectColor(state: WidgetState.Ready): String? = state.running?.projectColor ?: state.paused?.projectColor
internal fun projectText(context: Context, state: WidgetState.Ready): String? {
    val project = state.running?.project ?: state.paused?.project ?: return null
    val task = state.running?.task ?: state.paused?.task
    return if (task == null) project else context.getString(R.string.widget_project_task, project, task)
}
internal fun description(context: Context, state: WidgetState.Ready): String =
    (state.running?.description ?: state.paused?.description)?.ifBlank {
        context.getString(R.string.widget_no_description)
    } ?: context.getString(R.string.widget_idle)

@Composable
internal fun Label(
    text: String,
    size: Float,
    muted: Boolean = false,
    color: ColorProvider = if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
) {
    Text(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

// fittedClockSize shrinks the clock's text in steps of CLOCK_SIZE_STEP down to MIN_CLOCK_SIZE, until
// it fits with CLOCK_SLACK_PX to spare.
private const val MIN_CLOCK_SIZE = 6f
private const val CLOCK_SIZE_STEP = .5f
private const val CLOCK_SLACK_PX = 4
