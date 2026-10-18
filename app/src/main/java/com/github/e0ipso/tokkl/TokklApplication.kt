package com.github.e0ipso.tokkl

import android.app.Application
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope

class TokklApplication : Application() {

    /**
     * Loaded on first use, which is MainActivity's onCreate, on the main thread. The first frame
     * has to know the theme, or launch would show the other mode's colours first.
     */
    val themeSettings: ThemeSettingsModel by lazy {
        val store = ThemeSettingsStore(this)
        ThemeSettingsModel(initial = store.load(), save = store::save, scope = MainScope(), io = Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        // Every process, whatever starts it, sends requests only to the stored server.
        ConfiguredServer.readStoredFrom(ServerConfigStore(this)::load)
    }
}
