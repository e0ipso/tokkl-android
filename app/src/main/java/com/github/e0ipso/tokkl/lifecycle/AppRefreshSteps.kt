package com.github.e0ipso.tokkl.lifecycle

import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.cache.ServerCacheRefresh
import com.github.e0ipso.tokkl.capabilities.CapabilitiesRefresh
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.session.AccountRefreshSteps
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.TimerStateIntakes
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower

/**
 * What each account refresh does after `GET /api/v9/me` answered, in this order, with the same
 * answers: the capabilities refresh and feed the account's timer state what their probe read, and
 * the cache refreshes with those capabilities. Then the Timer view, the Pomodoro defaults and the
 * reminder take their preferences from `GET /api/v9/me/preferences`, [sendQueue] sends the queue,
 * since the server has just answered, and the [timerSync] follows the capabilities, since any
 * refresh can learn or lose the timer API. The app holds it because it spans the core modules and
 * the Timer feature.
 */
internal class AppRefreshSteps(
    private val capabilities: CapabilitiesRefresh,
    private val timerStates: TimerStateIntakes,
    private val cache: ServerCacheRefresh,
    private val timerView: TimerViewSetting,
    private val pomodoroDefaults: PomodoroDefaultsSetting,
    private val reminder: ReminderSetting,
    private val sendQueue: () -> Unit,
    private val timerSync: TimerSyncFollower,
) : AccountRefreshSteps {

    override suspend fun afterAnswer(api: TokklApi, me: Me, preferences: MePreferences?) {
        val flags = capabilities.refresh(api, me, timerStates.intake())
        cache.refresh(api, me, preferences, flags)
        timerView.refreshed(preferences, keepsTimerView = flags.favorites)
        pomodoroDefaults.refreshed(preferences, keepsPreferences = flags.favorites)
        reminder.refreshed(preferences, tokkl = flags.favorites)
        sendQueue()
        timerSync.follow()
    }
}
