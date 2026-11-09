package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.capabilities.AllDayOffer
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/** What a [ChangeSender]'s drains tell the app, each on the sender's IO context. */
interface ChangeSenderEvents {

    /**
     * A quiet drain sent at least one change, so the app can refresh: after a quiet drain, not
     * after each change or pass. A change queued meanwhile drains before the drain ends.
     */
    fun sent()

    /**
     * A quiet drain that sent at least one change left nothing to send. It comes inside the drain,
     * before [sent], so the app can cancel a background retry that would find nothing.
     */
    fun emptied()

    /** A 404 took [count] changes out of the queue because the server no longer has their entry. */
    fun discarded(count: Int)
}

/**
 * Sends the queue to the configured server, oldest change first (docs/PRD.md O1). A failure the
 * phone can wait out stops the drain and keeps the change queued, so the order holds and nothing
 * is lost: no network, a 5xx, a 408 or a 429. After a 429 the next try waits as [RetryAfter]
 * says.
 *
 * Each other refusal has its own outcome, as [refusal] describes (docs/PRD.md O6). An entry the
 * server no longer has takes every queued change to it out of the queue and the entry out of the
 * cache, and [ChangeSenderEvents.discarded] counts them for the discard notice. A 404 on a
 * delete that may already have reached the server counts as sent. A held change waits for the
 * user with the server's message ([ChangeStore.hold]); later changes to the same entry wait behind
 * it, and the rest of the queue goes on. A 403, or a 401, pauses the whole queue and discards
 * nothing: [signInNeeded] turns true, and no drain sends anything until [resume], which signing
 * in again calls.
 *
 * [ChangeTries] sends each change once and says how a queued start goes when a later timer runs
 * (O3) and how a create that may have reached the server is found instead of posted again. When a
 * try's connection fails after the request left the phone ([worthAnotherTryAtOnce]), such as a
 * pooled connection the server had closed while it sat idle, the drain tries that change once
 * more at once, on a new connection, before it stops. The sender's reads may still go out again.
 *
 * One coroutine owns the drains and the 429 wait: it takes the requests from a channel, drains,
 * and drains again while more requests came meanwhile. [send] runs when a change is queued and
 * after each refresh, and once more when a 429's wait ends. When the network returns,
 * [BackgroundSend]'s worker calls [sendAndWait], also with the app closed. Each delete goes out
 * through [undo], so an Undo never decides while a delete is on its way.
 *
 * @param entries opens the entry calls of the signed-in account, or gives null when signed out.
 *   Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a drain.
 * @param events hears on [io] what the drains did, as [ChangeSenderEvents] says.
 * @param spans where the server puts a start once the entry is stopped, which finds the entry a
 *   lost create made, and the span of a queued start ended before a later timer.
 * @param capabilities whether the server stores no-time entries (`Capabilities.allDay`), read for
 *   each request. While it is false no request carries `all_day`, as Toggl Track needs.
 * @param undo takes deletes back; the sender sends each delete through it.
 */
