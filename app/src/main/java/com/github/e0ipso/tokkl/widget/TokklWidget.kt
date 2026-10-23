package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * The home screen widget (docs/PRD.md N3): the running entry's description, project and elapsed
 * time with Pause and Stop buttons, the paused timer's total with Resume and Stop (T1), or Start
 * when no timer runs, and below them as many favorites as fit. It reads only the phone, through
 * [TokklApplication.widgetState], and its taps change the phone and queue the change, so all of
 * it works offline.
 *
 * Nothing updates it on a timer: the provider info sets no update period, and the elapsed time is a
 * Chronometer that the launcher ticks. [WidgetSync] updates it when the phone's state changes,
 * and while Glance's session is open, the composition follows that state itself.
 *
 * Its colours are Tokkl's palette in light and dark, chosen by the launcher's night mode.
 */
class TokklWidget : GlanceAppWidget() {

    /** Each placed size gets its own layout, so the favorites fill the widget's height. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = (context.applicationContext as TokklApplication).widgetState
        val initial = states.first()
        provideContent {
            val state by states.collectAsState(initial)
            GlanceTheme(colors = WidgetColors) { WidgetContent(state) }
        }
    }
}

/** Declared in the manifest, where the launcher finds the widget. */
class TokklWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TokklWidget()
}

private val WidgetColors = ColorProviders(light = TokklLightColorScheme, dark = TokklDarkColorScheme)

@Composable
private fun WidgetContent(state: WidgetState) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(WidgetLayout.PADDING_DP.dp),
    ) {
        when (state) {
            WidgetState.NoAccount -> SignIn(context)
            is WidgetState.Ready -> {
                TimerRow(context, state.running, state.paused)
                val shown = favoritesThatFit(LocalSize.current.height.value, state.favorites.size, context.resources.configuration.fontScale)
                state.favorites.take(shown).forEach { FavoriteRow(context, it) }
            }
        }
    }
}

/** Without an account, the whole widget opens the app to sign in. */
@Composable
private fun SignIn(context: Context) {
    val label = context.getString(R.string.widget_sign_in)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
    }
}

/**
 * The running entry with Pause and Stop, the paused timer with Resume and Stop, or "No timer
 * running" with Start: text says the state, not colour. Pause, Resume and Stop are icon buttons,
 * with what TalkBack reads, so two of them leave the description room at the minimum size.
 */
@Composable
private fun TimerRow(context: Context, running: WidgetRunning?, paused: WidgetPaused?) {
    Column(modifier = GlanceModifier.fillMaxWidth().height(WidgetLayout.timerRowHeight(context.resources.configuration.fontScale).dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(WidgetLayout.buttonHeight(context.resources.configuration.fontScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val description = running?.description ?: paused?.description
            Text(
                if (description == null) context.getString(R.string.widget_idle) else description.ifBlank { context.getString(R.string.widget_no_description) },
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            val colors = GlanceTheme.colors
            when {
                running != null -> {
                    IconButton(context.getString(R.string.widget_pause_description), R.drawable.ic_pause, pauseTimer(running.target), colors.surfaceVariant, colors.onSurface)
                    Spacer(GlanceModifier.width(8.dp))
                    IconButton(context.getString(R.string.widget_stop_description), R.drawable.ic_stop, stopTimer(running.target), colors.error, colors.onError)
                }
                paused != null -> {
                    IconButton(context.getString(R.string.widget_resume_description), R.drawable.ic_play, resumeTimer(paused.target), colors.primary, colors.onPrimary)
                    Spacer(GlanceModifier.width(8.dp))
                    IconButton(context.getString(R.string.widget_stop_description), R.drawable.ic_stop, stopTimer(paused.target), colors.error, colors.onError)
                }
                else -> TimerButton(context.getString(R.string.widget_start), context.getString(R.string.widget_start_description), R.drawable.ic_play, startTimer())
            }
        }
        if (running != null || paused != null) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (running != null) {
                    Elapsed(context, running.start, running.accumulatedSeconds)
                } else if (paused != null) {
                    Text(
                        context.getString(R.string.widget_paused, formatDuration(paused.seconds)),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                projectText(context, running?.project ?: paused?.project, running?.task ?: paused?.task)?.let { project ->
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        project,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerButton(text: String, description: String, icon: Int, onClick: Action) {
    FilledButton(
        text = text,
        onClick = onClick,
        icon = ImageProvider(icon),
        modifier = GlanceModifier.height(WidgetLayout.buttonHeight(LocalContext.current.resources.configuration.fontScale).dp).semantics { contentDescription = description },
    )
}

/**
 * A square icon button as tall as [TimerButton], labelled [description], in the web timer's
 * colours: Resume on primary, Pause on the second surface, Stop on danger.
 */
@Composable
private fun IconButton(description: String, icon: Int, onClick: Action, background: ColorProvider, content: ColorProvider) {
    val size = WidgetLayout.buttonHeight(LocalContext.current.resources.configuration.fontScale).dp
    SquareIconButton(
        imageProvider = ImageProvider(icon),
        contentDescription = description,
        onClick = onClick,
        modifier = GlanceModifier.size(size),
        backgroundColor = background,
        contentColor = content,
    )
}

/**
 * The elapsed time as a Chronometer the launcher ticks from the entry's start, after the
 * [accumulatedSeconds] a pause chain stored before it, so the widget needs no update to count.
 * Glance has no chronometer of its own, so it comes in as RemoteViews.
 */
@Composable
private fun Elapsed(context: Context, start: Instant, accumulatedSeconds: Long) {
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(R.id.widget_chronometer, chronometerBase(start, Instant.now(), SystemClock.elapsedRealtime(), accumulatedSeconds), null, true)
        setColorInt(R.id.widget_chronometer, "setTextColor", TokklLightColorScheme.onSurface.toArgb(), TokklDarkColorScheme.onSurface.toArgb())
    }
    // A RemoteViews child of a Glance Row needs an explicit width. Reserve the wider hours
    // form even before the first hour, so native ticks cannot wrap or displace the project.
    val metrics = context.resources.displayMetrics
    val clockPaint = Paint().apply {
        typeface = context.resources.getFont(R.font.anybody_wdth_wght)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, metrics)
        fontFeatureSettings = "tnum"
    }
    val width = (clockPaint.measureText("0000:00:00") + 2) / metrics.density
    AndroidRemoteViews(views, modifier = GlanceModifier.width(width.dp))
}

/** A favorite, which a tap starts at once. */
@Composable
private fun FavoriteRow(context: Context, favorite: WidgetFavorite) {
    val description = favorite.fields.description.ifBlank { context.getString(R.string.widget_no_description) }
    val project = projectText(context, favorite.project, favorite.task)
    val label = if (project == null) {
        context.getString(R.string.widget_start_favorite, description)
    } else {
        context.getString(R.string.widget_start_favorite_project, description, project)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(WidgetLayout.favoriteRowHeight(context.resources.configuration.fontScale).dp)
            .cornerRadius(8.dp)
            .clickable(startTimer(favorite.fields))
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_play),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(description, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1)
            if (project != null) {
                Text(project, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}

private fun projectText(context: Context, project: String?, task: String?): String? {
    project ?: return null
    task ?: return project
    return context.getString(R.string.widget_project_task, project, task)
}
