package com.github.e0ipso.tokkl.settings

import android.app.UiModeManager
import android.content.Context
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** The night mode to give Android for this theme mode. Following the system leaves it to Android. */
val ThemeMode.applicationNightMode: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }

/**
 * Gives Android each mode in [modes] as the app's night mode, starting with the current one.
 * Android draws the starting window from the manifest theme before the app runs, and picks
 * res/values or res/values-night by the night mode it keeps for the app. Without this, a light or
 * dark setting that differs from the system's starts in the system's colours.
 *
 * The setting stays the source of truth, so this runs on every start too and repairs a night mode
 * that went out of step. Each call is a binder call, so it runs on [io]. MainActivity handles the
 * configuration change itself, so a change keeps the activity and the screen it shows.
 */
fun syncApplicationNightMode(context: Context, modes: Flow<ThemeMode>, scope: CoroutineScope, io: CoroutineContext) {
    val uiModeManager = context.getSystemService(UiModeManager::class.java)
    modes.distinctUntilChanged()
        .onEach { uiModeManager.setApplicationNightMode(it.applicationNightMode) }
        .flowOn(io)
        .launchIn(scope)
}
