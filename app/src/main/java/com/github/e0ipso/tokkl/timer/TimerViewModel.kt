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
import kotlinx.coroutines.withContext

/**
 * Keeps the timer's draft and the taps the cache or the server does not show yet across
 * configuration changes, and the timer templates on its panel. The timer reads the app's shared
 * timer controls and sends the pause chain's actions to the server.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        TimerModel(
            controls = app.timerControls,
            projects = app.cache.projects,
            tasks = app.cache.tasks,
            accountSettings = app.accountSettings.state,
            changes = app.changes,
            scope = viewModelScope,
            trace = TapTrace.System,
            pauseChain = app.timerActions,
            sendQueued = { app.sender.sendAndWait() },
            discardNotices = app.timerDiscardNotice,
            // Records the notice as shown in the account's timer state store.
            takeDiscardNotice = { withContext(Dispatchers.IO) { app.timerState.takeDiscardNotice() } },
            timerApi = app.capabilities.state.map { it.timerState },
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
