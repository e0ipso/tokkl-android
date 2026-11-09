package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.ForegroundReads
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.Session
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The Room cache, the change queue and its sender, the check of a running entry, their
 * WorkManager workers, and the foreground refresh, which sends the queue before it refreshes.
 */
val cacheModule = module {

    // The file of the cache and the queue. It opens on the first query, which Room keeps off the main thread.
    single { CacheDatabase.open(androidContext()) }

    // The offline cache.
    single { AccountCache(get()) } binds arrayOf(AccountDataOwner::class, CurrentEntryCache::class)
    single { ServerCacheRefresh(get(), get(), get()) }

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
            onQueued = get(),
            clock = get(),
            spans = get<AccountServerSpans>(),
        )
    } binds arrayOf(EntryChanges::class, CurrentEntryIds::class, HeldChanges::class, UnsentChanges::class)

    // Sends the queued changes in order, and refreshes, cancels the background retry and counts
    // the discarded changes as its drains end ([QueueDrainEvents]).
    single<ChangeSenderEvents> { QueueDrainEvents(get(), get(), get(), get()) }
    single {
        ChangeSender(
            get(),
            client = get<Session>(),
            scope = get(),
            io = io(),
            events = get(),
            clock = get(),
            spans = get<AccountServerSpans>(),
            capabilities = get(),
            undo = get(),
        )
    } binds arrayOf(AccountDataOwner::class, QueueSender::class)

    // Takes a delete back, and refreshes once the server restored the entry. The sender sends each
    // delete through it, so an Undo never decides while a delete is on its way.
    single {
        DeleteUndoer(
            get(),
            client = get<Session>(),
            io = io(),
            refreshes = get(),
        )
    } bind DeleteUndo::class

    // The running entry's check every 15 minutes while a timer runs, and the guard that keeps a
    // check's answer from landing after sign-out.
    single { RunningCheckResults() }
    single { RunningCheck(get(), get()) } bind AccountDataOwner::class
    single { RunningCheckHook(inject(), inject(), get(), io()) } bind AppHook::class

    // Refreshes the account, and with it the running entry and the cache, each time the app comes
    // to the foreground and each time the network comes back while it is there. MainActivity
    // reports its starts, stops and network changes. Nothing refreshes on a timer.
    single { SendThenRefresh(get(), get()) } bind ForegroundReads::class
    single { ForegroundRefresh(get()) }

    // The queue's WorkManager workers, which KoinWorkerFactory makes with their dependencies.
    worker { params ->
        BackgroundSendWorker(
            context = androidContext(),
            params = params.get(),
            sender = get(),
            runs = get(),
            workManager = get(),
            io = io(),
        )
    }
    worker { params ->
        RunningCheckWorker(
            context = androidContext(),
            params = params.get(),
            cache = get(),
            session = get(),
            results = get(),
            io = io(),
        )
    }
}
