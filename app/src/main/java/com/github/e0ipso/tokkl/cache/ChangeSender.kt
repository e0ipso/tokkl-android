package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshed
import com.github.e0ipso.tokkl.session.SignOutOrder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Sends the queue to the configured server, oldest change first (docs/PRD.md O1). A failure the
 * phone can wait out stops the drain and keeps the change queued, so the order holds and nothing
 * is lost: no network, a 5xx, a 408 or a 429. After a 429 the next try waits for `Retry-After`,
 * or [DEFAULT_RETRY_AFTER] when the header is missing. The wait is kept in memory, and the queue
 * itself on disk.
 *
 * Each other refusal has its own outcome, as [refusal] describes (docs/PRD.md O6). An entry the
 * server no longer has takes every queued change to it out of the queue and the entry out of the
 * cache, and [onDiscarded] counts them for the discard notice. A 404 on a delete that may already
 * have reached the server counts as sent. A held change waits for the user with the server's
 * message ([ChangeStore.hold]); later changes to the same entry wait behind it, and the rest of the
 * queue goes on. A 403, or a 401, pauses the whole queue and discards nothing: [signInNeeded]
 * turns true, and no drain sends anything until [resume], which signing in again calls.
 *
 * [ChangeTries] sends each change once and says how a queued start goes when a later timer runs
 * (O3) and how a create that may have reached the server is found instead of posted again. When a
 * try's connection fails after the request left the phone ([worthAnotherTryAtOnce]), such as a
 * pooled connection the server had closed while it sat idle, the drain tries that change once
 * more at once, on a new connection, before it stops. The sender's reads may still go out again.
 *
 * [send] runs when a change is queued and after each refresh, and once more when a 429's wait
 * ends. When the network returns, [BackgroundSend]'s worker calls [sendAndWait], also with the
 * app closed.
 *
 * [undoDelete] takes a delete back (docs/PRD.md H3). A delete still in the queue leaves it with no
 * request. One the server has, or may have, comes back through Tokkl's
 * `POST /api/app/time_entries/{id}/restore`, which needs the server. The sender marks a delete
 * [PendingChange.maybeSent] before sending it, and sends it holding the lock that [undoDelete]
 * holds, so Undo never decides while a delete is on its way, and a delete that Undo took back
 * never goes out.
 *
 * @param client the signed-in account's client, or null when signed out. Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a drain.
 * @param onSent runs on [io] once a drain has sent at least one change, so the app can refresh:
 *   after a quiet drain, not after each change or pass. A change queued during this callback
 *   drains before the job ends. It runs after a restore too.
 * @param onEmptied runs on [io] after a quiet drain that sent at least one change has left
 *   nothing to send, inside the drain's job and before [onSent], so the app can cancel a
 *   background retry that would find nothing to send.
 * @param onDiscarded runs on [io] with how many changes a 404 took out of the queue because the
 *   server no longer has their entry, so the app can say so.
 * @param spans where the server puts a start once the entry is stopped, which finds the entry a
 *   lost create made, and the span of a queued start ended before a later timer.
 * @param allDay whether the server stores no-time entries (`Capabilities.allDay`), read for each
 *   request. While it is false no request carries `all_day`, as Toggl Track needs.
 */
