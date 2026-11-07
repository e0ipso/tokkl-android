package com.github.e0ipso.tokkl.tile

import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.github.e0ipso.tokkl.feature.tile.R
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.timernotice.showTimerActionFailure
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklDangerButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The Quick Settings tile's long press (docs/PRD.md N4, T1). SystemUI starts it with
 * [TileService.ACTION_QS_TILE_PREFERENCES]. It reads [TimerControlsSource.controls] and offers
 * Stop while a timer runs, with Pause where the server's timer offers it, or Resume and Stop while
 * one is paused. Each button keeps the [com.github.e0ipso.tokkl.timerstate.TimerTarget] it was drawn
 * with. The intent's extras are ignored, so the caller cannot name a timer. With nothing running
 * or paused, it closes. The window is translucent and Compose draws the dialog, so the shade stays
 * behind it.
 */
class TileControlsActivity : ComponentActivity() {

    private val timerControls: TimerControlsSource by inject()
    private val surfaceActions: TimerSurfaceActions by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTilePreferencesIntent(intent?.action)) {
            finish()
            return
        }
        setContent {
            // Collected while stopped too, so a press acts on the timer the dialog shows from the first frame back.
            val controls: TimerControls? by timerControls.controls.collectAsState(initial = null as TimerControls?)
            TokklTheme {
                val shown = controls
                val panel = shown?.let(::tileControlsPanel)
                if (shown != null && panel == null) {
                    LaunchedEffect(Unit) { finish() }
                }
                if (panel != null) {
                    TileControlsDialog(
                        panel = panel,
                        onPress = { control -> runControl(control) },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    /**
     * The control goes through [TimerSurfaceActions] in the application scope, so
     * dismissing the dialog does not cancel it. A Stop of a running entry is written on the phone
     * at once and does not wait for the network. Pause, Resume and Stop while paused go to the
     * server, and one that fails, offline included, says why in a toast while the dialog keeps
     * the timer it drew.
     */
    private fun runControl(control: TileControl) {
        val app = applicationContext
        scope.launch(io) {
            try {
                val tap = surfaceActions.tap(control.action, control.target)
                if (tap is SurfaceTap.Request) tap.send()?.let { showTimerActionFailure(app, it) }
            } catch (_: SQLiteException) {
                // The disk refused the change. The dialog still shows the timer it drew.
            }
        }
    }
}

/** True when SystemUI started this activity for the tile's long press. */
internal fun isTilePreferencesIntent(action: String?): Boolean = action == TileService.ACTION_QS_TILE_PREFERENCES

/**
 * Pause, where offered, and Stop, or Resume and Stop, for [panel]. The buttons stack at full
 * width, as in [com.github.e0ipso.tokkl.catalog.ConfirmDialog], so they cannot overlap at any font
 * scale. Back and a tap outside dismiss. The words say the action, and so does TalkBack.
 */
@Composable
private fun TileControlsDialog(panel: TileControlsPanel, onPress: (TileControl) -> Unit, onDismiss: () -> Unit) {
    val description = panel.description.ifBlank { stringResource(R.string.tile_no_description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(description, modifier = Modifier.semantics { heading() }) },
        text = {
            Text(
                stringResource(if (panel.paused) SharedR.string.timer_paused else SharedR.string.timer_running),
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
            ) {
                for (control in panel.buttons) {
                    TileControlButton(control.action) { onPress(control) }
                }
            }
        },
    )
}

/** One control: its icon and word, and the whole action for TalkBack. */
@Composable
private fun TileControlButton(action: TimerSurfaceAction, onClick: () -> Unit) {
    @StringRes val label: Int

    @StringRes val spoken: Int

    @DrawableRes val icon: Int
    when (action) {
        TimerSurfaceAction.Pause -> {
            label = SharedR.string.timer_pause
            spoken = SharedR.string.timer_pause_spoken
            icon = UiR.drawable.ic_pause
        }

        TimerSurfaceAction.Resume -> {
            label = SharedR.string.timer_resume
            spoken = SharedR.string.timer_resume_spoken
            icon = UiR.drawable.ic_play
        }

        TimerSurfaceAction.Stop -> {
            label = SharedR.string.timer_stop
            spoken = SharedR.string.timer_stop_spoken
            icon = UiR.drawable.ic_stop
        }
    }
    val description = stringResource(spoken)
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(TokklTheme.spacing.space5))
        Text(
            stringResource(label),
            modifier = Modifier.padding(start = TokklTheme.spacing.space2),
            textAlign = TextAlign.Center,
        )
    }
    val modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }
    when (action) {
        TimerSurfaceAction.Pause -> TokklGhostButton(onClick = onClick, modifier = modifier, content = content)
        TimerSurfaceAction.Resume -> TokklButton(onClick = onClick, modifier = modifier, content = content)
        TimerSurfaceAction.Stop -> TokklDangerButton(onClick = onClick, modifier = modifier, content = content)
    }
}
