package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.AccountServerSpans
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.BackgroundSendRuns
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.CancelWaitingSend
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.ChangeSenderEvents
import com.github.e0ipso.tokkl.cache.ChangeStore
import com.github.e0ipso.tokkl.cache.CurrentEntryIds
import com.github.e0ipso.tokkl.cache.DeleteUndo
import com.github.e0ipso.tokkl.cache.DeleteUndoer
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.cache.QueuedListener
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.cache.RunningCheckResults
import com.github.e0ipso.tokkl.cache.SendOnQueue
import com.github.e0ipso.tokkl.cache.ServerSpans
import com.github.e0ipso.tokkl.cache.UnsentChanges
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The Room cache, the change queue and its sender, and their background work. */
internal val dataModule = module {

    // The file of the cache and the queue. It opens on the first query, which Room keeps off the main thread.
    single { CacheDatabase.open(androidContext()) }

    // The offline cache.
    single { AccountCache(get(), get()) } bind AccountDataOwner::class

    // The queue's tables, which the queue writes and the sender reads.
    single { RoomChangeStore(get()) } bind ChangeStore::class

    single { AccountServerSpans(get()) } bind ServerSpans::class

    // Start, stop, create, edit and delete entries on the phone. The [ChangeSender] sends each
    // change at once, and [BackgroundSend] sends what is left when the network returns, also with
    // the app closed.
    single { BackgroundSendRuns() }
    single { BackgroundSend(get(), get()) } bind AccountDataOwner::class
    single { SendOnQueue(get(), get()) } bind QueuedListener::class
    single {
        ChangeQueue(
            get(),
            io(),
            onQueued = get<SendOnQueue>(),
            clock = get(),
            spans = get<AccountServerSpans>(),
        )
    } binds arrayOf(EntryChanges::class, CurrentEntryIds::class, HeldChanges::class, UnsentChanges::class)

    // Sends the queued changes in order. Once it has sent some, the account refreshes with a read
    // that starts after the send, so a refresh that read the server before it cannot put the
    // cache back. A drain that empties the queue cancels the background send's waiting retry.
    // Changes it discards because the server no longer has their entries count in
    // [DiscardedChanges].
    single { CancelWaitingSend(get(), get()) }

    // The running entry's check every 15 minutes while a timer runs, and the guard that keeps a
    // check's answer from landing after sign-out.
    single { RunningCheckResults() }
    single { RunningCheck(get(), get()) } bind AccountDataOwner::class
    // Takes a delete back, and refreshes once the server restored the entry. The sender sends each
    // delete through it, so an Undo never decides while a delete is on its way.
    single {
        DeleteUndoer(
            get(),
            client = get<Session>(),
            io = io(),
            restored = get<AccountRefreshRequests>()::refreshAfterChange,
        )
    } bind DeleteUndo::class

    single {
        val refreshes = get<AccountRefreshRequests>()
        val waitingSend = get<CancelWaitingSend>()
        val discards = get<DiscardedChanges>()
        ChangeSender(
            get(),
            client = get<Session>(),
            scope = get(),
            io = io(),
            events = object : ChangeSenderEvents {
                override fun sent() = refreshes.refreshAfterChange()

                override fun emptied() = waitingSend.emptied()

                override fun discarded(count: Int) = discards.add(count)
            },
            clock = get(),
            spans = get<AccountServerSpans>(),
            allDay = get<CapabilitiesSource>()::offersAllDay,
            undo = get(),
        )
    } binds arrayOf(QueueSender::class, AccountDataOwner::class)
}
