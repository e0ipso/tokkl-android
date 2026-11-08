package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** The favorites as launcher shortcuts. */
val shortcutsFeatureModule = module {
    // The favorites as launcher shortcuts (docs/PRD.md N6), which follow the cached favorites and
    // the capabilities from process start. The capabilities load on the first collection, off the
    // main thread.
    single {
        val launcher = LauncherShortcuts(androidContext())
        FavoriteShortcutSync(
            shortcuts = favoriteShortcutLists(androidContext(), launcher, get(), get()),
            launcher = launcher,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class
    single(named("hook-favorite-shortcuts")) {
        val sync = inject<FavoriteShortcutSync>()
        AppHook(AppHookOrder.FAVORITE_SHORTCUTS, processStart = { sync.value.start() })
    }
}