class ChangeSender(
    private val store: ChangeStore,
    private val entries: suspend () -> EntrySends?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val events: ChangeSenderEvents,
    private val clock: Clock,
    private val spans: ServerSpans,
    private val capabilities: AllDayOffer,
    private val undo: DeleteUndoer,
) : AccountDataOwner,
    QueueSender,
    SignInPause {

    override val signOutPhase = SignOutPhase.FINISH_SENDING

    override suspend fun forgetAccount() = stop()

    private val tries = ChangeTries(store, spans, capabilities)

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

    /** What the drain coroutine is asked to do. */
    private sealed interface Request {
        /** Drain until quiet. [done] hears how the last pass ended, or null when it failed on the disk. */
        class Drain(val done: CompletableDeferred<Outcome?>?) : Request

        /** Forget the 429 wait and the paused sign-in, once the drains before have ended. */
        class Stop(val done: CompletableDeferred<Unit>) : Request
    }

    /** Callers on any thread hand requests in without blocking; the drain coroutine takes them in order. */
    private val requests = Channel<Request>(Channel.UNLIMITED)

    /** The 429 wait. Only the drain coroutine reads and changes it. */
    private val retryAfter = RetryAfter(clock)

    /** The drain that starts when a 429's wait ends. Only the drain coroutine reads and changes it. */
    private var retry: Job? = null

    private val mutableSignInNeeded = MutableStateFlow(false)

    /**
     * True once the server refused the session's token for a queued change (docs/PRD.md O6). The
     * queue then sends nothing and discards nothing until [resume].
     */
    override val signInNeeded: StateFlow<Boolean> = mutableSignInNeeded.asStateFlow()

    init {
        scope.launch(io) { serveRequests() }
    }

    /**
     * The server refused the session's token on another request, as the timer event stream's 401
     * or 403. Pauses the queue as its own refusal would, so the status asks to sign in again, until
     * [resume].
     */
    override fun signInRefused() {
        mutableSignInNeeded.value = true
    }

    /** Lifts the pause after a refused token, once the user has signed in again, and drains the queue. */
    override fun resume() {
        mutableSignInNeeded.value = false
        send()
    }

    /**
     * Drains the queue in the background. A call while a drain runs makes that drain go once
     * more, so a change queued meanwhile does not wait for the next event. Returns at once.
     */
    fun send() {
        requests.trySend(Request.Drain(done = null))
    }

    /**
     * Drains the queue as [send] does and waits for the drain to end, including the passes that
     * calls during it asked for. Returns how its last pass ended, or null when that pass failed on
     * the disk. Cancelling the caller leaves the drain running.
     */
    suspend fun sendAndWait(): Outcome? {
        val done = CompletableDeferred<Outcome?>()
        requests.send(Request.Drain(done))
        return done.await()
    }

    /** Drains the queue as [sendAndWait] does, for a caller that needs no outcome. */
    override suspend fun sendQueued() {
        sendAndWait()
    }

    /**
     * Waits for the drains asked for before this call to end, then cancels the wait after a 429
     * and forgets it with the paused sign-in. Signing out calls this once [entries] gives null,
     * so the drain ends at its next change and nothing it writes lands after the cache is cleared.
     */
    suspend fun stop() {
        val done = CompletableDeferred<Unit>()
        requests.send(Request.Stop(done))
        done.await()
    }

    /** The drain coroutine: serves each request in turn, for as long as [scope] lives. */
    private suspend fun serveRequests() {
        var next: Request? = null
        while (true) {
            next = when (val request = next ?: requests.receive()) {
                is Request.Stop -> {
                    retry?.cancel()
                    retry = null
                    retryAfter.forget()
                    mutableSignInNeeded.value = false
                    request.done.complete(Unit)
                    null
                }

                is Request.Drain -> drainUntilQuiet(request)
            }
        }
    }

    /** The callers of one drain, and a stop request that came during it, which waits for the drain. */
    private class Batch(first: Request.Drain) {
        val waiting = mutableListOf(first)
        var stop: Request.Stop? = null
    }

    /**
     * Drains for [first] and for every drain request that comes before the queue is quiet, then
     * tells their callers how the last pass ended. The events go out after the last pass that
     * sent something, while a request that comes during them still gets another pass. Returns a
     * stop request it met, which waits for this drain and is served next.
     */
    private suspend fun drainUntilQuiet(first: Request.Drain): Request.Stop? {
        val batch = Batch(first)
        var outcome: Outcome? = null
        try {
            var sent = 0
            var quiet = false
            while (!quiet) {
                outcome = drainPass()
                sent += outcome?.sent ?: 0
                if (!tookMore(batch)) {
                    if (sent > 0 && outcome is Outcome.Empty) events.emptied()
                    if (sent > 0) events.sent()
                    sent = 0
                    quiet = !tookMore(batch)
                }
            }
        } finally {
            batch.waiting.forEach { it.done?.complete(outcome) }
        }
        return batch.stop
    }

    /** Takes the requests that came meanwhile into [batch]. True when a drain request was among them. */
    private fun tookMore(batch: Batch): Boolean {
        var more = false
        while (batch.stop == null) {
            when (val request = requests.tryReceive().getOrNull() ?: break) {
                is Request.Drain -> {
                    batch.waiting += request
                    more = true
                }

                is Request.Stop -> batch.stop = request
            }
        }
        return more
    }

    /** One [drain], or null when it failed on the disk, with the retry after a 429 it met. */
    private suspend fun drainPass(): Outcome? {
        val outcome = try {
            drain()
        } catch (_: SQLiteException) {
            // The queue stays on disk for the next try.
            null
        }
        if (outcome is Outcome.Waiting) retryAfterWait(outcome.until)
        return outcome
    }

    /**
     * Sends the queued changes in order until nothing is left to send or one fails in a way the
     * phone can wait out. Each sent change leaves the queue as [ChangeStore.sent] describes, and
     * each refused one as [ChangeSender] describes. Blocks on the disk, so call it on the IO
     * dispatcher, and from one coroutine at a time: the drain coroutine. Callers outside it go
     * through [send] or [sendAndWait]; this module's tests call it directly.
     */
    internal suspend fun drain(): Outcome {
        var sent = 0
        // The change this drain already tried once more, by its queue sequence.
        var triedAgain: Long? = null
        while (true) {
            if (mutableSignInNeeded.value) return Outcome.Stopped(sent, ApiError.AuthFailed)
            retryAfter.pending()?.let { return Outcome.Waiting(sent, it) }
            val queued = store.first() ?: return Outcome.Empty(sent)
            val server = entries() ?: return Outcome.SignedOut(sent)
            when (val result = send(server, queued)) {
                // Undo took the delete back before it went out.
                null -> Unit

                is ApiResult.Success -> {
                    store.sent(result.value.change, result.value.serverId, result.value.entry)
                    sent++
                }

                is ApiResult.Failure -> {
                    val error = result.error
                    if (error is ApiError.RateLimited) return waitOut(sent, error)
                    // Read the change again: the failed try may have marked it maybeSent.
                    if (error.worthAnotherTryAtOnce() && triedAgain != queued.sequence) {
                        triedAgain = queued.sequence
                        continue
                    }
                    when (val step = settleRefused(server, queued, error, sent)) {
                        is RefusalStep.Next -> sent += step.left
                        is RefusalStep.Stop -> return step.outcome
                    }
                }
            }
        }
    }

    /** What [drain] does after it settled a refused change: go on, or stop with [Stop.outcome]. */
    private sealed interface RefusalStep {
        /** Go on with the next change. [left] changes left the queue. */
        data class Next(val left: Int) : RefusalStep

        data class Stop(val outcome: Outcome) : RefusalStep
    }

    /** Settles [queued], which the server refused with [error], after [sent] changes went out. */
    private suspend fun settleRefused(
        server: EntrySends,
        queued: PendingChange,
        error: ApiError,
        sent: Int,
    ): RefusalStep =
        when (val refused = refusal(server, queued, error)) {
            is Refusal.Wait -> {
                val waiting = refused.error
                RefusalStep.Stop(
                    if (waiting is ApiError.RateLimited) waitOut(sent, waiting) else Outcome.Stopped(sent, waiting),
                )
            }

            Refusal.SignInAgain -> {
                mutableSignInNeeded.value = true
                RefusalStep.Stop(Outcome.Stopped(sent, ApiError.AuthFailed))
            }

            Refusal.AlreadyDone -> {
                store.sent(queued, queued.entryId, answer = null)
                RefusalStep.Next(1)
            }

            Refusal.EntryGone -> {
                val discarded = store.discardEntry(queued)
                if (discarded > 0) events.discarded(discarded)
                RefusalStep.Next(discarded)
            }

            is Refusal.Hold -> {
                store.hold(queued, refused.reason)
                RefusalStep.Next(0)
            }
        }

    /** Waits out the server's rate limit [error] before the next send. */
    private fun waitOut(sent: Int, error: ApiError.RateLimited): Outcome =
        Outcome.Waiting(sent, retryAfter.start(error))

    /** Drains again once [until] has passed. One wait at a time. */
    private fun retryAfterWait(until: Instant) {
        if (retry?.isActive == true) return
        retry = scope.launch(io) {
            delay(Duration.between(clock.instant(), until).toMillis().coerceAtLeast(0) + 1)
            send()
        }
    }

    /** Sends [change], or returns null when it left the queue before it went out. */
    private suspend fun send(server: EntrySends, change: PendingChange): ApiResult<SentChange>? {
        if (change.kind == ChangeKind.CREATE) return tries.create(server, change)
        if (change.kind == ChangeKind.DELETE) return undo.sendingDelete { tries.delete(server, change) }
        if (change.kind == ChangeKind.STOP && change.capturedStop != null) return tries.capturedStop(server, change)
        return tries.update(server, change)
    }

    companion object {
        /** Twice the farthest a snap moves a start: half of Tokkl's coarsest time resolution, an hour. */
        val LOOKUP_MARGIN: Duration = Duration.ofHours(1)
    }
}

/**
 * The queue's pause after the server refused the session's token (docs/PRD.md O6), as
 * [ChangeSender] keeps it, for the sign-in, the sync status and the timer event stream, which read
 * or change nothing else of the sender.
 */
interface SignInPause {

    /** True from a refused token until [resume]: the queue sends nothing meanwhile. */
    val signInNeeded: StateFlow<Boolean>

    /** Another request's token was refused: pauses the queue as its own refusal would. */
    fun signInRefused()

    /** Lifts the pause once the user has signed in again, and drains the queue. */
    fun resume()
}

/**
 * Drains the queue and waits for it, as the timer's surfaces and pages ask before a server action.
 * [ChangeSender] sends over Room and the network; their unit tests stand in for it.
 */
fun interface QueueSender {
    suspend fun sendQueued()
}
