package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.cache.ChangeDiscards
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.FrameContributions
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.storage.StartupLoad
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** What the sync status shows, the changes the server refused and their screen. */
val syncFeatureModule = module {
    single { LastAnswerStore(androidContext(), io()) }

    // When the server last answered the signed-in session (docs/PRD.md O5), which the [Session]'s
    // client reports. It loads at process start from a file of one value.
    single { LastAnswer(get<LastAnswerStore>(), get(), get(), io()) } binds
        arrayOf(SuccessListener::class, AccountDataOwner::class, StartupLoad::class)

    single { DiscardedChangesStore(androidContext(), io()) }

    // How many queued changes the [ChangeSender] discarded because the server no longer had their
    // entries, for the notice that says so (docs/PRD.md O6). It loads at process start from a file
    // of one value.
    single { DiscardedChanges(get<DiscardedChangesStore>(), get(), io()) } binds
        arrayOf(ChangeDiscards::class, AccountDataOwner::class, StartupLoad::class)

    // The "needs attention" list of the changes the server refused (docs/PRD.md O6). Discarding
    // one refreshes the account, so the cache takes the server's entry back.
    single { NeedsAttention(changes = get<ChangeQueue>(), refreshes = get(), scope = get()) }

    viewModel {
        NeedsAttentionViewModel(
            attention = get(),
            settings = get<AccountSettingsSource>().state,
            scope = viewModelCoroutineScope(),
        )
    }

    single {
        val changes = get<ChangeQueue>()
        SyncStatusSource(
            unsent = changes.unsentCount,
            needsAttention = changes.heldCount,
            signInNeeded = get<ChangeSender>().signInNeeded,
            lastAnswered = get<LastAnswer>().state,
            settings = get<AccountSettingsSource>().state,
            scope = get(),
        )
    }

    // The sync status band and the discarded changes' notice in the signed-in frame, in the
    // session's store.
    viewModel {
        SyncFrameViewModel(
            status = get<SyncStatusSource>().status,
            discarded = get(),
            signIns = get(),
            scope = viewModelCoroutineScope(),
        )
    }

    single { SyncEntries } bind FeatureRoutes::class
    single { SyncFrameContributions } bind FrameContributions::class
}
