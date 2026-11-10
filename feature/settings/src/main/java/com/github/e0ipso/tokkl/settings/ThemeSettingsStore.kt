package com.github.e0ipso.tokkl.settings

import android.content.Context
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.ui.theme.ThemeMode
import com.github.e0ipso.tokkl.ui.theme.ThemeSettings
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsDisk
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsRepository
import com.github.e0ipso.tokkl.ui.theme.syncApplicationNightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The theme settings, in the app's private preferences like
 * [com.github.e0ipso.tokkl.session.ServerConfigStore]. They stay on the phone: the backup and
 * data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class ThemeSettingsStore(context: Context, private val io: CoroutineContext) : ThemeSettingsDisk {

    private val file = PreferencesFile(context, FILE_NAME, io)

    /** The stored settings, with the defaults for anything missing or unknown. */
    override suspend fun load(): ThemeSettings = file.read {
        val defaults = ThemeSettings()
        val mode = getString(KEY_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: defaults.mode
        ThemeSettings(
            mode = mode,
            dynamicColor = getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
        )
    }

    @Throws(IOException::class)
    override suspend fun save(settings: ThemeSettings) {
        file.commit {
            putString(KEY_MODE, settings.mode.name)
            putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
        }
    }

    @Throws(IOException::class)
    suspend fun clear() {
        file.commit { clear() }
    }

    companion object {
        private const val FILE_NAME = "theme_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

/**
 * The theme settings, loaded from [store] at process start, with Android following their mode
 * through [syncApplicationNightMode] once they have loaded. The first frame has to know the
 * theme, or launch would show the other mode's colours first, so MainActivity waits for the load
 * before it draws, and Android needs the mode for the starting window of the next launch.
 */
fun themeSettingsWithNightMode(
    context: Context,
    store: ThemeSettingsStore,
    scope: CoroutineScope,
    io: CoroutineContext,
): ThemeSettingsRepository = ThemeSettingsRepository(store, scope, io).also { themes ->
    syncApplicationNightMode(context, themes.loaded.map { it.mode }, scope, io)
}

/** The theme settings' load at process start, which MainActivity's first frame waits for. */
class ThemeSettingsLoad(private val themes: ThemeSettingsRepository) : StartupLoad {
    override val isReady: Boolean get() = themes.isReady

    override suspend fun ready() = themes.ready()
}
