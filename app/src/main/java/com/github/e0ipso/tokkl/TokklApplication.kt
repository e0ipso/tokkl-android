package com.github.e0ipso.tokkl

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.glance.appwidget.updateAll
import androidx.work.WorkManager
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
import com.github.e0ipso.tokkl.cache.serverTimerControls
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
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationSync
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotifier
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
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.timerstate.DiscardedPomodoro
import com.github.e0ipso.tokkl.timerstate.ElapsedTimerSyncClock
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.StreamEnd
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncService
import com.github.e0ipso.tokkl.timerstate.holdTimerEvents
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.widget.PomodoroWidget
import com.github.e0ipso.tokkl.widget.PomodoroWidgetFieldsStore
import com.github.e0ipso.tokkl.widget.PomodoroWidgetState
import com.github.e0ipso.tokkl.widget.TokklWidget
import com.github.e0ipso.tokkl.widget.WidgetState
import com.github.e0ipso.tokkl.widget.WidgetStore
import com.github.e0ipso.tokkl.widget.WidgetSync
import com.github.e0ipso.tokkl.widget.key
import com.github.e0ipso.tokkl.widget.pomodoroWidgetState
import com.github.e0ipso.tokkl.widget.pomodoroWidgetDeadlineChanges
import com.github.e0ipso.tokkl.widget.widgetState
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                val flags = capabilities.refresh(api, me, timerState)
                cache.refresh(api, me, preferences, flags)
                timerView.refreshed(preferences, keepsTimerView = flags.favorites)
                pomodoroDefaults.refreshed(preferences, keepsPreferences = flags.favorites)
                reminderSettings.refreshed(preferences, tokkl = flags.favorites)
                sender.send()
                // The first refresh after sign-in learns the capability, and any refresh can lose it.
                followTimerSync()
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
    val pomodoroAlarms: PomodoroAlarmScheduler get() = alarmClock

    /** [pomodoroAlarms], whose boot and update receiver also starts the timer sync service. */
    private val alarmClock: AlarmClockPomodoroScheduler by lazy {
        AlarmClockPomodoroScheduler(this, timerSyncStarts = { capabilities.state.value.timerState })
    }

    /** The ringing service at a Pomodoro phase's planned end, as [pomodoroAlarm] drives it. */
    internal val pomodoroRinging by lazy { ServicePomodoroRinging(this) }

    /**
     * The exact alarm and the ring of the server's Pomodoro session, which follow the held timer
     * state while the server offers the timer API (docs/PRD.md W7). Loaded at process start; its
     * first call loads the held state and the capabilities from disk.
     */
    internal val pomodoroAlarm: PomodoroPhaseAlarm by lazy {
        PomodoroPhaseAlarm(
            held = { timerState.state.value },
            available = { capabilities.state.value.timerState },
            alarms = alarmClock,
            ringing = pomodoroRinging,
            io = Dispatchers.IO,
        )
    }

    private val pomodoroNotificationLock = Any()
    private var pomodoroNotificationForAccount = newPomodoroNotification()

    internal val pomodoroSessionNotification: PomodoroSessionNotificationSync
        get() = synchronized(pomodoroNotificationLock) { pomodoroNotificationForAccount.value }

    private fun newPomodoroNotification(restore: Boolean = true): Lazy<PomodoroSessionNotificationSync> = lazy {
        val store = PomodoroSessionNotificationStore(this)
        val notifier = PomodoroSessionNotifier(this)
        PomodoroSessionNotificationSync(
            alarm = pomodoroAlarm,
            actions = timerActions,
            changes = combine(heldTimerState, flow { emitAll(capabilities.state) }, pomodoroRinging.state) { _, _, _ -> Unit },
            signedIn = { Session(this).isSignedIn() },
            serviceRunning = { pomodoroRinging.state.value.serviceRunning },
            load = { if (restore) store.load() else null }, save = store::save, post = notifier::post, remove = notifier::remove,
            scope = MainScope(), io = Dispatchers.IO,
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

    /**
     * One reminder per running id/start, followed independently of any screen. Its Pause shows,
     * and goes to the server, only where [timerControls] offer Pause for that entry.
     */
    val reminder: ReminderSync by lazy {
        val store = ReminderStore(this)
        val notifier = ReminderNotifier(this)
        suspend fun runningNow(entryId: Long) = (timerControls.first() as? TimerControls.Running)?.takeIf { it.entry.id == entryId }
        ReminderSync(
            latest = ::reminderInput,
            currentId = changes::currentId,
            load = store::loadTimer,
            save = store::saveTimer,
            schedule = { ReminderWork.schedule(this, it) },
            cancel = { ReminderWork.cancel(this) },
            post = { timer -> notifier.post(timer, pausable = runningNow(changes.currentId(timer.id))?.pausable == true) },
            remove = notifier::remove,
            stop = { changes.stop(it) },
            pause = { entryId ->
                runningNow(entryId)?.let { timerSurfaceActions.tap(TimerSurfaceAction.Pause, it.target) as? SurfaceTap.Request }
            },
        )
    }

    /** The file of the [cache] and the queue. It opens on the first query, which Room keeps off the main thread. */
    internal val cacheDatabase: CacheDatabase by lazy { CacheDatabase.open(this) }

    /** The offline cache. */
    val cache: AccountCache by lazy { AccountCache(cacheDatabase) }

    private val timerStateLock = Any()
    private var timerStateForAccount = newTimerState(restore = true)

    /** The account's [TimerStateSource], and the name its timer controls carry in their targets. */
    private class AccountTimerState(val source: TimerStateSource, val account: String)

    /** Counts the replacements of [timerState], so flows of its state follow the new account's source. */
    private val timerStateRenewals = MutableStateFlow(0)

    /**
     * The account's timer state from a Tokkl server with the timer API, which every timer request,
     * action and event feeds. Loaded on first use from the stored state, a small file. Sign-out
     * retires it and sign-in replaces it, so take it once per request or stream and give its
     * answer to the same source: an answer kept for a retired source changes nothing.
     */
    val timerState: TimerStateSource
        get() = accountTimerState().source

    private fun accountTimerState(): AccountTimerState = synchronized(timerStateLock) { timerStateForAccount.value }

    private fun newTimerState(restore: Boolean): Lazy<AccountTimerState> = lazy {
        val store = TimerStateStore(this)
        val account = store.account()
        val lastFields = PomodoroWidgetFieldsStore(this)
        val initial = if (restore) store.load() else null
        // A pre-widget held session is already sufficient to seed its idle Start fields.
        try {
            initial?.let { lastFields.remember(account, it.held) }
        } catch (_: IOException) {
            // A later accepted state retries, as the timer state's own store does.
        }
        AccountTimerState(
            TimerStateSource(
                initial = initial,
                save = { stored ->
                    store.save(stored)
                    // Under the holder's acceptance lock, for actions and SSE from any surface.
                    lastFields.remember(account, stored.held)
                },
                cache = cache,
                // The state does not carry a stopped entry's stop; the history read does.
                onRunningEnded = { accountSettings.refreshAfterChange() },
            ),
            // Made anew after each sign-in and sign-out, which clear the store.
            account = account,
        )
    }

    /**
     * Pause, Resume and Discard, and a Pomodoro session's Start, Next phase and End, sent to the
     * server at once and never queued. Each answer, refusals included, goes to [timerState].
     */
    val timerActions: TimerActions by lazy {
        TimerActions(client = Session(this)::client, source = { timerState }, io = Dispatchers.IO)
    }

    /**
     * What every timer surface shows and acts on (docs/PRD.md T1, N1, N3, N4, N7): the Timer page,
     * the full-screen timer, the running notification, the widget, the Quick Settings tile and the
     * reminder. It is the cache's running entry, with its queued Starts, Stops and edits, and the
     * pause chain of the server's timer state in [timerState], as [serverTimerControls] puts them
     * together, while the server offers the timer API, with targets that name the account. Each
     * value reads the cache and the held state under the holder's lock, so a state never shows
     * apart from the running entry it wrote to the cache. Reads the disk off the main thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val timerControls: Flow<TimerControls> by lazy {
        val dao = cacheDatabase.changes()
        val heldStates = timerStateRenewals.flatMapLatest { flow { emitAll(timerState.state) } }
        combine(
            cacheDatabase.invalidationTracker.createFlow("time_entries", "pending_changes"),
            heldStates,
            flow { emitAll(capabilities.state) },
        ) { _, _, flags ->
            val account = accountTimerState()
            account.source.withState { held -> serverTimerControls(dao.latestRunning(), held, flags.timerState, account.account) }
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    /**
     * The Pause, Resume and Stop of the running notification, the widget, the Quick Settings tile
     * and its long press, and the reminder's Pause, checked against [timerControls] at each tap.
     * Stop of a running entry goes to the [changes] queue; the rest go to the server through
     * [timerActions].
     */
    val timerSurfaceActions: TimerSurfaceActions by lazy {
        TimerSurfaceActions(
            controls = { timerControls.first() },
            account = { accountTimerState().account },
            changes = changes,
            server = timerActions,
            sendQueued = { sender.sendAndWait() },
        )
    }

    /**
     * The signed-in account's held timer state, following [timerState] across sign-ins, or null
     * without one. Loads the stored state off the main thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val heldTimerState: Flow<HeldTimerState?> by lazy {
        timerStateRenewals.flatMapLatest { flow { emitAll(timerState.state) } }.flowOn(Dispatchers.IO)
    }

    /**
     * [heldTimerState] while the account's server offers the timer API, and null otherwise: what
     * the full-screen alarm shows. Loads the stored state and the capabilities off the main thread.
     */
    internal val heldSession: Flow<HeldTimerState?> by lazy {
        combine(heldTimerState, flow { emitAll(capabilities.state) }) { held, flags -> held.takeIf { flags.timerState } }
            .flowOn(Dispatchers.IO)
    }

    /**
     * The too-short pomodoro of the signed-in account's timer state that no screen has shown yet,
     * following [timerState] across sign-ins. Loads the stored state off the main thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val timerDiscardNotice: Flow<DiscardedPomodoro?> by lazy {
        timerStateRenewals.flatMapLatest { flow { emitAll(timerState.discardNotice) } }.flowOn(Dispatchers.IO)
    }

    /** [TimerSyncService] while it runs in the foreground. The service sets it. */
    @Volatile
    internal var timerSyncService: TimerSyncService? = null

    /**
     * The one owner of the timer event stream, which [TimerSyncService] runs for the account's
     * [timerState]. Each connection takes the signed-in client and feeds the source it was opened
     * for, so a stream of a retired account changes nothing. A refused credential pauses the queue
     * as the queue's own refusal does, which shows the existing prompt to sign in again, and stops
     * the service, as a stream with no account does.
     */
    val timerSync: TimerSync<TimerStateSource> by lazy {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        TimerSync(
            clock = ElapsedTimerSyncClock(scope),
            open = { source, link ->
                scope.launch(Dispatchers.IO) { link.ended(holdTimerEvents(Session(this@TokklApplication).client(), source, link)) }
            },
            stopped = { source, end ->
                if (end == StreamEnd.Refused && source === timerState) sender.signInRefused()
                stopTimerSyncService()
            },
        )
    }

    /**
     * Whether the timer event stream should run: signed in to a server with the timer API, whose
     * token the server has not refused. Reads the disk.
     */
    internal fun timerSyncWanted(): Boolean =
        capabilities.state.value.timerState && !sender.signInNeeded.value && Session(this).isSignedIn()

    /**
     * Starts [TimerSyncService], or points the running one at the account signed in now, while
     * [timerSyncWanted], and stops the stream and the service otherwise. App open, every account
     * refresh, sign-in and the boot and update receiver call it. A start the platform refuses, as
     * in the background, is dropped: the next app open starts the service, and an exact alarm
     * already set still rings. Blocks on disk I/O.
     */
    fun followTimerSync() {
        alarmClock.watchTimerSync()
        val service = timerSyncService
        if (!timerSyncWanted()) {
            timerSync.stop()
            stopTimerSyncService()
        } else if (service != null) {
            timerSync.run(timerState) {
                timerSyncService === service && capabilities.state.value.timerState && !sender.signInNeeded.value
            }
        } else {
            TimerSyncService.start(this)
        }
    }

    /** Stops [TimerSyncService] unless a newer start is on its way, which then decides for itself. */
    private fun stopTimerSyncService() {
        Handler(Looper.getMainLooper()).post { timerSyncService?.stopLatest() }
    }

    /**
     * Retires the timer state source and clears its store, then installs a new, empty source for
     * the account signing in. Blocks on disk I/O.
     */
    @Throws(IOException::class)
    internal fun renewTimerState() {
        timerState.retire()
        PomodoroWidgetFieldsStore(this).clear()
        TimerStateStore(this).clear()
        synchronized(timerStateLock) { timerStateForAccount = newTimerState(restore = false) }
        timerStateRenewals.update { it + 1 }
    }

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
     * Stores what a sign-in got from the server: the account settings first, then a new, empty
     * timer state, then the server and its token. If the session write fails, the app stays
     * signed out and the next sign-in replaces the settings. A sign-in again after a refused token
     * then lifts the queue's pause, and the changes that waited go out (docs/PRD.md O6). Blocks on
     * disk I/O.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun storeSignIn(server: ServerUrl, token: String, settings: AccountSettings) {
        accountSettings.store(settings)
        renewTimerState()
        Session(this).save(server, token)
        pomodoroSessionNotification.start()
        if (sender.signInNeeded.value) sender.resume()
        // Signing in again over a refused token keeps the capability: the stream restarts for the
        // new source now. A first sign-in starts it once its refresh has learned the capability.
        followTimerSync()
    }

    /**
     * The running timer's notification (docs/PRD.md N1), which follows [timerControls], the cache's
     * running entry and the server's pause chain (T1), from process start, so every start, pause,
     * resume, stop and edit from any surface or device reaches it.
     */
    val runningNotification: RunningNotificationSync by lazy {
        val store = RunningNotificationStore(this)
        val notifier = RunningNotifier(this)
        RunningNotificationSync(
            running = combine(timerControls, cache.projects, cache.tasks) { controls, projects, tasks ->
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
     * of [timerControls], the favorites from the cache, the favorites capability and the account's
     * default workspace, all from the phone. Cached history and ID mappings share the Timer
     * page's day rule. App opens and the existing running check reread the day even offline or
     * when an unchanged current entry produces no Room write. No widget work is scheduled.
     */
    /** Clock read on widget events; injectable for account-midnight integration tests. */
    @Volatile internal var widgetClock: Clock = Clock.systemUTC()

    val widgetState: Flow<WidgetState> by lazy {
        val openings = callbackFlow {
            val callbacks = object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) { trySend(Unit) }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
            registerActivityLifecycleCallbacks(callbacks)
            trySend(Unit)
            awaitClose { unregisterActivityLifecycleCallbacks(callbacks) }
        }
        val refreshes = merge(
            openings,
            WorkManager.getInstance(this).getWorkInfosForUniqueWorkFlow(RunningCheck.WORK_NAME).map { Unit },
        )
        val history = combine(
            cache.timeEntries,
            cacheDatabase.invalidationTracker.createFlow("entry_ids"),
            refreshes,
        ) { entries, _, _ ->
            val dao = cacheDatabase.changes()
            val mappings = entries.filter { it.id < 0 }.mapNotNull { entry ->
                dao.serverId(entry.id)?.let { entry.id to it }
            }.toMap()
            entries to mappings
        }
        val catalog = combine(cache.projects, cache.tasks, history) { projects, tasks, history -> Triple(projects, tasks, history) }
        combine(accountSettings.state, capabilities.state, timerControls, cache.favorites, catalog) { settings, flags, controls, favorites, (projects, tasks, history) ->
            val (entries, mappings) = history
            widgetState(
                settings?.defaultWorkspaceId, controls, favorites, flags.favorites, projects, tasks,
                entries = entries, settings = settings, now = widgetClock.instant(), serverIdOf = mappings,
            )
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
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
     * The Pomodoro widget's held session and idle defaults. Read the current account under its
     * holder lock, rather than combining a previous account's emitted state with new settings.
     * The ringing service and a single in-process phase-end wait trigger redraws. The latter
     * also works with denied exact alarms. No ticking job or idle work is added.
     */
    val pomodoroWidgetState: Flow<PomodoroWidgetState> by lazy {
        val store = PomodoroWidgetFieldsStore(this)
        val deadlines = pomodoroWidgetDeadlineChanges(heldTimerState.map { held ->
            held?.let { it.state.pomodoro?.phase?.plannedEnd?.minus(it.clockOffset) }
        })
        val catalog = combine(cache.projects, cache.tasks, pomodoroRinging.state, deadlines) { projects, tasks, ring, _ ->
            Triple(projects, tasks, ring.ring?.phase)
        }
        combine(
            heldTimerState,
            flow { emitAll(capabilities.state) },
            flow { emitAll(accountSettings.state) },
            flow { emitAll(pomodoroDefaults.defaults) },
            catalog,
        ) { _, flags, settings, defaults, (projects, tasks, ringing) ->
            val owner = accountTimerState()
            owner.source.withState { held ->
                pomodoroWidgetState(
                    workspaceId = settings?.defaultWorkspaceId,
                    account = owner.account.takeIf { Session(this).isSignedIn() },
                    available = flags.timerState,
                    held = held,
                    defaults = defaults,
                    remembered = store.load(owner.account),
                    projects = projects,
                    tasks = tasks,
                    deviceNow = Instant.now(),
                    ringing = ringing,
                )
            }
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    /** Phase 2 supplies the provider's redraw; its key is independent of the Timer widget. */
    fun pomodoroWidgetSync(update: suspend () -> Unit): WidgetSync {
        val store = WidgetStore(this, keyName = WidgetStore.POMODORO_KEY)
        return WidgetSync(
            keys = flow { emitAll(pomodoroWidgetState.map { it.key() }) },
            loadShown = store::load,
            saveShown = store::save,
            update = update,
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
     * so the app sends nothing new, and retires the timer state, so a late answer cannot write it
     * or the cache again. It stops the timer event stream, its reconnects and its service. Then it
     * cancels the queue's background work and every call in flight,
     * waits for the refresh and the queue's drain to end, and clears the timer state's store and
     * the cache with the queue, the Pomodoro alarm, its ringing and its notification, the running timer's notification, the launcher shortcuts, the account settings,
     * the capabilities, the Timer page's view, the Pomodoro defaults, the reminder and its settings, the trust decisions, the last answer and the count of
     * discarded changes. MainActivity then drops its view models, and with them
     * the history's older days and what every other screen loaded. The theme and the sign-in
     * cooldowns stay: they belong to the phone and to a server, not to the account.
     */
    val signOut: SignOut by lazy {
        SignOut(
            steps = listOf(
                { Session(this).clear() },
                { timerState.retire() },
                // With the held state retired, nothing arms or rings the account's phase again.
                { pomodoroAlarm.release() },
                // Before the stores clear, so no stream or reconnect of this account outlives them.
                {
                    timerSync.stop()
                    stopTimerSyncService()
                },
                { pomodoroSessionNotification.forget() },
                // ID 3 is shared with the session notification, including a non-ringing form.
                // Keep this independent of service cleanup so a failed stop still removes it.
                { getSystemService(NotificationManager::class.java).cancel(PomodoroSessionNotifier.NOTIFICATION_ID) },
                { BackgroundSend.cancel(this) },
                { RunningCheck.cancelAndWait(this) },
                { SharedHttpClient.cancelAll() },
                { accountSettings.forget() },
                { sender.stop() },
                { TimerStateStore(this).clear() },
                { PomodoroWidgetFieldsStore(this).clear() },
                {
                    cache.clear()
                    synchronized(pomodoroNotificationLock) {
                        pomodoroNotificationForAccount = newPomodoroNotification(restore = false)
                    }
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
                    alarmClock.watchTimerSync()
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
        // Arms, moves and drops the phase alarm and its ring on every held state and capability
        // change, from the stored state at process start on, also with the app in the background.
        MainScope().launch(Dispatchers.IO) {
            combine(heldTimerState, flow { emitAll(capabilities.state) }) { _, _ -> Unit }.collect { pomodoroAlarm.follow() }
        }
        pomodoroSessionNotification.start()
        reminder.follow(
            combine(cache.running, flow { emitAll(reminderSettings.state) }) { running, preferences -> running to preferences },
            MainScope(), Dispatchers.IO,
        )
        widgetSync.start()
        pomodoroWidgetSync { PomodoroWidget().updateAll(this) }.start()
        favoriteShortcuts.start()
        // Check every 15 minutes while the cache holds a running entry.
        RunningCheck.follow(this, cache.running.map { it != null }, MainScope(), Dispatchers.IO)
    }
}

/** How long the status keeps following the queue after the last screen stops showing it, as through a rotation. */
private const val STATUS_STOP_TIMEOUT_MS = 5_000L
