package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.github.e0ipso.tokkl.feature.widget.R
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** Cached Timer and Today report. The launcher ticks both clocks; redraws follow existing events. */
class TokklWidget :
    GlanceAppWidget(),
    KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = get<WidgetStateSource>().state
        val initial = states.first()
        provideContent {
            // Glance has no lifecycle owner, so collectAsStateWithLifecycle cannot run here.
            val state by states.collectAsState(initial)
            GlanceTheme(colors = WidgetColors) { TimerWidgetContent(state) }
        }
    }
}

class TokklWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TokklWidget()
}

private val WidgetColors = ColorProviders(light = TokklLightColorScheme, dark = TokklDarkColorScheme)
internal val ChartBand = ColorProvider(TokklLightExtraColors.chartBand, TokklDarkExtraColors.chartBand)
internal val RunningColor = ColorProvider(TokklLightExtraColors.running, TokklDarkExtraColors.running)
internal val PrimaryFill = ColorProvider(TokklLightExtraColors.primaryFill, TokklDarkExtraColors.primaryFill)
internal val OnPrimaryFill = ColorProvider(TokklLightExtraColors.onPrimaryFill, TokklDarkExtraColors.onPrimaryFill)
private val BillableColor = ColorProvider(TokklLightExtraColors.billable, TokklDarkExtraColors.billable)

