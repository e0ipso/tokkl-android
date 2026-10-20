package com.github.e0ipso.tokkl.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/**
 * Keeps the history's older days across configuration changes, so turning the phone loads nothing
 * again, and the [actions] on its entries, so a message with Undo shows again after a turn.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        HistoryModel(
            entries = app.cache.entries,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            accountSettings = app.accountSettings.state,
            client = Session(application)::client,
            scope = viewModelScope,
            io = Dispatchers.IO,
        )
    }

    val actions = (application as TokklApplication).let { app ->
        EntryActionsModel(
            changes = app.changes,
            undoDelete = app.sender::undoDelete,
            canRestore = { app.capabilities.state.value.restore },
            scope = viewModelScope,
        )
    }
}
