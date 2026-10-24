package com.github.e0ipso.tokkl.pomodoro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication

/** Keeps the draft and pending disk writes across rotation, until the account signs out. */
class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    val model = (application as TokklApplication).let { app ->
        PomodoroScreenModel(
            store = app.pomodoroSessions,
            defaults = app.pomodoroDefaults.defaults,
            settings = app.accountSettings.state,
            unsent = app.changes.unsentCount,
            refused = app.changes.held,
            scope = viewModelScope,
        )
    }
}
