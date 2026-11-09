package com.github.e0ipso.tokkl.navhost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import com.github.e0ipso.tokkl.navigation.EntryFormOpenings

/**
 * Where the navigation's view models live. It is a view model of MainActivity, so the stores
 * outlive a configuration change and turning the phone loads nothing again.
 *
 * [entries] gives each route on the back stack a store of its own, which Navigation 3's view
 * model decorator clears when the route leaves the stack. [session], a store of the same provider
 * under a key no route has, holds the view models that serve several screens of a signed-in
 * session, or keep a page's state after the page closes: the timer, the entry actions, the timer
 * templates, the reports' tab and filters and the Pomodoro page. A sign-out discards them all, so nothing an
 * account's screens held in memory, such as the history's older days, reaches the next sign-in.
 *
 * Lifecycle's own `rememberViewModelStoreProvider` clears its stores when its call leaves the
 * composition, but not when that happens while MainActivity is being made again. A sign-out that
 * finishes then would leave the old account's view models in MainActivity's store, so the stores
 * keep which of the account's finished sign-outs they came after.
 */
internal class NavigationStores : ViewModel() {

    /** The sign-outs the stores came after, as [after] last heard. */
    private var signOuts: Int? = null

    var entries: ViewModelStoreProvider = ViewModelStoreProvider(parentStore = null)
        private set

    var session: ViewModelStoreOwner = entries.getOrCreateOwner(SessionKey)
        private set

    val entryOpenings = EntryFormOpenings()

    /**
     * Tells the stores how many sign-outs have finished, as `CurrentAccount.signOuts` counts them. When
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
        session = entries.getOrCreateOwner(SessionKey)
        entryOpenings.clear()
        return true
    }

    override fun onCleared() {
        entries.clearAllKeys()
    }

    /** The key of [session]'s store. No route's content key equals it. */
    private object SessionKey
}
