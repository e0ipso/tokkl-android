package com.github.e0ipso.tokkl

import android.app.Application
import android.app.NotificationManager
import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.notification.RunningNotificationStore
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.notification.RunningNotifier
import com.github.e0ipso.tokkl.notification.runningNotificationContent
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseEndStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotification
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore
import com.github.e0ipso.tokkl.pomodoro.ServicePomodoroRinging
import com.github.e0ipso.tokkl.reminder.ReminderInput
import com.github.e0ipso.tokkl.reminder.ReminderNotifier
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.reminder.ReminderWork
import com.github.e0ipso.tokkl.session.AccountSettings
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
import com.github.e0ipso.tokkl.shortcuts.FavoriteShortcutSync
import com.github.e0ipso.tokkl.shortcuts.LauncherShortcuts
import com.github.e0ipso.tokkl.shortcuts.favoriteNames
import com.github.e0ipso.tokkl.shortcuts.favoriteShortcuts
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.DiscardedChangesStore
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.sync.LastAnswerStore
import com.github.e0ipso.tokkl.sync.NeedsAttentionModel
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.sync.shownSyncStatus
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.widget.TokklWidget
import com.github.e0ipso.tokkl.widget.WidgetState
import com.github.e0ipso.tokkl.widget.WidgetStore
import com.github.e0ipso.tokkl.widget.WidgetSync
import com.github.e0ipso.tokkl.widget.widgetState
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
     * and the cache takes the favorites, the [timerView] its view and [pomodoroDefaults] its
     * lengths from the settings' `GET /api/v9/me/preferences`. Then the [sender] sends the queue,
     * since the server has just answered.
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
                pomodoroDefaults.refreshed(preferences, keepsPreferences = flags.favorites)
                reminderSettings.refreshed(preferences, tokkl = flags.favorites)
                sender.send()
            },
        )
    }

    /**
     * Refreshes the account, and with it the running entry and the cache, each time the app comes
     * to the foreground and each time the network comes back while it is there. MainActivity
     * reports its starts, stops and network changes. It also tries queued changes independently
     * of the refresh, so an expired token still asks for sign-in when `/me` fails on opening the
     * app with a saved queue. Nothing refreshes on a timer.
     */
    val foregroundRefresh: ForegroundRefresh by lazy {
        ForegroundRefresh(
            refresh = {
                sender.send()
                accountSettings.refresh()
            },
            refreshOnReconnect = {
                sender.send()
                accountSettings.refreshOnReconnect()
            },
        )
    }

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

    /**
     * The lengths a Pomodoro session starts with, kept in Tokkl's four `pomodoro_*` length
     * preferences, or on the phone only where the server lacks them. Loaded on first use, which
     * reads a file of five values.
     */
    val pomodoroDefaults: PomodoroDefaultsSetting by lazy {
        val store = PomodoroDefaultsStore(this)
        PomodoroDefaultsSetting(
            initial = store.load(),
            save = store::save,
            client = Session(this)::client,
            keepsPreferences = { capabilities.state.value.favorites },
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    /**
     * The exact alarm at a running Pomodoro phase's planned end.
     * [PomodoroAlarmScheduler.permissionDenied] is what the Pomodoro page reads when that alarm
     * cannot be set. Loaded on first use, which reads the permission from the platform.
     */
    val pomodoroAlarms: PomodoroAlarmScheduler by lazy { AlarmClockPomodoroScheduler(this) }

    /**
     * The running Pomodoro. Loaded on first use, from the phase alarm or from the page. A queued
     * pomodoro asks for the same send as any other create.
     */
    internal val pomodoroRinging by lazy { ServicePomodoroRinging(this) }

    private val pomodoroSessionLock = Any()
    private var pomodoroSessionsForAccount = newPomodoroSessions()

    // Retained models keep their forgotten store. Install a fresh, lazy store only after
    // clearing the old account's cache, so late callbacks cannot find its session or queue.
    val pomodoroSessions: PomodoroSessionStore
        get() = synchronized(pomodoroSessionLock) { pomodoroSessionsForAccount.value }

    private fun newPomodoroSessions(): Lazy<PomodoroSessionStore> = lazy {
        PomodoroSessionStore.open(
            database = cacheDatabase,
            queue = changes,
            io = Dispatchers.IO,
            accountTime = { accountSettings.state.value?.time() },
            onQueued = {
                BackgroundSend.schedule(this)
                sender.send()
            },
            alarms = pomodoroAlarms,
            phaseEnds = PomodoroPhaseEndStore(this),
            ringing = pomodoroRinging,
        )
    }

    /** Shared reminder settings where both keys exist, account-local on older servers. */
    val reminderSettings: ReminderSetting by lazy {
        val store = ReminderStore(this)
        ReminderSetting(store.load(), store::save, Session(this)::client, MainScope(), Dispatchers.IO)
    }

    private suspend fun reminderInput(): ReminderInput = ReminderInput(
        cache.running.first()?.entry?.takeIf { Session(this).isSignedIn() },
        reminderSettings.state.value,
    )

    /** One reminder per running id/start, followed independently of any screen. */
    val reminder: ReminderSync by lazy {
        val store = ReminderStore(this)
        val notifier = ReminderNotifier(this)
        ReminderSync(
            latest = ::reminderInput,
            currentId = changes::currentId,
            load = store::loadTimer,
            save = store::saveTimer,
            schedule = { ReminderWork.schedule(this, it) },
            cancel = { ReminderWork.cancel(this) },
            post = notifier::post,
            remove = notifier::remove,
            stop = { changes.stop(it) },
            pause = { entryId ->
                val running = changes.timerControls.first() as? TimerControls.Running
                running != null && running.entry.id == entryId && changes.pause(running.target)
            },
        )
    }

    /** The file of the [cache] and the queue. It opens on the first query, which Room keeps off the main thread. */
    private val cacheDatabase: CacheDatabase by lazy { CacheDatabase.open(this) }

    /** The offline cache. */
    val cache: AccountCache by lazy { AccountCache(cacheDatabase, pomodoro = { pomodoroSessions }) }

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
                // Register the fallback before a fast drain can finish and cancel it.
                BackgroundSend.schedule(this)
                sender.send()
            },
            stopSpan = { start, at, oneStep ->
                accountSettings.state.value?.time()?.snapStop(start, at, oneStep) ?: SnappedSpan(start, maxOf(at, start))
            },
            pomodoro = { pomodoroSessions },
            capturedStopSpan = { start, at ->
                val time = accountSettings.state.value?.time()
                if (time == null) SnappedSpan(start, maxOf(at, start)) else time.snapCapturedStop(start, at)
            },
        )
    }

    /**
     * Sends the queued changes in order. Once it has sent some, the account refreshes with a read
     * that starts after the send, so a refresh that read the server before it cannot put the
     * cache back. A drain that empties the queue cancels the background send's waiting retry.
     * Changes it discards because the server no longer has their entries count in [discarded].
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
            onDiscarded = { count -> discarded.add(count) },
            serverStart = { start -> accountSettings.state.value?.time()?.snapInstant(start) ?: start },
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
     * How many queued changes the [sender] discarded because the server no longer had their
     * entries, for the notice that says so (docs/PRD.md O6). Loaded on the first discard or the
     * first frame of a signed-in screen, from a file of one value.
     */
    val discarded: DiscardedChanges by lazy {
        val store = DiscardedChangesStore(this)
        DiscardedChanges(initial = store.load(), save = store::save)
    }

    /**
     * The "needs attention" list of the changes the server refused (docs/PRD.md O6). Discarding
     * one refreshes the account, so the cache takes the server's entry back.
     */
    val needsAttention: NeedsAttentionModel by lazy {
        NeedsAttentionModel(
            held = changes.held,
            release = changes::sendAgain,
            drop = changes::discard,
            afterDiscard = { accountSettings.refresh() },
            scope = MainScope(),
        )
    }

    /**
     * The status element above the bottom bar: the queue's unsent count, the changes that need
     * attention, a refused token and the server's last answer, once changes have waited long
     * enough to show.
     */
    val syncStatus: StateFlow<SyncStatus?> by lazy {
        shownSyncStatus(
            changes.unsentCount,
            lastAnswer.state,
            accountSettings.state,
            needsAttention = changes.heldCount,
            signInNeeded = sender.signInNeeded,
        ).stateIn(MainScope(), SharingStarted.WhileSubscribed(STATUS_STOP_TIMEOUT_MS), null)
    }

    /**
     * Stores what a sign-in got from the server: the account settings first, then the server and
     * its token. If the session write fails, the app stays signed out and the next sign-in replaces
     * the settings. A sign-in again after a refused token then lifts the queue's pause, and the
     * changes that waited go out (docs/PRD.md O6). Blocks on disk I/O.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun storeSignIn(server: ServerUrl, token: String, settings: AccountSettings) {
        accountSettings.store(settings)
        Session(this).save(server, token)
        if (sender.signInNeeded.value) sender.resume()
    }

    /**
     * The running timer's notification (docs/PRD.md N1), which follows the cache's running entry
     * and the pause chain (T1) from process start, so every start, pause, resume, stop and edit
     * from any surface reaches it.
     */
    val runningNotification: RunningNotificationSync by lazy {
        val store = RunningNotificationStore(this)
        val notifier = RunningNotifier(this)
        RunningNotificationSync(
            running = combine(changes.timerControls, cache.projects, cache.tasks) { controls, projects, tasks ->
                runningNotificationContent(controls, projects, tasks)
            },
            loadShown = store::load,
            saveShown = store::save,
            currentId = changes::currentId,
            post = notifier::post,
            remove = notifier::remove,
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    /**
     * What the home screen widget shows (docs/PRD.md N3): the running entry or paused timer (T1)
     * and the favorites from the cache, the favorites capability and the account's default
     * workspace, all from the phone. The first use loads the account settings and the
     * capabilities from disk.
     */
    val widgetState: Flow<WidgetState> by lazy {
        val catalog = combine(cache.projects, cache.tasks, ::Pair)
        combine(accountSettings.state, capabilities.state, changes.timerControls, cache.favorites, catalog) { settings, flags, controls, favorites, (projects, tasks) ->
            widgetState(settings?.defaultWorkspaceId, controls, favorites, flags.favorites, projects, tasks)
        }.distinctUntilChanged()
    }

    /**
     * Updates the widget whenever what it shows changes, whichever surface changed it, from
     * process start. It builds [widgetState] off the main thread.
     */
    val widgetSync: WidgetSync by lazy {
        val store = WidgetStore(this)
        WidgetSync(
            state = { widgetState },
            loadShown = store::load,
            saveShown = store::save,
            update = { TokklWidget().updateAll(this) },
            scope = MainScope(),
            io = Dispatchers.IO,
        )
    }

    /**
     * The favorites as launcher shortcuts (docs/PRD.md N6), which follow the cached favorites and
     * the capabilities from process start. The capabilities load on the first collection, off the
     * main thread.
     */
    val favoriteShortcuts: FavoriteShortcutSync by lazy {
        val launcher = LauncherShortcuts(this)
        FavoriteShortcutSync(
            shortcuts = combine(flow { emitAll(capabilities.state) }, cache.favorites, cache.projects) { flags, favorites, projects ->
                favoriteShortcuts(flags.favorites, favorites, projects, launcher.limit, favoriteNames(this))
            },
            published = launcher::published,
            publish = launcher::publish,
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
     * queue and the Pomodoro session, its alarm and ringing, the running timer's notification, the launcher shortcuts, the account settings,
     * the capabilities, the Timer page's view, the Pomodoro defaults, the reminder and its settings, the trust decisions, the last answer and the count of
     * discarded changes. MainActivity then drops its view models, and with them
     * the history's older days and what every other screen loaded. The theme and the sign-in
     * cooldowns stay: they belong to the phone and to a server, not to the account.
     */
    val signOut: SignOut by lazy {
        SignOut(
            steps = listOf(
                { Session(this).clear() },
                { pomodoroSessions.forget() },
                // ID 3 is shared with the session notification, including a non-ringing form.
                // Keep this independent of service cleanup so a failed stop still removes it.
                { getSystemService(NotificationManager::class.java).cancel(PomodoroSessionNotification.ID) },
                { BackgroundSend.cancel(this) },
                { RunningCheck.cancelAndWait(this) },
                { SharedHttpClient.cancelAll() },
                { accountSettings.forget() },
                { sender.stop() },
                {
                    cache.clear()
                    synchronized(pomodoroSessionLock) { pomodoroSessionsForAccount = newPomodoroSessions() }
                },
                { reminder.forget() },
                {
                    reminderSettings.forget()
                    ReminderStore(this).clear()
                },
                { runningNotification.forget() },
                { favoriteShortcuts.forget() },
                { AccountSettingsStore(this).clear() },
                {
                    capabilities.forget()
                    CapabilitiesStore(this).clear()
                },
                {
                    timerView.forget()
                    TimerViewStore(this).clear()
                },
                {
                    pomodoroDefaults.forget()
                    PomodoroDefaultsStore(this).clear()
                },
                { ServerTrustStore(this).clear() },
                { sessionTrust.forget() },
                { lastAnswer.forget() },
                {
                    discarded.dismiss()
                    DiscardedChangesStore(this).clear()
                },
            ),
            io = Dispatchers.IO,
        )
    }

    /** The Settings page's sign-out button and its warning about unsent changes. */
    val signOutModel: SignOutModel by lazy {
        SignOutModel(unsentCount = { pomodoroSessions.unsavedChangeCount() }, clear = signOut::run, scope = MainScope())
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
        reminder.follow(
            combine(cache.running, flow { emitAll(reminderSettings.state) }) { running, preferences -> running to preferences },
            MainScope(), Dispatchers.IO,
        )
        widgetSync.start()
        favoriteShortcuts.start()
        // Check every 15 minutes while the cache holds a running entry.
        RunningCheck.follow(this, cache.running.map { it != null }, MainScope(), Dispatchers.IO)
    }
}

/** How long the status keeps following the queue after the last screen stops showing it, as through a rotation. */
private const val STATUS_STOP_TIMEOUT_MS = 5_000L
