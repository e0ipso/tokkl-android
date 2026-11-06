package com.github.e0ipso.tokkl.lifecycle

import android.content.Context
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.shortcuts.FavoriteShortcutSync
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.widget.WidgetSync
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What every process does when it starts, whatever starts it. Each component comes in as [Lazy]
 * and loads at the step that uses it, so the steps keep their order and their threads.
 */
internal class AppStartup(
    private val context: Context,
    private val servers: ServerConfigStore,
    private val trust: ServerTrustStore,
    private val refreshRequests: AccountRefreshRequests,
    private val accountRefresh: Lazy<AccountRefresh>,
    private val runningNotification: Lazy<RunningNotificationSync>,
    private val held: Lazy<HeldTimerFlows>,
    private val capabilities: Lazy<CapabilitiesSource>,
    private val pomodoroAlarm: Lazy<PomodoroPhaseAlarm>,
    private val pomodoroNotification: Lazy<AccountPomodoroNotification>,
    private val reminder: Lazy<ReminderSync>,
    private val reminderSettings: Lazy<ReminderSetting>,
    private val cache: Lazy<AccountCache>,
    private val widgetSync: Lazy<WidgetSync>,
    private val pomodoroWidgetSync: Lazy<WidgetSync>,
    private val favoriteShortcuts: Lazy<FavoriteShortcutSync>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    fun run() {
        // Every process, whatever starts it, sends requests only to the stored server.
        ConfiguredServer.readStoredFrom(servers::load)
        // A plain http:// server gets requests only once the user has confirmed it.
        ConfiguredTrust.readFrom(trust)
        // The refreshes that the refresh's own components ask for, from any process.
        scope.launch(io) { refreshRequests.forward(accountRefresh) }
        // Reads the cache off the main thread, and posts or removes nothing for the entry the
        // notification already followed, so opening the app never brings back a dismissed one.
        runningNotification.value.start()
        // Arms, moves and drops the phase alarm and its ring on every held state and capability
        // change, from the stored state at process start on, also with the app in the background.
        scope.launch(io) {
            combine(held.value.held, flow { emitAll(capabilities.value.state) }) { _, _ -> Unit }
                .collect { pomodoroAlarm.value.follow() }
        }
        pomodoroNotification.value.current.start()
        reminder.value.follow(
            combine(cache.value.running, flow { emitAll(reminderSettings.value.state) }) { running, preferences -> running to preferences },
            scope, io,
        )
        widgetSync.value.start()
        pomodoroWidgetSync.value.start()
        favoriteShortcuts.value.start()
        // Check every 15 minutes while the cache holds a running entry.
        RunningCheck.follow(context, cache.value.running.map { it != null }, scope, io)
    }
}
