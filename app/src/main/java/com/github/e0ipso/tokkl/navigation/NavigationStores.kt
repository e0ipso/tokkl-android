package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import java.util.UUID
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the navigation's view models live. It is a view model of MainActivity, so the stores
 * outlive a configuration change and turning the phone loads nothing again.
 *
 * [entries] gives each route on the back stack a store of its own, which Navigation 3's view
 * model decorator clears when the route leaves the stack. [session] holds the view models that
 * serve several screens of a signed-in session, or keep a page's state after the page closes:
 * the timer, the entry actions, the timer templates, the reports and the Pomodoro page. A
 * sign-out discards both, so nothing an account's screens held in memory, such as the history's
 * older days, reaches the next sign-in.
 */
internal class NavigationStores : ViewModel() {

    /** The sign-outs the stores came after, as [after] last heard. */
    private var signOuts: Int? = null

    var entries: ViewModelStoreProvider = ViewModelStoreProvider(parentStore = null)
        private set

    private var sessionStore = ViewModelStore()

    var session: ViewModelStoreOwner = ownerOf(sessionStore)
        private set

    val entryOpenings = EntryFormOpenings()

    /**
     * Tells the stores how many sign-outs have finished, as `SignOut.finished` counts them. When
     * one finished since the last call, also while MainActivity was being made again, the stores
     * drop every view model and this returns true.
     */
    fun after(signOuts: Int): Boolean {
        val before = this.signOuts
        this.signOuts = signOuts
        if (before == null || before == signOuts) return false
        // A screen still composed keeps its store until it leaves the composition.
        entries.clearAllKeys()
        entries = ViewModelStoreProvider(parentStore = null)
        sessionStore.clear()
        sessionStore = ViewModelStore()
        session = ownerOf(sessionStore)
        entryOpenings.clear()
        return true
    }

    override fun onCleared() {
        entries.clearAllKeys()
        sessionStore.clear()
    }

    private fun ownerOf(store: ViewModelStore) = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
    }
}

/** The signed-in session's view model store, which [NavigationStores.session] holds. */
internal val LocalSignedInSession = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("No signed-in session store")
}

/** The signed-in session's [T], made through Koin on first use. */
@Composable
internal inline fun <reified T : ViewModel> sessionViewModel(): T =
    koinViewModel(viewModelStoreOwner = LocalSignedInSession.current)

/**
 * The entry form openings MainActivity made, each with the copy of an entry that only the
 * history's or the calendar's memory holds, until the form's view model takes it. A new process
 * starts with none, so a form restored from saved state finds nothing and closes: its unsaved
 * fields went with the process, and so may the copy it opened on.
 */
internal class EntryFormOpenings {
    private val waiting = mutableMapOf<String, CachedTimeEntry?>()

    /** A new form's route for [opening], on [older] when the cache does not hold the entry. */
    fun route(opening: EntryOpening, older: CachedTimeEntry?): Route.Entry {
        val route = Route.Entry(opening, UUID.randomUUID().toString())
        waiting[route.id] = older
        return route
    }

    /** What [route] opens, once, or null when this process did not open it. */
    fun take(route: Route.Entry): EntryFormRequest? =
        if (route.id in waiting) EntryFormRequest(route.opening, waiting.remove(route.id)) else null

    fun clear() = waiting.clear()
}

/** What a new entry form opens on: [opening], with [older] for an entry the cache lacks. */
class EntryFormRequest(val opening: EntryOpening, val older: CachedTimeEntry?) {
    fun openIn(form: EntryFormViewModel) {
        when (opening) {
            is EntryOpening.Edit -> form.edit(opening.entryId, older)
            is EntryOpening.Running -> form.editRunning(opening.identity)
            is EntryOpening.New -> {
                val day = opening.day
                if (opening.noTimes && day != null) form.newAllDay(day) else form.new(day)
            }
        }
    }
}
