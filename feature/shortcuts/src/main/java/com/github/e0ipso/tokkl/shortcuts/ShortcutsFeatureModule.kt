package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The favorites as launcher shortcuts. */
val shortcutsFeatureModule = module {
    // The favorites as launcher shortcuts (docs/PRD.md N6), which follow the cached favorites and
    // the capabilities from process start, once the capabilities have loaded.
    single {
        val launcher = LauncherShortcuts(androidContext())
        FavoriteShortcutSync(
            shortcuts = favoriteShortcutLists(
                androidContext(),
                launcher,
                get<CapabilitiesSource>().loaded,
                favorites = get<AccountCache>().favorites,
                projects = get<AccountCache>().projects,
            ),
            launcher = launcher,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class
    single { FavoriteShortcutsHook(inject()) } bind AppHook::class

    // What a tap on a favorite's shortcut starts, read when the tap comes.
    single {
        val capabilities = get<CapabilitiesSource>()
        val settings = get<AccountSettingsSource>()
        FavoriteShortcutStarts(
            available = { capabilities.current().favorites },
            favorites = get<AccountCache>().favorites,
            projects = get<AccountCache>().projects,
            workspaceId = { settings.current()?.defaultWorkspaceId },
            changes = get(),
        )
    }
}
