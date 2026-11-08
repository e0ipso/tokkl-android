package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ShortStopOffer
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.trust.SessionTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * What the signed-in frame shows around the page: the running dots on the bar's items in
 * [badges], each page's route with the label TalkBack reads for its dot, the short Stop's Undo in
 * [shortStopUndo], and the session's trust prompt in [trust].
 */
@Immutable
internal data class SignedInState(
    val badges: Map<Route, Int> = emptyMap(),
    val shortStopUndo: ShortStopOffer? = null,
    val trust: SessionTrust.State = SessionTrust.State(),
)

/**
 * The signed-in frame's state and events, in the signed-in session's store, so a sign-out drops
 * it with the session.
 *
 * @param badges the running dots the features put on their pages' items.
 */
internal class SignedInViewModel(
    badges: List<PageBadge>,
    private val changes: ChangeQueue,
    private val trust: SessionTrust,
    scope: CoroutineScope,
) : ViewModel(scope) {

    /** What the frame shows, while it reads it. Nothing runs until the cache and the timer state are read. */
    val state: StateFlow<SignedInState> = combine(
        runningPages(badges),
        changes.shortStopUndo.onStart { emit(null) },
        trust.state,
        ::SignedInState,
    ).stateIn(viewModelScope, whileShown, SignedInState(trust = trust.state.value))

    /** Takes back the short Stop of [entryId], as its snackbar's Undo does. */
    suspend fun undoShortStop(entryId: EntryId) {
        changes.undoShortStop(entryId)
    }

    /** Forgets the short Stop of [entryId], once its snackbar went without an Undo. */
    suspend fun dismissShortStop(entryId: EntryId) {
        changes.dismissShortStop(entryId)
    }

    fun acceptTrust() = trust.accept()

    fun cancelTrust() = trust.cancel()

    fun reopenTrust() = trust.reopen()
}

/**
 * The pages whose running dot shows, as their [badges] say, each with the label TalkBack reads for
 * it. A badge counts as off until it is read, so the bar shows no dot before then.
 */
internal fun runningPages(badges: List<PageBadge>): Flow<Map<Route, Int>> = if (badges.isEmpty()) {
    flowOf(emptyMap())
} else {
    combine(
        badges.map { badge ->
            badge.shown.distinctUntilChanged().onStart { emit(false) }.map { shown -> badge.takeIf { shown } }
        },
    ) { shown -> shown.filterNotNull().associate { it.route to it.label } }
}
