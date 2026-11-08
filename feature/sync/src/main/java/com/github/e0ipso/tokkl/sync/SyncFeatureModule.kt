package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** What the sync status shows, the changes the server refused and their screen. */
val syncFeatureModule = module {
    single { LastAnswerStore(androidContext()) }

    // When the server last answered the signed-in session (docs/PRD.md O5), which the [Session]'s
    // client reports. The first answer or the first frame of a signed-in screen loads it from a
    // file of one value.
    single { LastAnswer(get<LastAnswerStore>(), get()) } binds arrayOf(SuccessListener::class, AccountDataOwner::class)

    single { DiscardedChangesStore(androidContext()) }

    // How many queued changes the [ChangeSender] discarded because the server no longer had their
    // entries, for the notice that says so (docs/PRD.md O6). The first discard or the first frame
    // of a signed-in screen loads it from a file of one value.
    single { DiscardedChanges(get<DiscardedChangesStore>()) } bind AccountDataOwner::class

    // The "needs attention" list of the changes the server refused (docs/PRD.md O6). Discarding
    // one refreshes the account, so the cache takes the server's entry back.
    single {
        val refresh = get<AccountRefresh>()
        NeedsAttention(
            changes = get<ChangeQueue>(),
            afterDiscard = { refresh.refresh() },
            scope = get(),
        )
    }

    viewModel {
        NeedsAttentionViewModel(
            attention = get(),
            settings = get<AccountSettingsSource>().state,
            scope = viewModelCoroutineScope(),
        )
    }

    single {
        SyncStatusSource(changes = get(), lastAnswer = get(), accountSettings = get(), sender = get(), scope = get())
    }

    single { SyncEntries } bind FeatureEntries::class
}
