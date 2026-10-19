package com.github.e0ipso.tokkl.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication

/** Keeps the timer's draft and the taps the cache does not show yet across configuration changes. */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        TimerModel(
            running = app.cache.running,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            accountSettings = app.accountSettings.state,
            changes = app.changes,
            scope = viewModelScope,
        )
    }
}
