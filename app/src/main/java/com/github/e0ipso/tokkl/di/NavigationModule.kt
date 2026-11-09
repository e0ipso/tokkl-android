package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.navhost.SignedInViewModel
import com.github.e0ipso.tokkl.navigation.FeatureNavigation
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The features' screens and top-level pages, and the signed-in frame's view model, which lives in
 * the session's store.
 */
internal val navigationModule = module {
    // What the features give the navigation: their routes and pages, the taps from outside the app
    // that open them, and what they show in the signed-in frame.
    single { FeatureNavigation(routes = getAll(), requests = getAll(), frame = getAll()) }

    // Every feature's top-level pages, in order.
    single { TopLevelPages(get<FeatureNavigation>().routes.flatMap { it.pages }) }

    viewModel {
        SignedInViewModel(
            badges = getAll(),
            changes = get(),
            trust = get(),
            scope = viewModelCoroutineScope(),
        )
    }
}
