package com.github.e0ipso.tokkl.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/** Keeps the trash page's state across configuration changes, so turning the phone sends no request. */
class TrashViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        TrashModel(
            client = Session(application)::client,
            refreshCache = { app.accountSettings.refreshAfterChange() },
            trashMissing = app.capabilities::trashMissing,
            scope = viewModelScope,
            io = Dispatchers.IO,
            settings = app.accountSettings.state,
        )
    }
}
