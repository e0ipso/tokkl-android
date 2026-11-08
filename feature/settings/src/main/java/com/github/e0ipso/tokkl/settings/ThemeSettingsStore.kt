package com.github.e0ipso.tokkl.settings

import android.content.Context
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.ui.theme.ThemeMode
import com.github.e0ipso.tokkl.ui.theme.ThemeSettings
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
 * The first call loads the preferences file from disk.
 */
class ThemeSettingsStore(context: Context) {

    private val file = PreferencesFile(context, FILE_NAME)

    /** The stored settings, with the defaults for anything missing or unknown. */
    fun load(): ThemeSettings {
        val defaults = ThemeSettings()
        val preferences = file.preferences
        val mode = preferences.getString(KEY_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: defaults.mode
        return ThemeSettings(
            mode = mode,
            dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
        )
    }

    @Throws(IOException::class)
    fun save(settings: ThemeSettings) {
        file.commit {
            putString(KEY_MODE, settings.mode.name)
            putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
        }
    }

    /** Goes back to the defaults. */
    @Throws(IOException::class)
    fun clear() {
        file.commit { clear() }
    }

    companion object {
        private const val FILE_NAME = "theme_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

/**
 * The theme settings, read from [store] now, with Android following their mode through
 * [syncApplicationNightMode]. The first frame has to know the theme, or launch would show the other
 * mode's colours first, and Android needs the mode for the starting window of the next launch.
 */
fun themeSettingsWithNightMode(
    context: Context,
    store: ThemeSettingsStore,
    scope: CoroutineScope,
    io: CoroutineContext,
): ThemeSettingsRepository =
    ThemeSettingsRepository(initial = store.load(), store = store::save, scope = scope, io = io).also { themes ->
        syncApplicationNightMode(context, themes.state.map { it.mode }, scope, io)
    }
