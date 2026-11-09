package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import com.github.e0ipso.tokkl.feature.widget.R
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.navigation.PageTap
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Instant
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** Server-owned session controls. Only the launcher's Chronometer ticks between state changes. */
class PomodoroWidget :
    GlanceAppWidget(),
    KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = get<PomodoroWidgetStateSource>().state
        val initial = states.first()
        provideContent {
            // Glance has no lifecycle owner, so collectAsStateWithLifecycle cannot run here.
            val state by states.collectAsState(initial)
            GlanceTheme(colors = PomodoroColors) { PomodoroWidgetContent(state) }
        }
    }
}

class PomodoroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PomodoroWidget()
}

/** The Pomodoro widget's colours: Tokkl's light and dark schemes, never the wallpaper's. */
val PomodoroColors = ColorProviders(light = TokklLightColorScheme, dark = TokklDarkColorScheme)

/** The same content is rendered in a native RemoteViews host for deterministic layout references. */
@Composable
fun PomodoroWidgetContent(
    state: PomodoroWidgetState,
    modifier: GlanceModifier = GlanceModifier,
    now: Instant = Instant.now(),
    ticking: Boolean = true,
) {
    val context = LocalContext.current
    val bounds = LocalSize.current
    val large = bounds.width.value >= WidgetLayout.LARGE_WIDTH_DP && bounds.height.value >= WidgetLayout.LARGE_HEIGHT_DP
    val card =
        bounds.width.value >= WidgetLayout.MEDIUM_WIDTH_DP && bounds.height.value >= WidgetLayout.MEDIUM_HEIGHT_DP
    val tiny = bounds.height.value < 80 || bounds.width.value < 180
    val padding = if (tiny) {
        2f
    } else if (large) {
        16f
    } else {
        12f
    }
    val width = bounds.width.value - padding * 2
    val height = bounds.height.value - padding * 2
    Column(
        modifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp).clickable(openPomodoro(context)).padding(padding.dp),
    ) {
        when (state) {
            PomodoroWidgetState.NoAccount -> Message(context.getString(R.string.widget_sign_in), tiny)

            PomodoroWidgetState.Unsupported -> Message(context.getString(R.string.pomodoro_widget_unsupported), tiny)

            is PomodoroWidgetState.Idle -> if (card) {
                IdleCard(
                    context,
                    state,
                    width,
                    height,
                    large,
                )
            } else {
                IdleStrip(context, state, tiny)
            }

            is PomodoroWidgetState.Active -> if (card) {
                SessionCard(
                    context,
                    state,
                    width,
                    height,
                    large,
                    now,
                    ticking,
                )
            } else {
                SessionStrip(context, state, width, tiny, now, ticking)
            }
        }
    }
}

internal fun openPomodoro(context: Context): Action =
    actionStartActivity(PageTap.POMODORO_WIDGET.into(mainActivityIntent(context)))

@Composable
private fun Message(message: String, tiny: Boolean) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PomodoroLabel(
            message,
            if (tiny) {
                6f
            } else if (LocalContext.current.resources.configuration.fontScale > 1.5f &&
                LocalSize.current.height.value < 160f
            ) {
                9f
            } else {
                14f
            },
            lines = if (tiny) 2 else 4,
            description = message,
        )
    }
}

@Composable
private fun IdleStrip(context: Context, state: PomodoroWidgetState.Idle, tiny: Boolean) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            PomodoroLabel(
                context.getString(R.string.pomodoro_widget_idle),
                if (tiny) {
                    8f
                } else if (context.resources.configuration.fontScale > 1.5f) {
                    12f
                } else {
                    14f
                },
            )
            if (!tiny) {
                PomodoroLabel(
                    defaultsLine(context, state),
                    12f,
                    muted = true,
                    description = defaultsDescription(context, state),
                )
            }
        }
        PomodoroIcon(
            context.getString(UiR.string.pomodoro_start),
            UiR.drawable.ic_play,
            startPomodoroWidget(state),
            if (tiny) 36f else 48f,
        )
    }
}

@Composable
private fun SessionStrip(
    context: Context,
    state: PomodoroWidgetState.Active,
    width: Float,
    tiny: Boolean,
    now: Instant,
    ticking: Boolean,
) {
    val button = if (tiny) 36f else 48f
    val scale = context.resources.configuration.fontScale
    val phase = phaseLabel(context, state)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            val heading = if (state.session.phase.kind == PhaseKind.WORK && !tiny) {
                context.getString(
                    R.string.pomodoro_widget_phase_number,
                    phase,
                    state.session.number,
                    state.session.longRestEvery,
                )
            } else {
                phase
            }
            PomodoroLabel(
                heading,
                if (tiny) {
                    6f
                } else if (scale > 1.5f && state.ended) {
                    8f
                } else if (scale > 1.5f) {
                    11f
                } else {
                    14f
                },
                color = phaseColor(state),
                description = "$phase, ${number(context, state)}",
            )
            PomodoroClock(context, state, width - button - 6f, if (tiny) 9f else 26f, now, ticking)
            if (!tiny) PhaseBand(context, state)
        }
        Spacer(GlanceModifier.width(6.dp))
        PomodoroIcon(
            nextLabel(context, state),
            R.drawable.ic_widget_next,
            phasePomodoroWidget(state),
            button,
            if (state.ended) RunningColor else PrimaryFill,
        )
    }
}
