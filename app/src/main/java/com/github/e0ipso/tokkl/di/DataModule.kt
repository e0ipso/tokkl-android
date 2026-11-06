package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.AccountServerSpans
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.CancelWaitingSend
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.ChangeStore
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.cache.SendOnQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.DiscardedChangesStore
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.sync.LastAnswerStore
import com.github.e0ipso.tokkl.sync.NeedsAttention
import com.github.e0ipso.tokkl.sync.SyncStatusSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Room cache, the change queue and its sender, and what the sync status shows. */
internal val dataModule = module {

    /** The file of the cache and the queue. It opens on the first query, which Room keeps off the main thread. */
    single { CacheDatabase.open(androidContext()) }

    /** The offline cache. */
    single { AccountCache(get()) }

    single<ChangeStore> { RoomChangeStore(get()) }

    single { AccountServerSpans(get()) }

    /**
     * Start, stop, create, edit and delete entries on the phone. The [ChangeSender] sends each
     * change at once, and [BackgroundSend] sends what is left when the network returns, also with
     * the app closed.
     */
    single { SendOnQueue(androidContext(), get()) }
    single { ChangeQueue(get(), io(), onQueued = get<SendOnQueue>()::queued, spans = get<AccountServerSpans>()) }

    /**
     * Sends the queued changes in order. Once it has sent some, the account refreshes with a read
     * that starts after the send, so a refresh that read the server before it cannot put the
     * cache back. A drain that empties the queue cancels the background send's waiting retry.
     * Changes it discards because the server no longer has their entries count in
     * [DiscardedChanges].
     */
    single { CancelWaitingSend(androidContext(), get()) }
    single {
        ChangeSender(
            get(),
            client = get<Session>(),
            scope = get(),
            io = io(),
            onSent = get<AccountRefreshRequests>()::refreshAfterChange,
            onEmptied = get<CancelWaitingSend>()::emptied,
            onDiscarded = get<DiscardedChanges>()::add,
            spans = get<AccountServerSpans>(),
            allDay = get<CapabilitiesSource>()::offersAllDay,
        )
    }

    single { LastAnswerStore(androidContext()) }

    /**
     * When the server last answered the signed-in session (docs/PRD.md O5), which the [Session]'s
     * client reports. The first answer or the first frame of a signed-in screen loads it from a
     * file of one value.
     */
    single { LastAnswer(get<LastAnswerStore>()) }

    single { DiscardedChangesStore(androidContext()) }

    /**
     * How many queued changes the [ChangeSender] discarded because the server no longer had their
     * entries, for the notice that says so (docs/PRD.md O6). The first discard or the first frame
     * of a signed-in screen loads it from a file of one value.
     */
    single { DiscardedChanges(get<DiscardedChangesStore>()) }

    /**
     * The "needs attention" list of the changes the server refused (docs/PRD.md O6). Discarding
     * one refreshes the account, so the cache takes the server's entry back.
     */
    single {
        val changes = get<ChangeQueue>()
        NeedsAttention(
            held = changes.held,
            release = changes::sendAgain,
            drop = changes::discard,
            afterDiscard = get<AccountRefresh>()::refresh,
            scope = get(),
        )
    }

    single { SyncStatusSource(get(), get(), get(), get(), get()) }
}
