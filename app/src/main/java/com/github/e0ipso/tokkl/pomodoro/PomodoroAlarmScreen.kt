package com.github.e0ipso.tokkl.pomodoro

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.Clock
import java.util.Locale
import kotlinx.coroutines.delay

/** Exactly two actions; scrolling keeps both reachable at the largest system font scale. */
@Composable
internal fun PomodoroAlarmContent(
    state: PomodoroAlarmState,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    clock: Clock = Clock.systemUTC(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val session = state.session
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .screenContent().padding(vertical = TokklTheme.spacing.betweenGroups),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
        ) {
            Text(stringResource(R.string.pomodoro_alarm_title), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            if (state.failed) ErrorBanner(stringResource(R.string.pomodoro_fullscreen_save_failed))
            if (session != null) {
                Text(stringResource(alarmTransitionLabel(session.ring().sound)), style = MaterialTheme.typography.titleLarge)
                var now by remember(clock, session.phaseKey()) { mutableStateOf(clock.instant()) }
                LaunchedEffect(clock, session.phaseKey(), lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            now = clock.instant()
                            delay(1000L - Math.floorMod(now.toEpochMilli(), 1000L))
                        }
                    }
                }
                val reading = session.clockAt(now)
                val duration = String.format(Locale.ROOT, "%d:%02d", reading.seconds / 60, reading.seconds % 60)
                val spoken = stringResource(
                    if (reading.overtime) R.string.pomodoro_overtime_clock else R.string.pomodoro_remaining_clock,
                    duration,
                )
                Text(stringResource(if (reading.overtime) R.string.pomodoro_overtime else R.string.pomodoro_remaining))
                Text(duration, style = TokklTheme.clockTextStyle,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken })
            }
            TokklButton(onClick = onNext, enabled = session != null && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.pomodoro_alarm_next))
            }
            TokklGhostButton(onClick = onEnd, enabled = session != null && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.pomodoro_alarm_end))
            }
        }
    }
}

@StringRes
internal fun alarmTransitionLabel(sound: PomodoroSound): Int = when (sound) {
    PomodoroSound.WORK_TO_SHORT_REST -> R.string.pomodoro_fullscreen_work_short
    PomodoroSound.WORK_TO_LONG_REST -> R.string.pomodoro_fullscreen_work_long
    PomodoroSound.SHORT_REST_TO_WORK -> R.string.pomodoro_fullscreen_short_work
    PomodoroSound.LONG_REST_TO_WORK -> R.string.pomodoro_fullscreen_long_work
}
