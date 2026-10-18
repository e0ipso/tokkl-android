package com.github.e0ipso.tokkl

import android.app.Application
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import com.github.e0ipso.tokkl.settings.syncApplicationNightMode
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.map

class TokklApplication : Application() {

    /**
     * Loaded on first use, which is MainActivity's onCreate, on the main thread. The first frame
     * has to know the theme, or launch would show the other mode's colours first. Android learns
     * the chosen mode too, for the starting window of the next launch.
     */
    val themeSettings: ThemeSettingsModel by lazy {
        val store = ThemeSettingsStore(this)
        val scope = MainScope()
        ThemeSettingsModel(initial = store.load(), save = store::save, scope = scope, io = Dispatchers.IO).also { model ->
            syncApplicationNightMode(this, model.state.map { it.mode }, scope, Dispatchers.IO)
        }
    }

    /**
     * Loaded on first use, which reads the stored settings from disk. That is usually
     * MainActivity's onStart on the main thread, and the file holds four short values. Each
     * refresh refreshes the [capabilities] and the [cache] too, with the same `GET /api/v9/me`,
     * and the cache takes the favorites from the settings' `GET /api/v9/me/preferences`.
     */
    val accountSettings: AccountSettingsSource by lazy {
        val store = AccountSettingsStore(this)
        AccountSettingsSource(
            initial = store.load(),
            save = store::save,
            client = Session(this)::client,
            scope = MainScope(),
            io = Dispatchers.IO,
            onRefresh = { api, me, preferences -> cache.refresh(api, me, preferences, capabilities.refresh(api, me)) },
        )
    }

    /** What the server offers. Loaded on first use from a file of four flags. */
    val capabilities: CapabilitiesSource by lazy {
        val store = CapabilitiesStore(this)
        CapabilitiesSource(initial = store.load(), save = store::save)
    }

    /** The offline cache. Its database file opens on the first query, which Room keeps off the main thread. */
    val cache: AccountCache by lazy { AccountCache(CacheDatabase.open(this)) }

    /**
     * The trust refusals of the signed-in session's requests, which every
     * [Session] reports and the navigation host prompts about. Accepting one
     * refreshes the account settings, the request that ran into it.
     */
    val sessionTrust: SessionTrustModel by lazy {
        SessionTrustModel(
            trust = ServerTrustStore(this),
            retry = { accountSettings.refresh() },
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Every process, whatever starts it, sends requests only to the stored server.
        ConfiguredServer.readStoredFrom(ServerConfigStore(this)::load)
        // A plain http:// server gets requests only once the user has confirmed it.
        ConfiguredTrust.readFrom(ServerTrustStore(this))
    }
}
