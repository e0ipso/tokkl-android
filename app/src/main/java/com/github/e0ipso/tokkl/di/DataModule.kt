package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.AccountServerSpans
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.CancelWaitingSend
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.ChangeStore
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.cache.SendOnQueue
import com.github.e0ipso.tokkl.cache.ServerSpans
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Room cache, and the change queue and its sender. */
internal val dataModule = module {

    /** The file of the cache and the queue. It opens on the first query, which Room keeps off the main thread. */
    single { CacheDatabase.open(androidContext()) }

    /** The offline cache. */
    single { AccountCache(get()) }

    single<ChangeStore> { RoomChangeStore(get()) }

    single { AccountServerSpans(get()) } bind ServerSpans::class

    /**
     * Start, stop, create, edit and delete entries on the phone. The [ChangeSender] sends each
     * change at once, and [BackgroundSend] sends what is left when the network returns, also with
     * the app closed.
     */
    single { SendOnQueue(androidContext(), get()) }
    single { ChangeQueue(get(), io(), onQueued = get<SendOnQueue>()::queued, spans = get<AccountServerSpans>()) } bind EntryChanges::class

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
    } bind AccountDataOwner::class

}
