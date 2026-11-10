package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.Session
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.singleOf
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

    single { AccountCache(get(), io()) } bind AccountDataOwner::class
    singleOf(::RoomCurrentEntries) bind CurrentEntryCache::class
    single { ServerCacheRefresh(get(), get(), allDayMissing = get<CapabilitiesSource>()::allDayMissing) }

    singleOf(::RoomChangeStore) binds arrayOf(ChangeStore::class, QueueStorage::class)

    single {
        val settings = get<AccountSettingsSource>()
        AccountServerSpans(settings.state, settings)
    } bind ServerSpans::class

    singleOf(::BackgroundSendRuns)
    singleOf(::BackgroundSend) bind AccountDataOwner::class
    single {
        val background = get<BackgroundSend>()
        val sender = get<ChangeSender>()
        ChangeQueue(
            get<QueueStorage>(),
            io(),
            // The background send first, so a fast drain cannot finish and cancel it before it is
            // registered, then a send at once.
            onQueued = {
                background.schedule()
                sender.send()
            },
            clock = get(),
            spans = get<AccountServerSpans>(),
        )
    } binds arrayOf(EntryChanges::class, RunningEntryChanges::class, HeldChanges::class)

    singleOf(::QueueDrainEvents) bind ChangeSenderEvents::class
    single {
        val session = get<Session>()
        ChangeSender(
            get(),
            entries = { session() },
            scope = get(),
            io = io(),
            events = get(),
            clock = get(),
            spans = get<AccountServerSpans>(),
            capabilities = get(),
            undo = get(),
        )
    } bind AccountDataOwner::class

    single {
        val session = get<Session>()
        DeleteUndoer(
            get(),
            entries = { session() },
            io = io(),
            refreshes = get(),
            scope = get(),
        )
    }

    singleOf(::RunningCheck) bind AccountDataOwner::class
    single { RunningCheckHook(inject(), inject(), get(), io()) } bind AppHook::class

    // The queued changes go first, then the account refreshes. The send does not wait for the
    // refresh, so an expired token still asks for sign-in when `/me` fails on opening the app with
    // a saved queue.
    single {
        val sender = get<ChangeSender>()
        val account = get<AccountRefresh>()
        ForegroundRefresh(
            refresh = {
                sender.send()
                account.refresh()
            },
            refreshOnReconnect = {
                sender.send()
                account.refreshOnReconnect()
            },
        )
    }

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
            account = get(),
            io = io(),
        )
    }
}
