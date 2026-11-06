package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.ViewModelsAfterSignOuts
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.TimerControlsSource
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmViewModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.FilterOptions
import com.github.e0ipso.tokkl.reports.ReportExportController
import com.github.e0ipso.tokkl.reports.ReportExportFiles
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TapTrace
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.trash.TrashViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The screens' view models. MainActivity keeps them in its store until a sign-out, so they outlive
 * a configuration change and turning the phone loads nothing again.
 */
internal val viewModelModule = module {
    viewModel {
        val servers = get<ServerConfigStore>()
        val accountSettings = get<AccountSettingsStore>()
        SignInViewModel(
            // Runs on the IO thread, since the stores touch the disk.
            signIn = SignIn(
                session = get<SessionWriter>(),
                // Recheck persisted identity on every submit, including a form restored after the
                // process died. The in-memory form's reauthentication marker may have been lost.
                signedInAccount = {
                    servers.load()?.let { server ->
                        SignInAgain(server, accountSettings.load()?.userId)
                    }
                },
            ),
            trust = get<ServerTrustStore>(),
            cooldowns = get<SignInCooldownStore>(),
            scope = viewModelCoroutineScope(),
            io = io(),
            clock = System::currentTimeMillis,
        )
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
            client = session::client,
            exportCapabilities = capabilities.state,
            exporter = ReportExportController(
                client = session::client,
                files = ReportExportFiles(File(androidContext().cacheDir, ReportExportFiles.DIRECTORY)),
                account = Session.exports,
                csvMissing = capabilities::csvMissing,
                scope = scope,
                io = io(),
            ),
            scope = scope,
            io = io(),
            // From the Room cache, never from the server, and only while the screen reads them.
            filterOptions = combine(
                cache.projects,
                cache.clients,
                cache.tags,
                accountSettings.state,
                capabilities.state,
            ) { projects, clients, tags, settings, capabilities ->
                FilterOptions.of(projects, clients, tags, settings?.defaultWorkspaceId, negation = capabilities.negatedFilters)
            }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FilterOptions.NONE),
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val changes = get<ChangeQueue>()
        HistoryViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            client = get<Session>()::client,
            scope = viewModelCoroutineScope(),
            io = io(),
            currentId = changes::currentId,
        )
    }
    viewModel {
        val capabilities = get<CapabilitiesSource>()
        EntryActionsViewModel(
            entries = get<AccountCache>().entries,
            changes = get<ChangeQueue>(),
            undoDelete = get<ChangeSender>()::undoDelete,
            canRestore = { capabilities.state.value.restore },
            accountSettings = get<AccountSettingsSource>().state,
            scope = viewModelCoroutineScope(),
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val capabilities = inject<CapabilitiesSource>()
        CalendarViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            client = get<Session>()::client,
            scope = viewModelCoroutineScope(),
            io = io(),
            // The capabilities load from disk on first read, off the main thread.
            allDayOffered = flow { emitAll(capabilities.value.state) }.map { it.allDay == true }.distinctUntilChanged(),
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val cache = get<AccountCache>()
        ProjectsViewModel(
            client = get<Session>()::client,
            workspaceId = { accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { accountSettings.refreshAfterChange() },
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedProjects = cache.projects,
            cachedClients = cache.clients,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        ClientsViewModel(
            client = get<Session>()::client,
            workspaceId = { accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { accountSettings.refreshAfterChange() },
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedClients = get<AccountCache>().clients,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        TagsViewModel(
            client = get<Session>()::client,
            workspaceId = { accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { accountSettings.refreshAfterChange() },
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedTags = get<AccountCache>().tags,
        )
    }
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        TrashViewModel(
            client = get<Session>()::client,
            refreshCache = { accountSettings.refreshAfterChange() },
            trashMissing = get<CapabilitiesSource>()::trashMissing,
            scope = viewModelCoroutineScope(),
            io = io(),
            settings = accountSettings.state,
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val sender = get<ChangeSender>()
        val timerStates = get<AccountTimerStates>()
        val io = io()
        TimerViewModel(
            controls = get<TimerControlsSource>().controls,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = viewModelCoroutineScope(),
            trace = TapTrace.System,
            pauseChain = get<TimerActions>(),
            sendQueued = { sender.sendAndWait() },
            discardNotices = get<HeldTimerFlows>().discardNotice,
            // Records the notice as shown in the account's timer state store.
            takeDiscardNotice = { withContext(io) { timerStates.source.takeDiscardNotice() } },
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
            save = FavoritesServer(client = get<Session>()::client, cache = cache)::save,
            scope = viewModelCoroutineScope(),
            io = io(),
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        EntryFormViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = viewModelCoroutineScope(),
            allDay = get<CapabilitiesSource>().state.map { it.allDay == true },
        )
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
            sendQueued = { sender.sendAndWait() },
            alarmProbe = { alarms.permissionDenied },
        )
    }
    viewModel {
        PomodoroAlarmViewModel(
            held = get<HeldTimerFlows>().session,
            actions = get<TimerActions>(),
            scope = viewModelCoroutineScope(),
        )
    }
    viewModelOf(::ViewModelsAfterSignOuts)
}

/**
 * The scope a view model gets, as `viewModelScope` makes it by default: the main thread, and a
 * failed child does not cancel the others. The view model cancels it when it is cleared.
 */
private fun viewModelCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** How long the cache's flows stay collected after the screen stops reading them, as across a rotation. */
private const val STOP_TIMEOUT_MS = 5_000L
