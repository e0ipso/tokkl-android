package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.AccountServerSpans
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.BackgroundSendRuns
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.ChangeSenderEvents
import com.github.e0ipso.tokkl.cache.ChangeStore
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.CurrentEntryIds
import com.github.e0ipso.tokkl.cache.DeleteUndoer
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.cache.RunningCheckResults
import com.github.e0ipso.tokkl.cache.ServerSpans
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The Room cache, the change queue and its sender, and their background work. */
internal val dataModule = module {

    // The file of the cache and the queue. It opens on the first query, which Room keeps off the main thread.
    single { CacheDatabase.open(androidContext()) }

    // The offline cache.
    single { AccountCache(get(), get()) } binds arrayOf(AccountDataOwner::class, CurrentEntryCache::class)

    // The queue's tables, which the queue writes and the sender reads.
    single { RoomChangeStore(get()) } bind ChangeStore::class

    single { AccountServerSpans(get()) } bind ServerSpans::class

    // Start, stop, create, edit and delete entries on the phone. The [ChangeSender] sends each
    // change at once, and [BackgroundSend] sends what is left when the network returns, also with
    // the app closed.
    single { BackgroundSendRuns() }
    single { BackgroundSend(get(), get()) } bind AccountDataOwner::class
    single {
        val background = get<BackgroundSend>()
        val sender = get<ChangeSender>()
        ChangeQueue(
            get(),
            io(),
            onQueued = {
                // Register the fallback before a fast drain can finish and cancel it.
                background.schedule()
                sender.send()
            },
            clock = get(),
            spans = get<AccountServerSpans>(),
        )
    } binds arrayOf(EntryChanges::class, CurrentEntryIds::class, HeldChanges::class)

    // Sends the queued changes in order. Once it has sent some, the account refreshes with a read
    // that starts after the send, so a refresh that read the server before it cannot put the
    // cache back. A drain that empties the queue cancels the background send's waiting retry.
    // Changes it discards because the server no longer has their entries count in
    // [DiscardedChanges].

    // The running entry's check every 15 minutes while a timer runs, and the guard that keeps a
    // check's answer from landing after sign-out.
    single { RunningCheckResults() }
    single { RunningCheck(get(), get()) } bind AccountDataOwner::class
    // Checks every 15 minutes while the cache holds a running entry.
    single(named("hook-running-check")) {
        val check = inject<RunningCheck>()
        val cache = inject<AccountCache>()
        val scope = get<CoroutineScope>()
        val io = io()
        AppHook(
            AppHookOrder.RUNNING_CHECK,
            processStart = { check.value.follow(cache.value.running.map { it != null }, scope, io) },
        )
    }
    // Takes a delete back, and refreshes once the server restored the entry. The sender sends each
    // delete through it, so an Undo never decides while a delete is on its way.
    single {
        DeleteUndoer(
            get(),
            client = get<Session>(),
            io = io(),
            restored = get<AccountRefreshRequests>()::refreshAfterChange,
        )
    }

    single {
        val refreshes = get<AccountRefreshRequests>()
        val background = get<BackgroundSend>()
        val store = get<ChangeStore>()
        val discards = get<DiscardedChanges>()
        ChangeSender(
            get(),
            client = get<Session>(),
            scope = get(),
            io = io(),
            events = object : ChangeSenderEvents {
                override fun sent() = refreshes.refreshAfterChange()

                // A queue the disk cannot read counts as holding a change, so the work stays.
                override fun emptied() =
                    background.cancelWaiting { runCatching { store.first() == null }.getOrDefault(false) }

                override fun discarded(count: Int) = discards.add(count)
            },
            clock = get(),
            spans = get<AccountServerSpans>(),
            allDay = get<CapabilitiesSource>()::offersAllDay,
            undo = get(),
        )
    } bind AccountDataOwner::class
}