/** Also used by native-host screenshot tests, with fixed cached state. */
@Composable
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
fun TimerWidgetContent(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val size = LocalSize.current
    val kind = timerWidgetSize(size.width.value, size.height.value)
    val padding = if (size.height.value < 64 || size.width.value < 150) 2f else WidgetLayout.PADDING_DP
    Column(
        modifier = modifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.surface).cornerRadius(16.dp).padding(padding.dp)
            .clickable(actionStartActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TIMER))),
        verticalAlignment = if (kind == TimerWidgetSize.STRIP) Alignment.CenterVertically else Alignment.Top,
    ) {
        when (state) {
            WidgetState.NoAccount -> Box(
                GlanceModifier.fillMaxSize().clickable(actionStartActivity(mainActivityIntent(context)))
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
        Column(
            GlanceModifier.defaultWeight().semantics {
                contentDescription = listOfNotNull(
                    status,
                    project,
                ).joinToString(", ")
            },
        ) {
            if (details) {
                if (state.running != null || state.paused != null) {
                    ProjectLine(project ?: context.getString(R.string.widget_no_project), projectColor(state), 11f)
                }
                Label(
                    if (state.paused != null) {
                        context.getString(
                            R.string.widget_paused_entry,
                            description,
                        )
                    } else {
                        description
                    },
                    14f,
                )
                if (state.running == null && state.paused == null) {
                    Label(
                        context.getString(R.string.widget_today),
                        10f,
                        muted = true,
                    )
                }
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
    // Android 12 scales sp linearly; newer Android versions enlarge big text less.
    // Reserve the native font's actual height rather than assuming the newer curve.
    val paint = clockPaint(context, if (large) 34f else 22f)
    val clockHeight = maxOf(
        WidgetLayout.clockRowHeight(large, scale),
        (paint.fontMetrics.bottom - paint.fontMetrics.top) / context.resources.displayMetrics.density + 4f,
    )
    val todayPaint = clockPaint(context, 18f)
    val todayHeight = maxOf(
        WidgetLayout.todayRowHeight(scale),
        (todayPaint.fontMetrics.bottom - todayPaint.fontMetrics.top) / context.resources.displayMetrics.density + 4f,
    )
    Row(
        GlanceModifier.fillMaxWidth().height(48.dp).semantics {
            contentDescription = listOfNotNull(
                description(context, state),
                projectText(context, state),
                state.paused?.let { context.getString(R.string.widget_paused_label) },
            ).joinToString(", ")
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Label(
                if (state.paused != null) {
                    context.getString(
                        R.string.widget_paused_entry,
                        description(context, state),
                    )
                } else {
                    description(context, state)
                },
                if (large) 17f else 16f,
            )
            if (large && scale < 1.5f && active) {
                ProjectLine(
                    projectText(context, state) ?: context.getString(R.string.widget_no_project),
                    projectColor(state),
                    12f,
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        if (state.running?.pausable == true) {
            Control(
                context.getString(R.string.widget_pause_description),
                UiR.drawable.ic_pause,
                pauseTimer(state.running.target),
                48f,
                GlanceTheme.colors.surfaceVariant,
                GlanceTheme.colors.onSurface,
            )
            Spacer(GlanceModifier.width(8.dp))
        } else if (state.paused != null) {
            Control(
                context.getString(R.string.widget_stop_description),
                UiR.drawable.ic_stop,
                stopTimer(state.paused.target),
                48f,
                GlanceTheme.colors.surfaceVariant,
                GlanceTheme.colors.onSurface,
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        PrimaryControl(context, state, 48f)
    }
    if (active) {
        Row(GlanceModifier.fillMaxWidth().height(clockHeight.dp), verticalAlignment = Alignment.CenterVertically) {
            TimerClock(context, state, if (large) 34f else 22f, if (large) width else width * .55f)
            if (!large) {
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    GlanceModifier.defaultWeight(),
                ) {
                    ProjectLine(
                        projectText(context, state) ?: context.getString(R.string.widget_no_project),
                        projectColor(state),
                        12f,
                    )
                }
            }
        }
    }
    Spacer(GlanceModifier.height(4.dp))
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline)) {}
    Spacer(GlanceModifier.height(5.dp))
    val day = state.day
    val reportAction = actionStartActivity(WidgetNavigation.intent(context, WidgetNavigation.OPEN_TODAY))
    val templates = large && state.favorites.isNotEmpty()
    val count = timerWidgetProjectRows(
        LocalSize.current.height.value,
        kind,
        scale,
        active,
        templates,
        clockHeight,
        todayHeight,
    )
    val segments = timerWidgetSegments(day?.projects.orEmpty())
    Column(GlanceModifier.fillMaxWidth().clickable(reportAction)) {
        if (large) {
            Row(
                GlanceModifier.fillMaxWidth().height(
                    WidgetLayout.reportHeadingHeight(scale).dp,
                ).semantics { contentDescription = context.getString(R.string.widget_open_report_description) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(context.getString(R.string.widget_today), 11f, muted = true)
                if (scale < 1.5f) {
                    Spacer(GlanceModifier.width(8.dp))
                    Label(
                        day?.date?.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).orEmpty(),
                        11f,
                        muted = true,
                    )
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
                    if (day?.running?.billable == true) {
                        Elapsed(
                            context,
                            day.running.start,
                            day.stoppedBillableSeconds,
                            16f,
                            width / 3 - 6,
                            context.getString(R.string.widget_billable),
                        )
                    } else {
                        StaticDuration(
                            context,
                            day?.formattedBillable(Instant.now()) ?: formatDuration(0),
                            16f,
                            width / 3 - 6,
                        )
                    }
                }
                Column(GlanceModifier.defaultWeight()) {
                    Label(context.getString(R.string.widget_entries), 10f, muted = true)
                    StaticDuration(context, (day?.entryCount ?: 0).toString(), 16f, width / 3 - 6)
                }
            }
        } else {
            Row(
                GlanceModifier.fillMaxWidth().height(
                    todayHeight.dp,
                ).semantics { contentDescription = context.getString(R.string.widget_open_report_description) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
        if (billableRow) {
            Label(
                context.getString(
                    R.string.widget_billable_total,
                    day?.formattedBillable(Instant.now()) ?: formatDuration(0),
                ),
                12f,
                muted = true,
            )
        }
    }
    if (templates) {
        Spacer(GlanceModifier.defaultWeight())
        TemplateChips(context, state.favorites, width, scale)
    }
}
