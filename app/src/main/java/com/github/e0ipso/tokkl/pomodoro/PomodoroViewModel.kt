package com.github.e0ipso.tokkl.pomodoro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Keeps the draft and pending disk writes across rotation, until the account signs out. */
class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    val model = (application as TokklApplication).let { app ->
        val sessions = app.pomodoroSessions
        PomodoroScreenModel(
            store = sessions,
            defaults = app.pomodoroDefaults.defaults,
            settings = app.accountSettings.state,
            unsent = app.changes.unsentCount,
            refused = app.changes.held,
            scope = viewModelScope,
            sessionChanges = merge(
                app.cacheDatabase.pomodoroSessions().running().map { Unit },
                sessions.notificationChanges.map { Unit },
            ),
        )
    }
}
