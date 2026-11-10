package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/**
 * The status element above the bottom bar, once changes have waited long enough to show.
 *
 * @param unsent how many changes the server has not received, held ones included.
 * @param signInNeeded whether the server refused the session's token, which pauses the queue.
 */
class SyncStatusSource(
    unsent: Flow<Int>,
    needsAttention: Flow<Int>,
    signInNeeded: Flow<Boolean>,
    lastAnswered: Flow<Instant?>,
    settings: Flow<AccountSettings?>,
    scope: CoroutineScope,
) {

    val status: StateFlow<SyncStatus?> = shownSyncStatus(
        unsent,
        lastAnswered,
        settings,
        needsAttention = needsAttention,
        signInNeeded = signInNeeded,
    ).stateIn(scope, SharingStarted.WhileSubscribed(STATUS_STOP_TIMEOUT_MS), null)
}

/** How long the status keeps following the queue after the last screen stops showing it, as through a rotation. */
private const val STATUS_STOP_TIMEOUT_MS = 5_000L
