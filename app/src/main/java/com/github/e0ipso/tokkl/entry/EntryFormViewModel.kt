package com.github.e0ipso.tokkl.entry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication

/** Keeps the entry form and what it holds across configuration changes. */
class EntryFormViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        EntryFormModel(
            entries = app.cache.entries,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            accountSettings = app.accountSettings.state,
            changes = app.changes,
            scope = viewModelScope,
        )
    }
}
