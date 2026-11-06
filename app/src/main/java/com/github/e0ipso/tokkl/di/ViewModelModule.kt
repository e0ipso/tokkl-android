package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.navigation.EntryFormRequest
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmViewModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.FilterOptions
import com.github.e0ipso.tokkl.reports.ReportExportController
import com.github.e0ipso.tokkl.reports.ReportExportFiles
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.reports.reportFilterOptions
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AppEvents
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.signin.StoredSignInAccount
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TapTrace
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.trash.TrashViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.ParametersHolder
import org.koin.dsl.module

/**
 * The screens' view models. The navigation keeps each in its back stack entry's store, or in the
 * signed-in session's, until the entry closes or a sign-out, so they outlive a configuration
 * change and turning the phone loads nothing again. Some take parameters from the screen that
 * makes them: sign-in the account to sign in again to, the entry form what it opens, and the
 * Timer page's history and calendar the entry actions' deletes and restores.
 */
internal val viewModelModule = module {
    /** The system trace of each Start and Stop tap on the Timer page. */
    single { TapTrace.System }

    viewModel { params ->
        SignInViewModel(
            // Runs on the IO thread, since the stores touch the disk.
            signIn = SignIn(
                session = get<SessionWriter>(),
                signedInAccount = StoredSignInAccount(get(), get())::current,
            ),
            trust = get<ServerTrustStore>(),
            cooldowns = get<SignInCooldownStore>(),
            scope = viewModelCoroutineScope(),
            io = io(),
            clock = System::currentTimeMillis,
            // A sign-in loads the account settings and the cache, as coming to the foreground does.
            onSignedIn = get<AccountRefresh>()::refresh,
        ).also { model -> params.getOrNull<SignInAgain>()?.let(model::signInAgain) }
    }
    viewModel {
        val session = get<Session>()
        val accountSettings = get<AccountSettingsSource>()
        val capabilities = get<CapabilitiesSource>()
        val cache = get<AccountCache>()
        // The exporter shares the view model's scope, so clearing the view model cancels it too.
        val scope = viewModelCoroutineScope()
        ReportsViewModel(
            accountSettings = accountSettings.state,
            client = session,
            exportCapabilities = capabilities.state,
            exporter = ReportExportController(
                client = session,
                files = ReportExportFiles(File(androidContext().cacheDir, ReportExportFiles.DIRECTORY)),
                account = Session.exports,
                csvMissing = capabilities::csvMissing,
                scope = scope,
                io = io(),
            ),
            scope = scope,
            io = io(),
            // Only while the screen reads them.
            filterOptions = reportFilterOptions(cache, accountSettings.state, capabilities.state)
                .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FilterOptions.NONE),
            appEvents = get<AppEvents>().events,
        )
    }
    viewModel { params ->
        val cache = get<AccountCache>()
        val changes = get<ChangeQueue>()
        HistoryViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            client = get<Session>(),
            scope = viewModelCoroutineScope(),
            io = io(),
            currentId = changes::currentId,
            overrides = params.overrides(),
            appEvents = get<AppEvents>().events,
        )
    }
    viewModel {
        val capabilities = get<CapabilitiesSource>()
        EntryActionsViewModel(
            entries = get<AccountCache>().entries,
            changes = get<ChangeQueue>(),
            undoDelete = get<ChangeSender>()::undoDelete,
            canRestore = capabilities::offersRestore,
            accountSettings = get<AccountSettingsSource>().state,
            scope = viewModelCoroutineScope(),
        )
    }
    viewModel { params ->
        val cache = get<AccountCache>()
        val capabilities = get<CapabilitiesSource>()
        CalendarViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            client = get<Session>(),
            scope = viewModelCoroutineScope(),
            io = io(),
            // The capabilities load from disk on first read, off the main thread.
            allDayOffered = capabilities.allDayOffers(),
            overrides = params.overrides(),
            appEvents = get<AppEvents>().events,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val cache = get<AccountCache>()
        ProjectsViewModel(
            client = get<Session>(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedProjects = cache.projects,
            cachedClients = cache.clients,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        ClientsViewModel(
            client = get<Session>(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedClients = get<AccountCache>().clients,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        TagsViewModel(
            client = get<Session>(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedTags = get<AccountCache>().tags,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        TrashViewModel(
            client = get<Session>(),
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            trashMissing = get<CapabilitiesSource>()::trashMissing,
            scope = viewModelCoroutineScope(),
            io = io(),
            settings = accountSettings.state,
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val sender = get<ChangeSender>()
        TimerViewModel(
            controls = get<TimerControlsSource>().controls,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = viewModelCoroutineScope(),
            trace = get(),
            pauseChain = get<TimerActions>(),
            sendQueued = sender::sendAndWait,
            discardNotices = get<HeldTimerFlows>().discardNotice,
            takeDiscardNotice = get<HeldTimerFlows>()::takeDiscardNotice,
            timerApi = get<CapabilitiesSource>().state.map { it.timerState },
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        FavoritesViewModel(
            favorites = cache.favorites,
            projects = cache.projects,
            tasks = cache.tasks,
            available = get<CapabilitiesSource>().state.map { it.favorites },
            save = FavoritesServer(client = get<Session>(), cache = cache)::save,
            scope = viewModelCoroutineScope(),
            io = io(),
        )
    }
    viewModel { params ->
        val cache = get<AccountCache>()
        EntryFormViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = viewModelCoroutineScope(),
            allDay = get<CapabilitiesSource>().state.map { it.allDay == true },
        ).also { form -> params.getOrNull<EntryFormRequest>()?.openIn(form) }
    }
    viewModel {
        val changes = get<ChangeQueue>()
        val sender = get<ChangeSender>()
        val alarms = get<PomodoroAlarmScheduler>()
        PomodoroViewModel(
            held = get<HeldTimerFlows>().held,
            controls = get<TimerControlsSource>().controls,
            actions = get<TimerActions>(),
            changes = changes,
            defaults = get<PomodoroDefaultsSetting>().defaults,
            settings = get<AccountSettingsSource>().state,
            unsent = changes.unsentCount,
            refused = changes.held,
            scope = viewModelCoroutineScope(),
            sendQueued = sender::sendAndWait,
            alarmProbe = alarms::permissionDenied,
        )
    }
    viewModel {
        PomodoroAlarmViewModel(
            held = get<HeldTimerFlows>().session,
            actions = get<TimerActions>(),
            scope = viewModelCoroutineScope(),
        )
    }
}

/** The entry actions' deletes and restores, which the Timer page passes, or none. */
private fun ParametersHolder.overrides(): Flow<Map<Long, CachedTimeEntry?>> =
    getOrNull<StateFlow<Map<Long, CachedTimeEntry?>>>() ?: emptyFlow()

/**
 * The scope a view model gets, as `viewModelScope` makes it by default: the main thread, and a
 * failed child does not cancel the others. The view model cancels it when it is cleared.
 */
private fun viewModelCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** How long the cache's flows stay collected after the screen stops reading them, as across a rotation. */
private const val STOP_TIMEOUT_MS = 5_000L
