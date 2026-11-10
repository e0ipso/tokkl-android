package com.github.e0ipso.tokkl.navhost

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.github.e0ipso.tokkl.navigation.EntryFormOpenings
import androidx.lifecycle.viewmodel.ViewModelStoreOwner as savedStateOwner

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

    fun session(parent: SavedStateRegistryOwner): ViewModelStoreOwner {
        val store = entries.getOrCreate(SESSION_KEY)
        val previous = parent.savedStateRegistry.getSavedStateProvider(SESSION_KEY) as? SessionOwner
        if (previous?.models?.viewModelStore === store) return previous.models
        previous?.close()
        return SessionOwner(store, parent).models
    }

    /** A restored sign-in page must not pass an earlier account's drafts to its next sign-in. */
    fun discardRestoredSession(parent: SavedStateRegistryOwner) {
        parent.savedStateRegistry.consumeRestoredStateForKey(SESSION_KEY)
    }

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
        entryOpenings.clear()
        return true
    }

    override fun onCleared() {
        entries.clearAllKeys()
    }

    /** A separate registry saves the session's handles and cannot retain them after sign-out. */
    private class SessionOwner(store: ViewModelStore, private val parent: SavedStateRegistryOwner) :
        SavedStateRegistryOwner,
        SavedStateRegistry.SavedStateProvider {
        override val lifecycle = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = controller.savedStateRegistry
        val models: ViewModelStoreOwner

        init {
            controller.performAttach()
            controller.performRestore(parent.savedStateRegistry.consumeRestoredStateForKey(SESSION_KEY))
            models = savedStateOwner(store, this)
            lifecycle.currentState = Lifecycle.State.CREATED
            parent.savedStateRegistry.registerSavedStateProvider(SESSION_KEY, this)
        }

        override fun saveState(): Bundle = Bundle().also(controller::performSave)

        fun close() {
            parent.savedStateRegistry.unregisterSavedStateProvider(SESSION_KEY)
            lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }

    private companion object {
        const val SESSION_KEY = "signed-in-session"
    }
}
