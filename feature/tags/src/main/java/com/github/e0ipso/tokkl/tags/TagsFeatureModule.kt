package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountRefresh
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
        TagsViewModel(
            repository = get(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedTags = get<AccountCache>().tags,
        )
    }

    single { TagsEntries } bind FeatureEntries::class
}
