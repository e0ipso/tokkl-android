package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the home screen widget (docs/PRD.md N3) in step with the phone. Every start, stop and edit
 * lands in the cache first, whichever surface makes it, so following the cache covers the app,
 * the notification, the tile, a shortcut, the widget's own taps and a refresh that finds a timer
 * started elsewhere.
 *
 * The key of what the widget last showed is kept on disk, so a new process updates the widget
 * only when the phone's state changed since, as when a tap on the widget started that process.
 * Nothing here schedules work or wakes the phone: the widget's clock is a Chronometer that the
 * launcher ticks, and the widget sets no update period.
 *
 * @param keys keys of what one widget provider shows. Collected on [io], since they read the disk.
 * @param loadShown the key of what the widget last showed, null for none.
 * @param saveShown replaces it.
 * @param update draws every placed widget again from the phone's state.
 */
class WidgetSync(
    private val keys: Flow<String>,
    private val loadShown: () -> String?,
    private val saveShown: (String) -> Unit,
    private val update: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    /** Keeps the Timer widget's lazy state factory compatible with independent key flows. */
    constructor(
        state: () -> Flow<WidgetState>,
        loadShown: () -> String?,
        saveShown: (String) -> Unit,
        update: suspend () -> Unit,
        scope: CoroutineScope,
        io: CoroutineContext,
    ) : this(flow { emitAll(state().map { it.key() }) }, loadShown, saveShown, update, scope, io)

    /** Follows the phone's state until the scope ends. Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the phone's state until it ends. */
    suspend fun follow() {
        keys.distinctUntilChanged().collect(::apply)
    }

    /** Updates the widgets unless they already show what [key] stands for. */
    suspend fun apply(key: String) {
        if (key == loadShown()) return
        update()
        try {
            saveShown(key)
        } catch (e: IOException) {
            // The widgets are right. A new process draws them once more, which costs nothing else.
        }
    }
}

/**
 * The key of what one provider last showed, in private preferences under [keyName]. The key is a hash,
 * so the file holds no description or name. It stays on the phone, because the backup and data
 * extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class WidgetStore(context: Context, private val keyName: String = TIMER_KEY) {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun load(): String? = preferences.getString(keyName, null)

    @Throws(IOException::class)
    fun save(key: String) {
        if (!preferences.edit().putString(keyName, key).commit()) throw IOException("Could not write $FILE_NAME")
    }

    companion object {
        private const val FILE_NAME = "widget"
        const val TIMER_KEY = "shown"
        const val POMODORO_KEY = "pomodoro_shown"
    }
}
