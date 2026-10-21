package com.github.e0ipso.tokkl.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many queued changes left the queue because the server no longer had their entry, since the
 * user last dismissed the notice that says so (docs/PRD.md O6): "1 change discarded: the entry was
 * deleted elsewhere". Each discard adds to the count, so one notice covers them all. The count
 * outlives the process, so a discard by the background send with the app closed shows when the
 * app opens.
 *
 * @param initial the stored count, 0 when none is stored.
 * @param save keeps the count on the phone.
 */
class DiscardedChanges(initial: Int, private val save: (Int) -> Unit) {

    private val mutableState = MutableStateFlow(initial)

    /** The count the notice shows, or 0 while it shows none. */
    val state: StateFlow<Int> = mutableState.asStateFlow()

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
            save(count)
        }
    }
}
