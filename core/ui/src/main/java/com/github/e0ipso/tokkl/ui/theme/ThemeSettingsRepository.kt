package com.github.e0ipso.tokkl.ui.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * Where the theme settings are kept. The settings feature keeps them in a preferences file, out of
 * this module's reach; the unit tests keep them in memory.
 */
interface ThemeSettingsDisk {
    @Throws(IOException::class)
    suspend fun load(): ThemeSettings

    @Throws(IOException::class)
    suspend fun save(settings: ThemeSettings)
}

/**
 * The theme settings for the whole process. They load as this is made, in [scope], and the store
 * reads the disk off the main thread; MainActivity draws its first frame once they have loaded,
 * so the app starts in the right colours. A change shows in [state] at once, so the theme redraws
 * without a restart, and then goes to disk, one write at a time. When a write fails, [state] goes
 * back to what the disk holds, unless a newer change has replaced it, so the screen never shows a
 * choice that would be gone after a restart.
 *
 * @param store reads and writes the stored settings.
 * @param scope outlives every screen, so leaving the app does not cancel a pending write.
 * @param loadContext where the stored settings load, the I/O dispatcher in the app, so the load
 *   never waits for the main thread.
 */
class ThemeSettingsRepository(
    private val store: ThemeSettingsDisk,
    private val scope: CoroutineScope,
    loadContext: CoroutineContext,
) {

    private val mutableState = MutableStateFlow(ThemeSettings())

    /** The settings: the defaults until the stored ones load. */
    val state: StateFlow<ThemeSettings> = mutableState.asStateFlow()

    private val writes = Mutex()

    /** What the disk holds. Read and written only while holding [writes], or by the load. */
    private var saved = ThemeSettings()

    private val loading = scope.launch(loadContext) {
        try {
            val stored = store.load()
            saved = stored
            mutableState.value = stored
        } catch (_: IOException) {
            // The defaults stay, as when nothing is stored.
        }
    }

    /** Whether the stored settings have loaded. */
    val isReady: Boolean get() = loading.isCompleted

    /** Waits for the stored settings to load. */
    suspend fun ready() = loading.join()

    /** [state] once the stored settings have loaded. */
    val loaded: Flow<ThemeSettings> = flow {
        ready()
        emitAll(state)
    }

    fun onModeChange(mode: ThemeMode) = change { it.copy(mode = mode) }

    fun onDynamicColorChange(on: Boolean) = change { it.copy(dynamicColor = on) }

    /** Once loaded, as for every screen, the change shows before this returns. */
    private fun change(edit: (ThemeSettings) -> ThemeSettings) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ready()
            mutableState.update(edit)
            writes.withLock {
                val wanted = mutableState.value
                if (wanted == saved) return@withLock
                try {
                    store.save(wanted)
                    saved = wanted
                } catch (_: IOException) {
                    mutableState.compareAndSet(wanted, saved)
                }
            }
        }
    }
}
