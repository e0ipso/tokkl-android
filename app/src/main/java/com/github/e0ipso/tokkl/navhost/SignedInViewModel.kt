package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ShortStopOffer
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.trust.SessionTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * What the signed-in frame shows around the page: the Timer page's [timerView], which decides
 * whether the entry form shows beside the history, the marks on the bar's Timer and Pomodoro
 * items, the [syncStatus] band, the [discardedCount] notice, the short Stop's Undo in
 * [shortStopUndo], and the session's trust prompt in [trust].
 */
@Immutable
internal data class SignedInState(
    val timerView: TimerView = TimerView.LIST,
    val timerRunning: Boolean = false,
    val pomodoroRunning: Boolean = false,
    val syncStatus: SyncStatus? = null,
    val discardedCount: Int = 0,
    val shortStopUndo: ShortStopOffer? = null,
    val trust: SessionTrust.State = SessionTrust.State(),
)

/**
 * The signed-in frame's state and events, in the signed-in session's store, so a sign-out drops
 * it with the session.
 *
 * @param timerRunning whether a time entry runs, as the cache has it.
 * @param pomodoroRunning whether the server's Pomodoro session runs, as the held timer state has it.
 * @param servers the stored server, which signing in again starts from. Read on [io].
 */
internal class SignedInViewModel(
    timerView: StateFlow<TimerView>,
    timerRunning: Flow<Boolean>,
    pomodoroRunning: Flow<Boolean>,
    syncStatus: StateFlow<SyncStatus?>,
    private val discarded: DiscardedChanges,
    private val changes: ChangeQueue,
    private val trust: SessionTrust,
    private val servers: ServerConfigStore,
    private val settings: StateFlow<AccountSettings?>,
    private val io: CoroutineContext,
    scope: CoroutineScope,
) : ViewModel(scope) {

    private val marks = combine(
        timerRunning.distinctUntilChanged().onStart { emit(false) },
        pomodoroRunning.distinctUntilChanged().onStart { emit(false) },
        ::Pair,
    )

    private val notices = combine(
        syncStatus,
        discarded.state,
        changes.shortStopUndo.onStart { emit(null) },
        trust.state,
        ::Notices,
    )

    /** What the frame shows, while it reads it. Nothing runs until the cache and the timer state are read. */
    val state: StateFlow<SignedInState> = combine(timerView, marks, notices) { view, (running, pomodoro), shown ->
        SignedInState(
            timerView = view,
            timerRunning = running,
            pomodoroRunning = pomodoro,
            syncStatus = shown.syncStatus,
            discardedCount = shown.discardedCount,
            shortStopUndo = shown.shortStopUndo,
            trust = shown.trust,
        )
    }.stateIn(
        viewModelScope,
        whileShown,
        SignedInState(
            timerView = timerView.value,
            syncStatus = syncStatus.value,
            discardedCount = discarded.state.value,
            trust = trust.state.value,
        ),
    )

    /** The account to sign in to again after the server refused its token, or null without a stored server. */
    suspend fun signInAgain(): SignInAgain? = withContext(io) {
        servers.load()?.let { server -> SignInAgain(server, settings.value?.userId) }
    }

    /** Takes back the short Stop of [entryId], as its snackbar's Undo does. */
    suspend fun undoShortStop(entryId: EntryId) {
        changes.undoShortStop(entryId)
    }

    /** Forgets the short Stop of [entryId], once its snackbar went without an Undo. */
    suspend fun dismissShortStop(entryId: EntryId) {
        changes.dismissShortStop(entryId)
    }

    fun dismissDiscarded() = discarded.dismiss()

    fun acceptTrust() = trust.accept()

    fun cancelTrust() = trust.cancel()

    fun reopenTrust() = trust.reopen()

    private class Notices(
        val syncStatus: SyncStatus?,
        val discardedCount: Int,
        val shortStopUndo: ShortStopOffer?,
        val trust: SessionTrust.State,
    )
}
