package com.github.e0ipso.tokkl.settings

import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The theme settings for the whole process. A change shows in [state] at once, so the theme
 * redraws without a restart, and then goes to disk on [io], one write at a time. When a write
 * fails, [state] goes back to what the disk holds, unless a newer change has replaced it, so the
 * screen never shows a choice that would be gone after a restart.
 *
 * @param initial the stored settings, read before the first frame so the app starts in the
 *   right colours.
 * @param save writes the settings, throwing [IOException] when it cannot.
 * @param scope outlives every screen, so leaving the app does not cancel a pending write.
 */
class ThemeSettingsModel(
    initial: ThemeSettings,
    private val save: (ThemeSettings) -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<ThemeSettings> = mutableState.asStateFlow()

    private val writes = Mutex()

    /** What the disk holds. Read and written only while holding [writes]. */
    private var saved = initial

    fun onModeChange(mode: ThemeMode) = change { it.copy(mode = mode) }

    fun onDynamicColorChange(on: Boolean) = change { it.copy(dynamicColor = on) }

    private fun change(edit: (ThemeSettings) -> ThemeSettings) {
        mutableState.update(edit)
        scope.launch {
            writes.withLock {
                val wanted = mutableState.value
                if (wanted == saved) return@withLock
                try {
                    withContext(io) { save(wanted) }
                    saved = wanted
                } catch (e: IOException) {
                    mutableState.compareAndSet(wanted, saved)
                }
            }
        }
    }
}
