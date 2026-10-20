package com.github.e0ipso.tokkl.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/** Keeps the chosen day and the weeks loaded from the server across configuration changes, so turning the phone loads nothing again. */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        CalendarModel(
            entries = app.cache.entries,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            accountSettings = app.accountSettings.state,
            changes = app.changes,
            client = Session(application)::client,
            scope = viewModelScope,
            io = Dispatchers.IO,
        )
    }
}
