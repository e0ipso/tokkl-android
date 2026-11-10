package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.navhost.SignedInViewModel
import com.github.e0ipso.tokkl.navigation.FeatureNavigation
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val navigationModule = module {
    single { FeatureNavigation(routes = getAll(), requests = getAll(), frame = getAll()) }

    single { TopLevelPages(get<FeatureNavigation>().routes.flatMap { it.pages }) }

    viewModel {
        SignedInViewModel(badges = getAll(), changes = get(), trust = get(), scope = viewModelCoroutineScope())
    }
}
