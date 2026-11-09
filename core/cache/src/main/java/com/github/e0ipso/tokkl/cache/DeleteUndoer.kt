package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** What [DeleteUndoer.undoDelete] did. */
sealed interface UndoOutcome {
    /** The delete had not gone out. It left the queue and the entry is back, with no request. */
    data object Cancelled : UndoOutcome

    /** The server restored the entry. */
    data object Restored : UndoOutcome

    /**
     * The delete reached the server, or may have, and the server cannot be reached: no
     * network, a proxy whose server is down, a 429, a refused trust check or no session.
     * The delete stays as it was, so Undo can be tried again.
     */
    data object NeedsServer : UndoOutcome

    /** The server did not restore the entry. [status] is its HTTP status, or null without one. */
    data class Failed(val status: Int?) : UndoOutcome
}

/**
 * Takes a delete back (docs/PRD.md H3). A delete still in the queue leaves it with no request.
 * One the server has, or may have, comes back through Tokkl's
 * `POST /api/app/time_entries/{id}/restore`, which needs the server, so call it only when
 * `Capabilities.restore` is true.
 *
 * [ChangeSender] marks a delete [PendingChange.maybeSent] before sending it, and sends it inside
 * [sendingDelete], which holds the lock [undoDelete] holds, so Undo never decides while a delete is
 * on its way, and a delete that Undo took back never goes out.
 *
 * @param client the signed-in account's client, or null when signed out. Runs on [io].
 * @param refreshes asked for a refresh after the server restored an entry.
 */
class DeleteUndoer(
    private val store: ChangeStore,
    private val client: ApiClientProvider,
    private val io: CoroutineContext,
    private val refreshes: AccountRefreshRequests,
) : DeleteUndo {

    /** Held while a delete goes out and while [undoDelete] takes one back, so the two never overlap. */
    private val deletes = Mutex()

    /** Runs [send], the sender's try at a delete, so that no Undo decides meanwhile. */
    internal suspend fun <T> sendingDelete(send: suspend () -> T): T = deletes.withLock { send() }

    /**
     * Takes back the delete of the entry [entryId], the id the phone deleted it under. A delete
     * the sender has not sent leaves the queue, with no request. Otherwise the server restores the
     * entry. Blocks the sender's deletes meanwhile. Once started, the Undo reaches the server and
     * the queue also when the caller is cancelled.
     */
    override suspend fun undoDelete(entryId: EntryId): UndoOutcome {
        val outcome = withContext(io + NonCancellable) {
            deletes.withLock {
                when (val found = store.takeBackDelete(entryId)) {
                    TakenBack.Unsent -> UndoOutcome.Cancelled
                    is TakenBack.OnServer -> restore(found.serverId)
                    TakenBack.Unknown -> UndoOutcome.Failed(status = null)
                }
            }
        }
        if (outcome == UndoOutcome.Restored) withContext(io) { refreshes.refreshAfterChange() }
        return outcome
    }

    /** Asks the server to restore its entry [serverId], and records the answer. */
    private suspend fun restore(serverId: Long): UndoOutcome {
        val api = client() ?: return UndoOutcome.NeedsServer
        return when (val result = api.entries.restore(EntryId(serverId), resend = true)) {
            is ApiResult.Success -> {
                store.restored(serverId, result.value.toCached())
                UndoOutcome.Restored
            }

            is ApiResult.Failure -> when (val error = result.error) {
                // A 2xx: the server restored it, in an answer the phone cannot read.
                is ApiError.InvalidResponse -> {
                    store.restored(serverId, answer = null)
                    UndoOutcome.Restored
                }

                is ApiError.Network, is ApiError.RateLimited, is ApiError.TrustRefused -> UndoOutcome.NeedsServer

                is ApiError.UnexpectedStatus ->
                    if (error.status in PROXY_UNREACHABLE) UndoOutcome.NeedsServer else UndoOutcome.Failed(error.status)

                ApiError.AuthFailed -> UndoOutcome.Failed(ApiError.AuthFailed.status)

                is ApiError.HostNotAllowed -> UndoOutcome.Failed(status = null)
            }
        }
    }

    private companion object {
        /** Bad gateway, service unavailable and gateway timeout, which a proxy answers while its server is down. */
        val PROXY_UNREACHABLE = setOf(502, 503, 504)
    }
}

/** Takes a delete back, as [DeleteUndoer] does over the queue and the server; a page's unit tests answer for it. */
fun interface DeleteUndo {
    suspend fun undoDelete(entryId: EntryId): UndoOutcome
}
