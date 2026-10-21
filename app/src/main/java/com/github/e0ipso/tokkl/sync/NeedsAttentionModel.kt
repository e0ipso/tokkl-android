package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.cache.PendingChange
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
class NeedsAttentionModel(
    val held: Flow<List<PendingChange>>,
    private val release: suspend (sequence: Long) -> Boolean,
    private val drop: suspend (sequence: Long) -> Boolean,
    private val afterDiscard: () -> Unit,
    private val scope: CoroutineScope,
) {

    fun sendAgain(change: PendingChange) {
        scope.launch { release(change.sequence) }
    }

    fun discard(change: PendingChange) {
        scope.launch { if (drop(change.sequence)) afterDiscard() }
    }
}
