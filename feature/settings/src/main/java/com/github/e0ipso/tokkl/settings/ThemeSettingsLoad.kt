package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsRepository

/** The theme settings' load at process start, which MainActivity's first frame waits for. */
class ThemeSettingsLoad(private val themes: ThemeSettingsRepository) : StartupLoad {
    override val isReady: Boolean get() = themes.isReady

    override suspend fun ready() = themes.ready()
}
