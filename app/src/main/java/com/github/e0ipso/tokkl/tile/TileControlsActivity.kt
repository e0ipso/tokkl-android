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
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklDangerButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile's long press (docs/PRD.md N4, T1). SystemUI starts it with
 * [TileService.ACTION_QS_TILE_PREFERENCES]. It reads [com.github.e0ipso.tokkl.cache.ChangeQueue.timerControls]
 * and offers Pause and Stop while a timer runs, or Resume and Stop while one is paused. Each
 * button keeps the [com.github.e0ipso.tokkl.cache.TimerTarget] it was drawn with. The intent's
 * extras are ignored, so the caller cannot name a timer. With nothing running or paused, it
 * closes. The window is translucent and Compose draws the dialog, so the shade stays behind it.
 */
class TileControlsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTilePreferencesIntent(intent?.action)) {
            finish()
            return
        }
        val app = applicationContext as TokklApplication
        setContent {
            val controls: TimerControls? by app.changes.timerControls.collectAsState(initial = null as TimerControls?)
            TokklTheme {
                val shown = controls
                val panel = shown?.let(::tileControlsPanel)
                if (shown != null && panel == null) {
                    LaunchedEffect(Unit) { finish() }
                }
                if (panel != null) {
                    TileControlsDialog(
                        panel = panel,
                        onPress = { control -> runControl(app, control) },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    /**
     * The change goes to the queue in a scope of its own, so dismissing the dialog does not
     * cancel it. The queue writes it on the phone at once and does not wait for the network.
     */
    private fun runControl(app: TokklApplication, control: TileControl) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.changes.useTileControl(control)
            } catch (e: SQLiteException) {
                // The disk refused the change. The dialog still shows the timer it drew.
            }
        }
    }
}

/** True when SystemUI started this activity for the tile's long press. */
internal fun isTilePreferencesIntent(action: String?): Boolean =
    action == TileService.ACTION_QS_TILE_PREFERENCES

/**
 * Pause and Stop, or Resume and Stop, for [panel]. The buttons stack at full width, as in
 * [com.github.e0ipso.tokkl.catalog.ConfirmDialog], so they cannot overlap at any font scale.
 * Back and a tap outside dismiss. The words say the action, and so does TalkBack.
 */
@Composable
private fun TileControlsDialog(
    panel: TileControlsPanel,
    onPress: (TileControl) -> Unit,
    onDismiss: () -> Unit,
) {
    val description = panel.description.ifBlank { stringResource(R.string.tile_no_description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(description, modifier = Modifier.semantics { heading() }) },
        text = { Text(stringResource(if (panel.paused) R.string.timer_paused else R.string.timer_running)) },
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
private fun TileControlButton(action: TileControlAction, onClick: () -> Unit) {
    @StringRes val label: Int
    @StringRes val spoken: Int
    @DrawableRes val icon: Int
    when (action) {
        TileControlAction.Pause -> {
            label = R.string.timer_pause
            spoken = R.string.timer_pause_spoken
            icon = R.drawable.ic_pause
        }
        TileControlAction.Resume -> {
            label = R.string.timer_resume
            spoken = R.string.timer_resume_spoken
            icon = R.drawable.ic_play
        }
        TileControlAction.Stop -> {
            label = R.string.timer_stop
            spoken = R.string.timer_stop_spoken
            icon = R.drawable.ic_stop
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
        TileControlAction.Pause -> TokklGhostButton(onClick = onClick, modifier = modifier, content = content)
        TileControlAction.Resume -> TokklButton(onClick = onClick, modifier = modifier, content = content)
        TileControlAction.Stop -> TokklDangerButton(onClick = onClick, modifier = modifier, content = content)
    }
}
