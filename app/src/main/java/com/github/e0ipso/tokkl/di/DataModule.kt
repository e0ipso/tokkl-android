package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.DiscardedChangesStore
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.sync.LastAnswerStore
import com.github.e0ipso.tokkl.sync.NeedsAttentionModel
import com.github.e0ipso.tokkl.sync.SyncStatusSource
import com.github.e0ipso.tokkl.time.SnappedSpan
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Room cache, the change queue and its sender, and what the sync status shows. */
internal val dataModule = module {

    /** The file of the cache and the queue. It opens on the first query, which Room keeps off the main thread. */
    single { CacheDatabase.open(androidContext()) }

    /** The offline cache. */
    single { AccountCache(get()) }

    /**
     * Start, stop, create, edit and delete entries on the phone. The [ChangeSender] sends each
     * change at once, and [BackgroundSend] sends what is left when the network returns, also with
     * the app closed.
     */
    single {
        val context = androidContext()
        val sender = inject<ChangeSender>()
        val accountSettings = inject<AccountSettingsSource>()
        ChangeQueue(
            get(),
            io(),
            onQueued = {
                // Register the fallback before a fast drain can finish and cancel it.
                BackgroundSend.schedule(context)
                sender.value.send()
            },
            stopSpan = { start, at, oneStep ->
                accountSettings.value.state.value?.time()?.snapStop(start, at, oneStep) ?: SnappedSpan(start, maxOf(at, start))
            },
            capturedStopSpan = { start, at ->
                val time = accountSettings.value.state.value?.time()
                if (time == null) SnappedSpan(start, maxOf(at, start)) else time.snapCapturedStop(start, at)
            },
        )
    }

    /**
     * Sends the queued changes in order. Once it has sent some, the account refreshes with a read
     * that starts after the send, so a refresh that read the server before it cannot put the
     * cache back. A drain that empties the queue cancels the background send's waiting retry.
     * Changes it discards because the server no longer has their entries count in
     * [DiscardedChanges].
     */
    single {
        val context = androidContext()
        val store = RoomChangeStore(get())
        val accountSettings = inject<AccountSettingsSource>()
        val discarded = inject<DiscardedChanges>()
        val capabilities = inject<CapabilitiesSource>()
        ChangeSender(
            store,
            client = get<Session>()::client,
            scope = get(),
            io = io(),
            onSent = { accountSettings.value.refreshAfterChange() },
            // A queue the disk cannot read counts as holding a change, so the work stays.
            onEmptied = { BackgroundSend.cancelWaiting(context) { runCatching { store.first() == null }.getOrDefault(false) } },
            onDiscarded = { count -> discarded.value.add(count) },
            serverStart = { start -> accountSettings.value.state.value?.time()?.snapInstant(start) ?: start },
            switchSpan = { start, stop ->
                accountSettings.value.state.value?.time()?.snapStop(start, stop, oneStep = false) ?: SnappedSpan(start, stop)
            },
            allDay = { capabilities.value.state.value.allDay == true },
        )
    }

    single { LastAnswerStore(androidContext()) }

    /**
     * When the server last answered the signed-in session (docs/PRD.md O5), which the [Session]'s
     * client reports. Loaded on the first answer or the first frame of a signed-in screen, from a
     * file of one value.
     */
    single {
        val store = get<LastAnswerStore>()
        LastAnswer(initial = store.load(), save = store::save)
    }

    single { DiscardedChangesStore(androidContext()) }

    /**
     * How many queued changes the [ChangeSender] discarded because the server no longer had their
     * entries, for the notice that says so (docs/PRD.md O6). Loaded on the first discard or the
     * first frame of a signed-in screen, from a file of one value.
     */
    single {
        val store = get<DiscardedChangesStore>()
        DiscardedChanges(initial = store.load(), save = store::save)
    }

    /**
     * The "needs attention" list of the changes the server refused (docs/PRD.md O6). Discarding
     * one refreshes the account, so the cache takes the server's entry back.
     */
    single {
        val changes = get<ChangeQueue>()
        val accountSettings = inject<AccountSettingsSource>()
        NeedsAttentionModel(
            held = changes.held,
            release = changes::sendAgain,
            drop = changes::discard,
            afterDiscard = { accountSettings.value.refresh() },
            scope = get(),
        )
    }

    single { SyncStatusSource(get(), get(), get(), get(), get()) }
}
