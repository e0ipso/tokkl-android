package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Composable
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.sync.SyncStatusBand
import java.time.Clock
import com.github.e0ipso.tokkl.feature.pomodoro.R as PomodoroR
import com.github.e0ipso.tokkl.feature.timer.R as TimerR

/**
 * The running dots on the bar's Timer and Pomodoro items, with the labels TalkBack reads, as the
 * Timer and Pomodoro features' badges give them to the signed-in frame.
 */
internal fun runningBadges(timer: Boolean = false, pomodoro: Boolean = false): Map<Route, Int> = buildMap {
    if (timer) put(Route.Timer, TimerR.string.nav_timer_running)
    if (pomodoro) put(PomodoroRoute, PomodoroR.string.nav_pomodoro_running)
}

/** The sync status band for [status] at [clock], as the sync feature shows it above the bar. */
internal fun syncBand(
    status: SyncStatus,
    clock: Clock = Clock.systemUTC(),
    onOpenNeedsAttention: (() -> Unit)? = null,
): @Composable () -> Unit = { SyncStatusBand(status, onOpenNeedsAttention, clock = clock) }
