package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The status element above the bottom bar: the queue's unsent count, the changes that need
 * attention, a refused token and the server's last answer, once changes have waited long
 * enough to show.
 */
class SyncStatusSource(
    changes: ChangeQueue,
    lastAnswer: LastAnswer,
    accountSettings: AccountSettingsSource,
    sender: ChangeSender,
    scope: CoroutineScope,
) {

    val status: StateFlow<SyncStatus?> = shownSyncStatus(
        changes.unsentCount,
        lastAnswer.state,
        accountSettings.state,
        needsAttention = changes.heldCount,
        signInNeeded = sender.signInNeeded,
    ).stateIn(scope, SharingStarted.WhileSubscribed(STATUS_STOP_TIMEOUT_MS), null)
}

/** How long the status keeps following the queue after the last screen stops showing it, as through a rotation. */
private const val STATUS_STOP_TIMEOUT_MS = 5_000L
