package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.navhost.SignedInViewModel
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Every feature's screens, as the navigation host adds them to its entry provider. */
internal val FeatureScreens = named("feature-screens")

/**
 * The features' screens and top-level pages, and the signed-in frame's view model, which lives in
 * the session's store.
 */
internal val navigationModule = module {
    single(FeatureScreens) { getAll<FeatureEntries>() }

    // Every feature's top-level pages, in order.
    single { TopLevelPages(get<List<FeatureEntries>>(FeatureScreens).flatMap { it.pages }) }

    viewModel {
        SignedInViewModel(
            badges = getAll(),
            changes = get(),
            trust = get(),
            scope = viewModelCoroutineScope(),
        )
    }
}
