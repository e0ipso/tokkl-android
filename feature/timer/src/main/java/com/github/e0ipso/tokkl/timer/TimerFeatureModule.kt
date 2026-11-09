package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.DeleteUndoer
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.entries.OlderPages
import com.github.e0ipso.tokkl.entries.ServerWeeks
import com.github.e0ipso.tokkl.entries.SessionEntries
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.entry.openIn
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.favorites.FavoritesStore
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.navigation.EntryFormRequest
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.picker.pickerSource
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.computation
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerActions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The Timer page with its history and calendar, the entry form and the timer templates: the Timer
 * view setting, the session's entries, the view models and the screens. The history, the
 * calendar, the entry actions and the entry form share the session's [SessionEntries], and the
 * entry form takes what its screen opens.
 */
val timerFeatureModule = module {
    single { TimerViewStore(androidContext(), io()) }

    // The Timer page's List or Calendar choice, kept in Tokkl's `timerView` preference. It loads
    // at process start from a file of two values.
    single {
        TimerViewSetting(
            store = get<TimerViewStore>(),
            client = get<Session>(),
            capabilities = get(),
            account = get(),
            scope = get(),
            io = io(),
        )
    } binds arrayOf(AccountDataOwner::class, StartupLoad::class)

    // The system trace of each Start and Stop tap on the Timer page.
    single { TapTrace.System }

    single<FavoritesStore> {
        FavoritesServer(
            client = get<Session>(),
            account = get(),
            replaceFavorites = get<AccountCache>()::replaceFavorites,
            scope = get(),
        )
    }

    // The history's and the calendar's days outside the cache, from the server.
    single { OlderPages(get<Session>()) }
    single { ServerWeeks(get<Session>()) }

    // The session's one owner of the entries the history, the calendar and the entry form show.
    // Sign-out forgets them.
    single {
        SessionEntries(
            cached = get<AccountCache>().entries,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            olderPages = get(),
            serverWeeks = get(),
            clock = get(),
            app = get(),
            accounts = get<CurrentAccount>().context.map { it.number },
            scope = get<CoroutineScope>() + get<CoroutineDispatcher>(MainDispatcher),
            io = io(),
            computation = computation(),
        )
    } bind AccountDataOwner::class
    viewModel {
        val cache = get<AccountCache>()
        HistoryViewModel(
            entries = get(),
            projects = cache.projects,
            tasks = cache.tasks,
            scope = viewModelCoroutineScope(),
            computation = computation(),
        )
    }
    viewModel {
        EntryActionsViewModel(
            entries = get<AccountCache>().entries,
            changes = get<ChangeQueue>(),
            removals = get(),
            undoDelete = get<DeleteUndoer>()::undoDelete,
            capabilities = get<CapabilitiesSource>().state,
            accountSettings = get<AccountSettingsSource>().state,
            scope = viewModelCoroutineScope(),
            clock = get(),
        )
    }
    viewModel {
        val cache = get<AccountCache>()
        val capabilities = get<CapabilitiesSource>()
        CalendarViewModel(
            entries = get(),
            projects = cache.projects,
            tasks = cache.tasks,
            accountSettings = get<AccountSettingsSource>().state,
            changes = get<ChangeQueue>(),
            scope = viewModelCoroutineScope(),
            computation = computation(),
            clock = get(),
            // From the capabilities once they have loaded.
            allDayOffered = capabilities.allDayOffers(),
            saved = get(),
        )
    }
    viewModel {
        val cache = get<AccountCache>()
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
            sender = get<ChangeSender>()::sendQueued,
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
            store = get(),
            scope = scope,
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
            running = get<ChangeQueue>(),
            pastEdits = get(),
            scope = scope,
            clock = get(),
            allDay = get<CapabilitiesSource>().state.map { it.allDay == true },
            picker = pickerSource(cache.projects, cache.clients, cache.tasks, cache.tags),
            offline = get<DefaultNetwork>().offlineIn(scope),
            saved = get(),
        ).also { form -> params.get<EntryFormRequest>().openIn(form) }
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

    single { TimerEntries } bind FeatureRoutes::class
    single { TimerRequests } bind FeatureRequests::class

    // The bar's running dot on Timer while an entry runs, by the cache's word.
    single { TimerBadge(get<AccountCache>().running.map { it != null }) } bind PageBadge::class
}
