package com.github.e0ipso.tokkl

import android.app.Application
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.notification.RunningNotificationStore
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.notification.RunningNotifier
import com.github.e0ipso.tokkl.notification.runningNotificationContent
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.settings.SignOutModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import com.github.e0ipso.tokkl.settings.syncApplicationNightMode
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.sync.LastAnswerStore
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.sync.shownSyncStatus
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TokklApplication : Application() {

    /**
     * Loaded on first use, which is MainActivity's onCreate, on the main thread. The first frame
     * has to know the theme, or launch would show the other mode's colours first. Android learns
     * the chosen mode too, for the starting window of the next launch.
     */
    val themeSettings: ThemeSettingsModel by lazy {
        val store = ThemeSettingsStore(this)
        val scope = MainScope()
        ThemeSettingsModel(initial = store.load(), save = store::save, scope = scope, io = Dispatchers.IO).also { model ->
            syncApplicationNightMode(this, model.state.map { it.mode }, scope, Dispatchers.IO)
        }
    }

    /**
     * Loaded on first use, which reads the stored settings from disk. That is usually
     * MainActivity's onStart on the main thread, and the file holds six short values. Each
     * refresh refreshes the [capabilities] and the [cache] too, with the same `GET /api/v9/me`,
     * and the cache takes the favorites, and the [timerView] its view, from the settings'
     * `GET /api/v9/me/preferences`. Then the [sender] sends the queue, since the server has just
     * answered.
     */
    val accountSettings: AccountSettingsSource by lazy {
        val store = AccountSettingsStore(this)
        AccountSettingsSource(
            initial = store.load(),
            save = store::save,
            client = Session(this)::client,
            scope = MainScope(),
            io = Dispatchers.IO,
            onRefresh = { api, me, preferences ->
                val flags = capabilities.refresh(api, me)
                cache.refresh(api, me, preferences, flags)
                timerView.refreshed(preferences, keepsTimerView = flags.favorites)
                sender.send()
            },
        )
    }

    /**
     * Refreshes the account, and with it the running entry and the cache, each time the app comes
     * to the foreground and each time the network comes back while it is there. MainActivity
     * reports its starts, stops and network changes. Nothing refreshes on a timer.
     */
    val foregroundRefresh: ForegroundRefresh by lazy { ForegroundRefresh(refresh = { accountSettings.refresh() }) }

    /** What the server offers. Loaded on first use from a file of four flags. */
    val capabilities: CapabilitiesSource by lazy {
        val store = CapabilitiesStore(this)
        CapabilitiesSource(initial = store.load(), save = store::save)
    }

    /**
     * The Timer page's List or Calendar choice, kept in Tokkl's `timerView` preference. Loaded on
     * first use, which reads a file of two values: the Timer page's first frame on the main thread.
     */
    val timerView: TimerViewSetting by lazy {
        val store = TimerViewStore(this)
        TimerViewSetting(
            initial = store.load(),
            save = store::save,
            client = Session(this)::client,
            keepsTimerView = { capabilities.state.value.favorites },
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    /** The file of the [cache] and the queue. It opens on the first query, which Room keeps off the main thread. */
    private val cacheDatabase: CacheDatabase by lazy { CacheDatabase.open(this) }

    /** The offline cache. */
    val cache: AccountCache by lazy { AccountCache(cacheDatabase) }

    /**
     * Start, stop, create, edit and delete entries on the phone. The [sender] sends each change at
     * once, and [BackgroundSend] sends what is left when the network returns, also with the app
     * closed.
     */
    val changes: ChangeQueue by lazy {
        ChangeQueue(
            cacheDatabase,
            Dispatchers.IO,
            onQueued = {
                sender.send()
                BackgroundSend.schedule(this)
            },
            stopSpan = { start, at, oneStep ->
                accountSettings.state.value?.time()?.snapStop(start, at, oneStep) ?: SnappedSpan(start, maxOf(at, start))
            },
        )
    }

    /**
     * Sends the queued changes in order. Once it has sent some, the account refreshes with a read
     * that starts after the send, so a refresh that read the server before it cannot put the
     * cache back. A drain that empties the queue cancels the background send's waiting retry.
     */
    val sender: ChangeSender by lazy {
        val store = RoomChangeStore(cacheDatabase)
        ChangeSender(
            store,
            client = Session(this)::client,
            scope = MainScope(),
            io = Dispatchers.IO,
            onSent = { accountSettings.refreshAfterChange() },
            // A queue the disk cannot read counts as holding a change, so the work stays.
            onEmptied = { BackgroundSend.cancelWaiting(this) { runCatching { store.first() == null }.getOrDefault(false) } },
            serverStart = { start -> accountSettings.state.value?.time()?.snapUnsent(start, start)?.start ?: start },
            switchSpan = { start, stop -> accountSettings.state.value?.time()?.snapStop(start, stop, oneStep = false) ?: SnappedSpan(start, stop) },
        )
    }

    /**
     * When the server last answered the signed-in session (docs/PRD.md O5), which every
     * [Session]'s client reports. Loaded on the first answer or the first frame of a signed-in
     * screen, from a file of one value.
     */
    val lastAnswer: LastAnswer by lazy {
        val store = LastAnswerStore(this)
        LastAnswer(initial = store.load(), save = store::save)
    }

    /**
     * The status element above the bottom bar: the queue's unsent count and the server's last
     * answer, once changes have waited long enough to show.
     */
    val syncStatus: StateFlow<SyncStatus?> by lazy {
        shownSyncStatus(changes.unsentCount, lastAnswer.state, accountSettings.state)
            .stateIn(MainScope(), SharingStarted.WhileSubscribed(STATUS_STOP_TIMEOUT_MS), null)
    }

    /**
     * The running timer's notification (docs/PRD.md N1), which follows the cache's running entry
     * from process start, so every start, stop and edit from any surface reaches it.
     */
    val runningNotification: RunningNotificationSync by lazy {
        val store = RunningNotificationStore(this)
        val notifier = RunningNotifier(this)
        RunningNotificationSync(
            running = combine(cache.running, cache.projects, cache.tasks) { running, projects, tasks ->
                runningNotificationContent(running?.entry, projects, tasks)
            },
            loadShown = store::load,
            saveShown = store::save,
            post = notifier::post,
            remove = notifier::remove,
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    /**
     * The trust refusals of the signed-in session's requests, which every
     * [Session] reports and the navigation host prompts about. Accepting one
     * refreshes the account settings, the request that ran into it.
     */
    val sessionTrust: SessionTrustModel by lazy {
        SessionTrustModel(
            trust = ServerTrustStore(this),
            retry = { accountSettings.refresh() },
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    /**
     * Sign-out (docs/PRD.md S6). It forgets the server and the token with its Keystore key first,
     * so the app sends nothing new. Then it cancels the queue's background work and every call in
     * flight, waits for the refresh and the queue's drain to end, and clears the cache with the
     * queue, the running timer's notification, the account settings, the capabilities, the Timer
     * page's view, the trust decisions and the time the server last answered. MainActivity then drops its view models, and with them
     * the history's older days and what every other screen loaded. The theme and the sign-in
     * cooldowns stay: they belong to the phone and to a server, not to the account.
     */
    val signOut: SignOut by lazy {
        SignOut(
            steps = listOf(
                { Session(this).clear() },
                { BackgroundSend.cancel(this) },
                { SharedHttpClient.cancelAll() },
                { accountSettings.forget() },
                { sender.stop() },
                { cache.clear() },
                { runningNotification.forget() },
                { AccountSettingsStore(this).clear() },
                {
                    capabilities.forget()
                    CapabilitiesStore(this).clear()
                },
                {
                    timerView.forget()
                    TimerViewStore(this).clear()
                },
                { ServerTrustStore(this).clear() },
                { sessionTrust.forget() },
                { lastAnswer.forget() },
            ),
            io = Dispatchers.IO,
        )
    }

    /** The Settings page's sign-out button and its warning about unsent changes. */
    val signOutModel: SignOutModel by lazy {
        SignOutModel(unsentCount = { changes.unsentCount.first() }, clear = signOut::run, scope = MainScope())
    }

    override fun onCreate() {
        super.onCreate()
        // Every process, whatever starts it, sends requests only to the stored server.
        ConfiguredServer.readStoredFrom(ServerConfigStore(this)::load)
        // A plain http:// server gets requests only once the user has confirmed it.
        ConfiguredTrust.readFrom(ServerTrustStore(this))
        // Reads the cache off the main thread, and posts or removes nothing for the entry the
        // notification already followed, so opening the app never brings back a dismissed one.
        runningNotification.start()
    }
}

/** How long the status keeps following the queue after the last screen stops showing it, as through a rotation. */
private const val STATUS_STOP_TIMEOUT_MS = 5_000L
