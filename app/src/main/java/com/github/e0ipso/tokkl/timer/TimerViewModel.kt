package com.github.e0ipso.tokkl.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.favorites.FavoritesModel
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

/**
 * Keeps the timer's draft and the taps the cache does not show yet across configuration changes,
 * and the timer templates on its panel.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        TimerModel(
            controls = app.changes.timerControls,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            accountSettings = app.accountSettings.state,
            changes = app.changes,
            scope = viewModelScope,
            trace = TapTrace.System,
        )
    }

    val favorites = (application as TokklApplication).let { app ->
        FavoritesModel(
            favorites = app.cache.favorites,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            available = app.capabilities.state.map { it.favorites },
            save = FavoritesServer(client = Session(app)::client, cache = app.cache)::save,
            scope = viewModelScope,
            io = Dispatchers.IO,
        )
    }
}
