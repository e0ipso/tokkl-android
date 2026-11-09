package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.DeleteUndoer
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.calendar.ServerWeeks
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.entry.openIn
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.history.OlderPages
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.navigation.EntryFormRequest
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.picker.pickerSource
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.ParametersHolder
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The Timer page with its history and calendar, the entry form and the timer templates: the Timer
 * view setting, the view models and the screens. The history and the calendar take the entry
 * actions' deletes and restores from the Timer page, and the entry form what it opens.
 */
val timerFeatureModule = module {
    single { TimerViewStore(androidContext()) }

    // The Timer page's List or Calendar choice, kept in Tokkl's `timerView` preference. It loads
    // at process start from a file of two values.
    single {
        TimerViewSetting(
            store = get<TimerViewStore>(),
            client = get<Session>(),
            keepsTimerView = get<CapabilitiesSource>()::keepsPreferences,
            scope = get(),
        )
    } binds arrayOf(AccountDataOwner::class, StartupLoad::class)

    // The system trace of each Start and Stop tap on the Timer page.
    single { TapTrace.System }

    // The history's and the calendar's days outside the cache, from the server.
    single { OlderPages(get<Session>()) }
    single { ServerWeeks(get<Session>()) }

    viewModel { params ->
        val cache = get<AccountCache>()
        val changes = get<ChangeQueue>()
        HistoryViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            olderPages = get(),
            scope = viewModelCoroutineScope(),
            io = io(),
            clock = get(),
            ids = changes,
            overrides = params.overrides(),
            app = get(),
        )
    }
    viewModel {
        val capabilities = get<CapabilitiesSource>()
        EntryActionsViewModel(
            entries = get<AccountCache>().entries,
            changes = get<ChangeQueue>(),
            undoDelete = get<DeleteUndoer>()::undoDelete,
            // A tap on a screen, which shows once the capabilities have loaded.
            canRestore = { capabilities.state.value.restore },
            accountSettings = get<AccountSettingsSource>().state,
            scope = viewModelCoroutineScope(),
            clock = get(),
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
            weeks = get(),
            scope = viewModelCoroutineScope(),
            io = io(),
            clock = get(),
            // From the capabilities once they have loaded.
            allDayOffered = capabilities.allDayOffers(),
            overrides = params.overrides(),
            app = get(),
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val sender = get<ChangeSender>()
        TimerViewModel(
            controls = get<AccountTimerStates>().controls,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = viewModelCoroutineScope(),
            clock = get(),
            trace = get(),
            pauseChain = get<TimerActions>(),
            sendQueued = sender::sendQueued,
            discards = get<AccountTimerStates>(),
            timerApi = get<CapabilitiesSource>().state.map { it.timerState },
            view = get<TimerViewSetting>().view,
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val scope = viewModelCoroutineScope()
        FavoritesViewModel(
            favorites = cache.favorites,
            projects = cache.projects,
            tasks = cache.tasks,
            available = get<CapabilitiesSource>().state.map { it.favorites },
            store = FavoritesServer(client = get<Session>(), cache = cache)::save,
            scope = scope,
            io = io(),
            picker = pickerSource(cache.projects, cache.clients, cache.tasks, cache.tags),
            offline = get<DefaultNetwork>().offlineIn(scope),
            workspaceId = get<AccountSettingsSource>().state.map { it?.defaultWorkspaceId },
        )
    }
    viewModel { params ->
        val cache = get<AccountCache>()
        val scope = viewModelCoroutineScope()
        EntryFormViewModel(
            entries = cache.entries,
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = scope,
            clock = get(),
            allDay = get<CapabilitiesSource>().state.map { it.allDay == true },
            picker = pickerSource(cache.projects, cache.clients, cache.tasks, cache.tags),
            offline = get<DefaultNetwork>().offlineIn(scope),
        ).also { form -> params.getOrNull<EntryFormRequest>()?.openIn(form) }
    }
    viewModel {
        val cache = get<AccountCache>()
        val scope = viewModelCoroutineScope()
        TimerPageViewModel(
            views = get(),
            entries = cache.timeEntries,
            picker = pickerSource(cache.projects, cache.clients, cache.tasks, cache.tags),
            settings = get<AccountSettingsSource>().state,
            offline = get<DefaultNetwork>().offlineIn(scope),
            clock = get(),
            scope = scope,
        )
    }

    single { TimerEntries } bind FeatureEntries::class

    // The bar's running dot on Timer while an entry runs, by the cache's word.
    single { TimerBadge(get()) } bind PageBadge::class
}

/** The entry actions' deletes and restores, which the Timer page passes, or none. */
private fun ParametersHolder.overrides(): Flow<Map<EntryId, TimeEntry?>> =
    getOrNull<StateFlow<Map<EntryId, TimeEntry?>>>() ?: emptyFlow()
