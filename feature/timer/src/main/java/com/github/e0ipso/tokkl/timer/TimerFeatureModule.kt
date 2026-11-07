package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.entry.openIn
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.navigation.EntryFormRequest
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.picker.offlineIn
import com.github.e0ipso.tokkl.picker.pickerSource
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AppEvents
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
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

    // The Timer page's List or Calendar choice, kept in Tokkl's `timerView` preference. The first
    // read loads it from a file of two values: the Timer page's first frame on the main thread.
    single {
        TimerViewSetting(
            store = get<TimerViewStore>(),
            client = get<Session>(),
            keepsTimerView = get<CapabilitiesSource>()::keepsPreferences,
            scope = get(),
            io = io(),
        )
    } binds arrayOf(AccountDataOwner::class, TimerViewChoice::class)

    // The system trace of each Start and Stop tap on the Timer page.
    single { TapTrace.System }

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
        val scope = viewModelCoroutineScope()
        FavoritesViewModel(
            favorites = cache.favorites,
            projects = cache.projects,
            tasks = cache.tasks,
            available = get<CapabilitiesSource>().state.map { it.favorites },
            save = FavoritesServer(client = get<Session>(), cache = cache)::save,
            scope = scope,
            io = io(),
            picker = cache.pickerSource(),
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
            allDay = get<CapabilitiesSource>().state.map { it.allDay == true },
            picker = cache.pickerSource(),
            offline = get<DefaultNetwork>().offlineIn(scope),
        ).also { form -> params.getOrNull<EntryFormRequest>()?.openIn(form) }
    }
    viewModel {
        val cache = get<AccountCache>()
        val scope = viewModelCoroutineScope()
        TimerPageViewModel(
            views = get(),
            entries = cache.timeEntries,
            picker = cache.pickerSource(),
            settings = get<AccountSettingsSource>().state,
            offline = get<DefaultNetwork>().offlineIn(scope),
            clock = get(),
            scope = scope,
        )
    }

    single { TimerEntries } bind FeatureEntries::class
}

/** The entry actions' deletes and restores, which the Timer page passes, or none. */
private fun ParametersHolder.overrides(): Flow<Map<Long, CachedTimeEntry?>> =
    getOrNull<StateFlow<Map<Long, CachedTimeEntry?>>>() ?: emptyFlow()
