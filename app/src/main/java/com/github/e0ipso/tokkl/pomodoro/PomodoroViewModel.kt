package com.github.e0ipso.tokkl.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.TimerControlsSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.TimerActions

/** Keeps the start form's draft and the typed fields across rotation, until the account signs out. */
class PomodoroViewModel(
    held: HeldTimerFlows,
    controls: TimerControlsSource,
    actions: TimerActions,
    changes: ChangeQueue,
    defaults: PomodoroDefaultsSetting,
    accountSettings: AccountSettingsSource,
    sender: ChangeSender,
    alarms: PomodoroAlarmScheduler,
) : ViewModel() {
    val model = PomodoroScreenModel(
        held = held.held,
        controls = controls.controls,
        actions = actions,
        changes = changes,
        defaults = defaults.defaults,
        settings = accountSettings.state,
        unsent = changes.unsentCount,
        refused = changes.held,
        scope = viewModelScope,
        sendQueued = { sender.sendAndWait() },
        alarmProbe = { alarms.permissionDenied },
    )
}
