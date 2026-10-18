package com.github.e0ipso.tokkl.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * The theme settings, in the app's private preferences like
 * [com.github.e0ipso.tokkl.session.ServerConfigStore]. They stay on the phone: the backup and
 * data extraction rules leave out every domain.
 *
 * The first call loads the preferences file from disk.
 */
class ThemeSettingsStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored settings, with the defaults for anything missing or unknown. */
    fun load(): ThemeSettings {
        val defaults = ThemeSettings()
        val mode = preferences.getString(KEY_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: defaults.mode
        return ThemeSettings(mode = mode, dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor))
    }

    @Throws(IOException::class)
    fun save(settings: ThemeSettings) {
        commit {
            putString(KEY_MODE, settings.mode.name)
            putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
        }
    }

    /** Goes back to the defaults. */
    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    internal companion object {
        const val FILE_NAME = "theme_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}
