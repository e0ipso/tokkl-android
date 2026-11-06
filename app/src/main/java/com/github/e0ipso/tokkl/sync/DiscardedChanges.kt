package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutOrder
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many queued changes left the queue because the server no longer had their entry, since the
 * user last dismissed the notice that says so (docs/PRD.md O6): "1 change discarded: the entry was
 * deleted elsewhere". Each discard adds to the count, so one notice covers them all. The count
 * outlives the process, so a discard by the background send with the app closed shows when the
 * app opens. The first read loads the stored count from [store] on the reading thread.
 */
class DiscardedChanges(private val store: ClearableStore<Int>) : AccountDataOwner {

    override val signOutOrder = SignOutOrder.DISCARDED_CHANGES

    override suspend fun forgetAccount() {
        dismiss()
        store.clear()
    }

    private val mutableState by lazy { MutableStateFlow(store.load() ?: 0) }

    /** The count the notice shows, or 0 while it shows none. */
    val state: StateFlow<Int> by lazy { mutableState.asStateFlow() }

    private val lock = Any()

    /** [count] more changes were discarded. Called on the drain's thread. */
    fun add(count: Int) {
        if (count <= 0) return
        set { it + count }
    }

    /** The user read the notice, or signed out. */
    fun dismiss() = set { 0 }

    private fun set(change: (Int) -> Int) {
        synchronized(lock) {
            val count = change(mutableState.value)
            mutableState.value = count
            store.save(count)
        }
    }
}
