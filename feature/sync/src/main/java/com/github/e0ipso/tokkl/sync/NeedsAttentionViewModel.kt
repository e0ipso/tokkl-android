package com.github.e0ipso.tokkl.sync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.CacheRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The "needs attention" list: the [held] changes the server refused, and the [settings] their times follow. */
@Immutable
data class NeedsAttentionState(val held: List<HeldChange> = emptyList(), val settings: AccountSettings? = null)

/**
 * The "needs attention" screen's state, from [attention]'s refused changes, and its Send again
 * and Discard, which [attention] runs in a scope that outlives the screen.
 */
class NeedsAttentionViewModel(
    private val attention: NeedsAttention,
    settings: StateFlow<AccountSettings?>,
    scope: CoroutineScope,
) : ViewModel(scope) {

    /** What the screen shows, while it reads it. The list is empty until the queue is read. */
    val state: StateFlow<NeedsAttentionState> = combine(
        attention.held.onStart { emit(emptyList()) },
        settings,
        ::NeedsAttentionState,
    ).stateIn(viewModelScope, whileShown, NeedsAttentionState(settings = settings.value))

    fun sendAgain(change: HeldChange) = attention.sendAgain(change)

    fun discard(change: HeldChange) = attention.discard(change)
}

/**
 * The "needs attention" list (docs/PRD.md O6): the queued changes the server refused, each with
 * the server's reason, which wait for the user while later changes to the same entry wait behind
 * them. Editing the entry folds the fix into the held change and sends it, as `ChangeQueue` does
 * for any change to that entry. [sendAgain] sends a held change as it is, and [discard] drops it
 * with the changes behind it, then asks [refreshes] for a refresh so the cache can take the
 * server's entry back.
 *
 * @param scope outlives the screen, so leaving it does not cancel a write.
 */
class NeedsAttention(
    private val changes: HeldChanges,
    private val refreshes: CacheRefresh,
    private val scope: CoroutineScope,
) {

    val held: Flow<List<HeldChange>> = changes.held

    fun sendAgain(change: HeldChange) {
        scope.launch { changes.sendAgain(change.sequence) }
    }

    fun discard(change: HeldChange) {
        scope.launch { if (changes.discard(change.sequence)) refreshes.refresh() }
    }
}
