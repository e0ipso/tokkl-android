package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.session.RefreshRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The "needs attention" list (docs/PRD.md O6): the queued changes the server refused, each with
 * the server's reason, which wait for the user while later changes to the same entry wait behind
 * them. Editing the entry folds the fix into the held change and sends it, as `ChangeQueue` does
 * for any change to that entry. [sendAgain] sends a held change as it is, and [discard] drops it
 * with the changes behind it, then calls [afterDiscard] so the cache can take the server's entry
 * back.
 *
 * @param scope outlives the screen, so leaving it does not cancel a write.
 */
class NeedsAttention(
    private val changes: HeldChanges,
    private val afterDiscard: RefreshRequest,
    private val scope: CoroutineScope,
) {

    val held: Flow<List<PendingChange>> = changes.held

    fun sendAgain(change: PendingChange) {
        scope.launch { changes.sendAgain(change.sequence) }
    }

    fun discard(change: PendingChange) {
        scope.launch { if (changes.discard(change.sequence)) afterDiscard.request() }
    }
}
