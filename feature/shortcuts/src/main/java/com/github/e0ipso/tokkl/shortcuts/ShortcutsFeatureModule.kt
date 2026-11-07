package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The favorites as launcher shortcuts. */
val shortcutsFeatureModule = module {
    /**
     * The favorites as launcher shortcuts (docs/PRD.md N6), which follow the cached favorites and
     * the capabilities from process start. The capabilities load on the first collection, off the
     * main thread.
     */
    single {
        val launcher = LauncherShortcuts(androidContext())
        FavoriteShortcutSync(
            shortcuts = favoriteShortcutLists(androidContext(), launcher, get(), get()),
            published = launcher::published,
            publish = launcher::publish,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class
}
