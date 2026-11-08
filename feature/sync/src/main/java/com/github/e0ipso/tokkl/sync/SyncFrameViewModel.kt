package com.github.e0ipso.tokkl.sync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.platform.whileShown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the sync feature shows in the signed-in frame: the sync [status] band and the [discardedCount] notice. */
@Immutable
internal data class SyncFrame(val status: SyncStatus? = null, val discardedCount: Int = 0)

/**
 * The sync status band and the discarded changes' notice that the signed-in frame shows around
 * every page, in the signed-in session's store, so a sign-out drops it with the session. After the
 * server refused the token, the band's sign-in opens sign-in again for the account ([signIns]).
 */
internal class SyncFrameViewModel(
    status: StateFlow<SyncStatus?>,
    private val discarded: DiscardedChanges,
    private val signIns: SignInRoutes,
    scope: CoroutineScope,
) : ViewModel(scope) {

    /** What the frame shows, while it reads it. */
    val state: StateFlow<SyncFrame> = combine(status, discarded.state, ::SyncFrame)
        .stateIn(viewModelScope, whileShown, SyncFrame(status.value, discarded.state.value))

    /** Sign-in for the account whose token the server refused, or null without a stored server. */
    suspend fun signInAgain(): Route? = signIns.again()

    fun dismissDiscarded() = discarded.dismiss()
}
