package com.github.e0ipso.tokkl.widget

import android.content.Context
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.CoroutineContext

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
 * @param shown the key of what the widget last showed, null for none.
 * @param update draws every placed widget again from the phone's state.
 */
class WidgetSync(
    private val keys: Flow<String>,
    private val shown: ValueStore<String>,
    private val update: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    /** Follows the phone's state until the scope ends. Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the phone's state until it ends. */
    suspend fun follow() {
        keys.distinctUntilChanged().collect(::apply)
    }

    /** Updates the widgets unless they already show what [key] stands for. */
    suspend fun apply(key: String) {
        if (key == shown.load()) return
        update()
        try {
            shown.save(key)
        } catch (_: IOException) {
            // The widgets are right. A new process draws them once more, which costs nothing else.
        }
    }
}

/**
 * The key of what one provider last showed, in private preferences under [keyName]. The key is a hash,
 * so the file holds no description or name. It stays on the phone, because the backup and data
 * extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class WidgetStore(context: Context, private val keyName: String = TIMER_KEY) : ValueStore<String> {

    private val file = PreferencesFile(context, FILE_NAME)

    override suspend fun load(): String? = file.read { getString(keyName, null) }

    @Throws(IOException::class)
    override suspend fun save(value: String) {
        file.commit { putString(keyName, value) }
    }

    companion object {
        private const val FILE_NAME = "widget"
        const val TIMER_KEY = "shown"
        const val POMODORO_KEY = "pomodoro_shown"
    }
}