class ChangeSender(
    private val store: ChangeStore,
    private val client: ApiClientProvider,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val onSent: () -> Unit = {},
    private val onEmptied: () -> Unit = {},
    private val onDiscarded: (Int) -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val spans: ServerSpans = ServerSpans.Unsnapped,
    private val allDay: () -> Boolean = { false },
) : AccountRefreshListener, AccountDataOwner {

    override val signOutOrder = SignOutOrder.CHANGE_SENDER

    override suspend fun forgetAccount() = stop()

    private val tries = ChangeTries(store, spans, allDay)

    /** What one [drain] did. */
    sealed interface Outcome {
        /**
         * How many changes left the queue because the server answered them: it stored them, it
         * had what a stop asked for already, or it no longer has their entry.
         */
        val sent: Int

        /** Nothing is left to send: the queue is empty, or holds only changes that wait for the user. */
        data class Empty(override val sent: Int) : Outcome

        /**
         * The oldest change stays queued, because the server did not accept it, or because the
         * queue waits for the user to sign in again ([ApiError.AuthFailed]).
         */
        data class Stopped(override val sent: Int, val error: ApiError) : Outcome

        /** A 429 asked the phone to wait until [until] before the next try. */
        data class Waiting(override val sent: Int, val until: Instant) : Outcome

        /** Signed out: nothing goes anywhere. */
        data class SignedOut(override val sent: Int) : Outcome
    }

    /** What [undoDelete] did. */
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

    /** Guards [sending], [again] and [retry]. */
    private val lock = Any()

    /** Held while a delete goes out and while [undoDelete] takes one back, so the two never overlap. */
    private val deletes = Any()

    private var sending: Job? = null

    /** A [send] call came while a drain ran, which may have looked at the queue before it. */
    private var again = false

    private var retry: Job? = null

    /** No request before this instant, after a 429. */
    @Volatile
    private var retryAt: Instant? = null

    /** How the latest pass of a drain ended, or null when it failed on the disk. */
    @Volatile
    private var lastOutcome: Outcome? = null

    private val mutableSignInNeeded = MutableStateFlow(false)

    /**
     * True once the server refused the session's token for a queued change (docs/PRD.md O6). The
     * queue then sends nothing and discards nothing until [resume].
     */
    val signInNeeded: StateFlow<Boolean> = mutableSignInNeeded.asStateFlow()

    /**
     * The server refused the session's token on another request, as the timer event stream's 401
     * or 403. Pauses the queue as its own refusal would, so the status asks to sign in again, until
     * [resume].
     */
    fun signInRefused() {
        mutableSignInNeeded.value = true
    }

    /** The server has just answered the account's refresh, so the queue may go out now. */
    override fun refreshed(refresh: AccountRefreshed) {
        send()
    }

    /** Lifts the pause after a refused token, once the user has signed in again, and drains the queue. */
    fun resume(): Job {
        mutableSignInNeeded.value = false
        return send()
    }

    /**
     * Drains the queue in the background. While a drain runs, another call returns that one, which
     * then drains once more, so a change queued meanwhile does not wait for the next event.
     */
    fun send(): Job {
        val job = synchronized(lock) {
            sending?.also { again = true }
                ?: scope.launch(io, start = CoroutineStart.LAZY) { drainUntilQuiet() }.also { sending = it }
        }
        job.start()
        return job
    }

    /**
     * Drains the queue as [send] does and waits for the drain to end. Returns how its last pass
     * ended, or null when that pass failed on the disk. Cancelling the caller leaves the drain
     * running.
     */
    suspend fun sendAndWait(): Outcome? {
        send().join()
        return lastOutcome
    }

    /**
     * Cancels the wait after a 429 and forgets it, then waits for a running drain to end. Signing
     * out calls this once [client] returns null, so the drain ends at its next change and nothing
     * it writes lands after the cache is cleared.
     */
    suspend fun stop() {
        val running = synchronized(lock) {
            retry?.cancel()
            retry = null
            sending
        }
        running?.join()
        retryAt = null
        lastOutcome = null
        mutableSignInNeeded.value = false
    }

    private fun drainUntilQuiet() {
        var drainAgain = true
        var sent = 0
        try {
            while (drainAgain) {
                val outcome = try {
                    drain()
                } catch (e: SQLiteException) {
                    // The queue stays on disk for the next try.
                    null
                }
                lastOutcome = outcome
                sent += outcome?.sent ?: 0
                if (outcome is Outcome.Waiting) retryAfterWait(outcome.until)
                if (!synchronized(lock) { again }) {
                    // Keep this job published while cancelling background work and refreshing.
                    // A caller during cleanup must join it, and any new change gets another pass.
                    if (sent > 0 && lastOutcome is Outcome.Empty) onEmptied()
                    if (sent > 0) onSent()
                    sent = 0
                }
                drainAgain = synchronized(lock) {
                    again.also {
                        again = false
                        if (!it) sending = null
                    }
                }
            }
        } finally {
            // A cancelled or failed drain lets the next call start another.
            if (drainAgain) synchronized(lock) { sending = null }
        }
    }

    /**
     * Sends the queued changes in order until nothing is left to send or one fails in a way the
     * phone can wait out. Each sent change leaves the queue as [ChangeStore.sent] describes, and
     * each refused one as [ChangeSender] describes. Blocks on the network and the disk, so call
     * it off the main thread, and from one thread at a time.
     */
    fun drain(): Outcome {
        var sent = 0
        // The change this drain already tried once more, by its queue sequence.
        var triedAgain: Long? = null
        while (true) {
            if (mutableSignInNeeded.value) return Outcome.Stopped(sent, ApiError.AuthFailed)
            val wait = retryAt
            if (wait != null) {
                if (clock.instant().isBefore(wait)) return Outcome.Waiting(sent, wait)
                retryAt = null
            }
            val queued = store.first() ?: return Outcome.Empty(sent)
            val api = client() ?: return Outcome.SignedOut(sent)
            when (val result = send(api, queued)) {
                // Undo took the delete back before it went out.
                null -> Unit
                is ApiResult.Success -> {
                    store.sent(result.value.change, result.value.serverId, result.value.entry)
                    sent++
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    if (error is ApiError.RateLimited) {
                        val until = clock.instant().plusSeconds(error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER.seconds)
                        retryAt = until
                        return Outcome.Waiting(sent, until)
                    }
                    // Read the change again: the failed try may have marked it maybeSent.
                    if (error.worthAnotherTryAtOnce() && triedAgain != queued.sequence) {
                        triedAgain = queued.sequence
                        continue
                    }
                    when (val refused = refusal(api, queued, error)) {
                        is Refusal.Wait -> {
                            val waiting = refused.error
                            if (waiting is ApiError.RateLimited) {
                                val until = clock.instant().plusSeconds(waiting.retryAfterSeconds ?: DEFAULT_RETRY_AFTER.seconds)
                                retryAt = until
                                return Outcome.Waiting(sent, until)
                            }
                            return Outcome.Stopped(sent, waiting)
                        }
                        Refusal.SignInAgain -> {
                            mutableSignInNeeded.value = true
                            return Outcome.Stopped(sent, ApiError.AuthFailed)
                        }
                        Refusal.AlreadyDone -> {
                            store.sent(queued, queued.entryId, answer = null)
                            sent++
                        }
                        Refusal.EntryGone -> {
                            val discarded = store.discardEntry(queued)
                            sent += discarded
                            if (discarded > 0) onDiscarded(discarded)
                        }
                        is Refusal.Hold -> store.hold(queued, refused.reason)
                    }
                }
            }
        }
    }

    /** Drains again once [until] has passed. One wait at a time. */
    private fun retryAfterWait(until: Instant) {
        synchronized(lock) {
            if (retry?.isActive == true) return
            retry = scope.launch {
                delay(Duration.between(clock.instant(), until).toMillis().coerceAtLeast(0) + 1)
                send()
            }
        }
    }

    /** Sends [change], or returns null when it left the queue before it went out. */
    private fun send(api: TogglApiClient, change: PendingChange): ApiResult<SentChange>? {
        if (change.kind == ChangeKind.CREATE) return tries.create(api, change)
        if (change.kind == ChangeKind.DELETE) return synchronized(deletes) { tries.delete(api, change) }
        if (change.kind == ChangeKind.STOP && change.capturedStop != null) return tries.capturedStop(api, change)
        return tries.update(api, change)
    }

    /**
     * Takes back the delete of the entry [entryId], the id the phone deleted it under. A delete
     * the sender has not sent leaves the queue, with no request. Otherwise the server restores the
     * entry with `POST /api/app/time_entries/{id}/restore`, a Tokkl-only endpoint, so call this
     * only when `Capabilities.restore` is true. Blocks the sender's deletes meanwhile.
     */
    suspend fun undoDelete(entryId: Long): UndoOutcome {
        val outcome = withContext(io) {
            synchronized(deletes) {
                when (val found = store.takeBackDelete(entryId)) {
                    TakenBack.Unsent -> UndoOutcome.Cancelled
                    is TakenBack.OnServer -> restore(found.serverId)
                    TakenBack.Unknown -> UndoOutcome.Failed(status = null)
                }
            }
        }
        if (outcome == UndoOutcome.Restored) withContext(io) { onSent() }
        return outcome
    }

    /** Asks the server to restore its entry [serverId], and records the answer. */
    private fun restore(serverId: Long): UndoOutcome {
        val api = client() ?: return UndoOutcome.NeedsServer
        val url = api.server.app("time_entries", serverId.toString(), "restore")
        // OkHttp sends a POST only with a body. Tokkl reads none.
        return when (val result = api.send("POST", url, JsonObject(emptyMap()), ServerTimeEntry.serializer())) {
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
                is ApiError.UnexpectedStatus -> if (error.status in PROXY_UNREACHABLE) UndoOutcome.NeedsServer else UndoOutcome.Failed(error.status)
                ApiError.AuthFailed -> UndoOutcome.Failed(ApiError.AuthFailed.status)
                is ApiError.HostNotAllowed -> UndoOutcome.Failed(status = null)
            }
        }
    }

    companion object {
        /** How long to wait after a 429 that names no whole number of seconds. */
        val DEFAULT_RETRY_AFTER: Duration = Duration.ofSeconds(60)

        /** Twice the farthest a snap moves a start: half of Tokkl's coarsest time resolution, an hour. */
        val LOOKUP_MARGIN: Duration = Duration.ofHours(1)

        /** Bad gateway, service unavailable and gateway timeout, which a proxy answers while its server is down. */
        private val PROXY_UNREACHABLE = setOf(502, 503, 504)
    }
}
