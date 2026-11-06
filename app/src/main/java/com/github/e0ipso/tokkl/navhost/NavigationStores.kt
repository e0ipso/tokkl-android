package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.navigation.EntryFormOpenings
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
