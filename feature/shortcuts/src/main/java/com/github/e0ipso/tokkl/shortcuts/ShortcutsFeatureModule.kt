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

val shortcutsFeatureModule = module {
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
