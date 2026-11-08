package com.github.e0ipso.tokkl.sync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

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
