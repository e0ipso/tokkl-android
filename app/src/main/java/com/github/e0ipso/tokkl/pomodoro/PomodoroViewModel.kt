package com.github.e0ipso.tokkl.pomodoro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication

/** Keeps the start form's draft and the typed fields across rotation, until the account signs out. */
class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    val model = (application as TokklApplication).let { app ->
        PomodoroScreenModel(
            held = app.heldTimerState,
            controls = app.timerControls,
            actions = app.timerActions,
            changes = app.changes,
            defaults = app.pomodoroDefaults.defaults,
            settings = app.accountSettings.state,
            unsent = app.changes.unsentCount,
            refused = app.changes.held,
            scope = viewModelScope,
            sendQueued = { app.sender.sendAndWait() },
            alarmProbe = { app.pomodoroAlarms.permissionDenied },
        )
    }
}
