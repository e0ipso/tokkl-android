package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Tags page: its repository, its view model and its screen. */
val tagsFeatureModule = module {
    single<TagsRepository> { ServerTagsRepository(get<Session>()) }

    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val scope = viewModelCoroutineScope()
        TagsViewModel(
            repository = get(),
            settings = accountSettings,
            changes = get(),
            scope = scope,
            io = io(),
            cachedTags = get<AccountCache>().tags,
            offline = get<DefaultNetwork>().offlineIn(scope),
            app = get(),
        )
    }

    single { TagsEntries } bind FeatureEntries::class
}
