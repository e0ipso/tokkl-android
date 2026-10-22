package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.jsTrim
import com.github.e0ipso.tokkl.trash.TrashEntry
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/**
 * Sends the queue to the configured server, oldest change first (docs/PRD.md O1). A failure the
 * phone can wait out stops the drain and keeps the change queued, so the order holds and nothing
 * is lost: no network, a 5xx, a 408 or a 429. After a 429 the next try waits for `Retry-After`,
 * or [DEFAULT_RETRY_AFTER] when the header is missing. The wait is kept in memory, and the queue
 * itself on disk.
 *
 * Each other refusal has its own outcome (docs/PRD.md O6):
 * - A 409 on a captured Stop is reconciled against the exact server span or Trash row. A legacy
 *   queued Stop without a captured instant retains its pre-migration handling.
 * - A 404 on an update or a legacy stop, when the server no longer has the entry, takes every queued
 *   change to it out of the queue and the entry out of the cache, and [onDiscarded] counts them
 *   for the discard notice. A 404 on a delete that may already have reached the server counts
 *   as sent. Any other 404, such as a create or
 *   an update whose project the server no longer has, is held as a 400 is.
 * - A 400, and any other 4xx, is held for the user with the server's message
 *   ([ChangeStore.hold]). Later changes to the same entry wait behind it, and the rest of the
 *   queue goes on.
 * - A 403, or a 401, pauses the whole queue and discards nothing: [signInNeeded] turns true, and
 *   no drain sends anything until [resume], which signing in again calls.
 *
 * A queued start is the exception to "the change that reaches the server last wins" (O3), because
 * Tokkl stops the running entry when a new one starts. Before posting it, the sender reads the
 * running entry, one GET per start. When that entry started after the queued start, the start goes
 * as an entry stopped where the later timer starts, and the later timer keeps running (see
 * [ChangeStore.endBefore]). Otherwise the start goes as queued, and the server stops an earlier
 * timer itself.
 *
 * A create reaches the server once. Before posting it, the sender marks it [PendingChange.maybeSent]
 * on disk, and clears the mark when a failure shows the server stored nothing, such as a 4xx or
 * no route. Neither Tokkl nor Toggl takes an idempotency key on a create, so before posting a
 * marked create again the sender reads the entries around its start, one more GET, and when one
 * that the phone did not know when it queued the create matches it ([couldHaveMade]) it takes
 * that entry's id instead of posting.
 *
 * Each try sends its change once: creates, updates and deletes go out with `resend` false, so
 * OkHttp does not send one again on a new connection by itself, and a lost answer always reaches
 * the sender, whose marks account for its own retries. The sender's reads may still go out again.
 * When a try's connection fails after the request left the phone ([worthAnotherTryAtOnce]), such
 * as a pooled connection the server had closed while it sat idle, the drain tries that change once
 * more at once, on a new connection, before it stops.
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
 *   after the drain's last pass, not after each change or pass. It runs after a restore too.
 * @param onEmptied runs on [io] once a drain that sent at least one change has left nothing to
 *   send, inside the drain's job and before [onSent], so the app can cancel a background retry
 *   that would find nothing to send.
 * @param onDiscarded runs on [io] with how many changes a 404 took out of the queue because the
 *   server no longer has their entry, so the app can say so.
 * @param serverStart where the server puts a start once the entry is stopped: Tokkl snaps it to
 *   the account's time resolution, and Toggl keeps it. Finds the entry a lost create made.
 * @param switchSpan the span the server stores for a running entry from `start` that a new timer
 *   stopped at `stop`: Tokkl puts both ends on the grid, the stop where the new timer's start
 *   snaps (`AccountTime.snapStop` without one step), and Toggl keeps them. A queued start ended
 *   before a later timer goes this way, so it does not overlap that timer.
 */
class ChangeSender(
    private val store: ChangeStore,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val onSent: () -> Unit = {},
    private val onEmptied: () -> Unit = {},
    private val onDiscarded: (Int) -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val serverStart: (Instant) -> Instant = { it },
    private val switchSpan: (start: Instant, stop: Instant) -> SnappedSpan = ::SnappedSpan,
) {

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
        if (sent > 0 && lastOutcome is Outcome.Empty) onEmptied()
        // One refresh for the whole drain, however many changes and passes it took.
        if (sent > 0) onSent()
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
    private fun send(api: TogglApiClient, change: PendingChange): ApiResult<Sent>? {
        if (change.kind == ChangeKind.CREATE) return create(api, change)
        if (change.kind == ChangeKind.DELETE) return delete(api, change)
        if (change.kind == ChangeKind.STOP && change.capturedStop != null) return capturedStop(api, change)
        val request = change.request(api.server)
        return when (val result = api.send(request.method, request.url, request.body, ServerTimeEntry.serializer(), resend = false)) {
            is ApiResult.Success -> ApiResult.Success(Sent(change, result.value.id, result.value.toCached()))
            is ApiResult.Failure -> result
        }
    }

    /** A captured Stop may already have reached the server even when its reply was lost. */
    private fun capturedStop(api: TogglApiClient, change: PendingChange): ApiResult<Sent> {
        if (change.maybeSent) {
            when (val found = stoppedOnServer(api, change)) {
                is ApiResult.Success -> if (found.value) return ApiResult.Success(Sent(change, change.entryId, if (change.shortStop) null else change.entry()))
                is ApiResult.Failure -> return found
            }
        }
        store.maybeSent(change, true)
        val request = change.request(api.server)
        return when (val result = api.send(request.method, request.url, request.body, ServerTimeEntry.serializer(), resend = false)) {
            is ApiResult.Success -> if (result.value.matchesStop(change)) {
                ApiResult.Success(Sent(change, result.value.id, if (change.shortStop) null else result.value.toCached()))
            } else {
                ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The server stored a different Stop outcome"))
            }
            is ApiResult.Failure -> result
        }
    }

    /** True only after reading the exact stopped row, including a short row hidden in Trash. */
    private fun stoppedOnServer(api: TogglApiClient, change: PendingChange): ApiResult<Boolean> {
        val url = api.server.api("me", "time_entries", change.entryId.toString())
        return when (val result = api.get(url, ServerTimeEntry.serializer())) {
            is ApiResult.Success -> when {
                result.value.stop == null -> ApiResult.Success(false)
                result.value.matchesStop(change) -> ApiResult.Success(true)
                else -> ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The server stopped this entry differently"))
            }
            is ApiResult.Failure -> if (change.shortStop && (result.error as? ApiError.Http)?.status == NOT_FOUND) {
                when (val trash = api.get(api.server.app("trash"), ListSerializer(TrashEntry.serializer()))) {
                    is ApiResult.Success -> if (trash.value.any { it.matchesStop(change) }) ApiResult.Success(true)
                        else ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The short Stop is not in Trash"))
                    is ApiResult.Failure -> trash
                }
            } else result
        }
    }

    /**
     * Posts the queued create [queued] once: when an earlier post may have stored it, the entry
     * that post made stands for it instead. A start goes as [endBeforeALaterTimer] has it.
     */
    private fun create(api: TogglApiClient, queued: PendingChange): ApiResult<Sent> {
        if (queued.maybeSent) {
            when (val found = earlierCopy(api, queued)) {
                is ApiResult.Success -> found.value?.let { return ApiResult.Success(Sent(queued, it.id, it.toCached())) }
                is ApiResult.Failure -> return found
            }
        }
        val change = if (queued.stop == null) {
            when (val result = endBeforeALaterTimer(api, queued)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> return result
            }
        } else {
            queued
        }
        if (!change.maybeSent) store.maybeSent(change, true)
        val request = change.request(api.server)
        return when (val result = api.send(request.method, request.url, request.body, ServerTimeEntry.serializer(), resend = false)) {
            is ApiResult.Success -> ApiResult.Success(Sent(change, result.value.id, result.value.toCached()))
            is ApiResult.Failure -> {
                if (result.error.leftNothingOnTheServer()) store.maybeSent(change, false)
                result
            }
        }
    }

    /**
     * Sends the queued delete [change], marked [PendingChange.maybeSent] first, or returns null
     * when Undo took it back before then. A 404 on a retry counts as sent: the server no longer has the
     * entry, because an earlier try or another client deleted it, which is what the delete asked
     * for (docs/PRD.md O6). On a first try, the mark clears when another failure shows that the
     * server deleted nothing. Once a try may have reached the server, the mark stays whatever
     * later tries get, so Undo goes through the server.
     */
    private fun delete(api: TogglApiClient, change: PendingChange): ApiResult<Sent>? =
        synchronized(deletes) {
            if (!store.maybeSent(change, true)) return null
            val request = change.request(api.server)
            when (val result = api.sendNoContent(request.method, request.url, request.body, resend = false)) {
                is ApiResult.Success -> ApiResult.Success(Sent(change, change.entryId, entry = null))
                is ApiResult.Failure -> when {
                    (result.error as? ApiError.Http)?.status == NOT_FOUND && change.maybeSent -> ApiResult.Success(Sent(change, change.entryId, entry = null))
                    !change.maybeSent -> {
                        if (result.error.leftNothingOnTheServer()) store.maybeSent(change, false)
                        result
                    }
                    else -> result
                }
            }
        }

    /**
     * What the drain does with [change], which the server, or the way to it, refused with [error]
     * (docs/PRD.md O6). A 404 on an update or a stop reads the entry, one GET, to tell an entry
     * the server no longer has from a value it no longer has, such as a deleted project.
     */
    private fun refusal(api: TogglApiClient, change: PendingChange, error: ApiError): Refusal {
        if (error == ApiError.AuthFailed) return Refusal.SignInAgain
        val refused = error as? ApiError.UnexpectedStatus ?: return Refusal.Wait(error)
        val status = refused.status
        return when {
            status == UNAUTHORIZED -> Refusal.SignInAgain
            status !in 400..499 || status == REQUEST_TIMEOUT -> Refusal.Wait(error)
            status == CONFLICT && change.kind == ChangeKind.STOP && change.capturedStop == null -> Refusal.AlreadyDone
            change.kind == ChangeKind.STOP && change.capturedStop != null && status in setOf(CONFLICT, NOT_FOUND) ->
                when (val found = stoppedOnServer(api, change)) {
                    is ApiResult.Success -> if (found.value) Refusal.AlreadyDone else Refusal.Hold(refused.message)
                    is ApiResult.Failure -> if (found.error is ApiError.Http && found.error.status in 400..499) {
                        Refusal.Hold(refused.message ?: "Captured Stop could not be verified")
                    } else Refusal.Wait(found.error)
                }
            status == NOT_FOUND && change.kind != ChangeKind.CREATE -> when (val entry = entryOnServer(api, change.entryId)) {
                is ApiResult.Success -> if (entry.value) Refusal.Hold(refused.message) else Refusal.EntryGone
                is ApiResult.Failure -> if (entry.error == ApiError.AuthFailed || (entry.error as? ApiError.Http)?.status == UNAUTHORIZED) {
                    Refusal.SignInAgain
                } else {
                    Refusal.Wait(entry.error)
                }
            }
            else -> Refusal.Hold(refused.message)
        }
    }

    /** Whether the server has the entry [id], preserving a failed lookup's retry or sign-in outcome. */
    private fun entryOnServer(api: TogglApiClient, id: Long): ApiResult<Boolean> =
        when (val result = api.get(api.server.api("me", "time_entries", id.toString()), JsonObject.serializer())) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Failure -> when {
                (result.error as? ApiError.Http)?.status == NOT_FOUND -> ApiResult.Success(false)
                result.error is ApiError.InvalidResponse -> ApiResult.Success(true)
                else -> result
            }
        }

    /** What the drain does with a refused change. */
    private sealed interface Refusal {
        /** The failure may pass: stop the drain and keep the change. */
        data class Wait(val error: ApiError) : Refusal

        /** The server refused the token: pause the queue until the user signs in again. */
        data object SignInAgain : Refusal

        /** The server has what the change asked for already: it leaves the queue. */
        data object AlreadyDone : Refusal

        /** The server no longer has the entry: every change to it leaves the queue, counted. */
        data object EntryGone : Refusal

        /** Only the user can settle it: hold it with the server's [reason]. */
        data class Hold(val reason: String?) : Refusal
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

    /**
     * The entry an earlier post of [create] made, or null when the server has none: one that
     * [couldHaveMade] it, among the entries that start within [LOOKUP_MARGIN] of it, that the
     * server did not hold already when the create was queued ([PendingChange.existingIds]), such
     * as a duplicate's source, and that no other create from the phone maps to already.
     */
    private fun earlierCopy(api: TogglApiClient, create: PendingChange): ApiResult<ServerTimeEntry?> {
        val start = create.start.truncatedTo(ChronoUnit.SECONDS)
        val url = api.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", start.minus(LOOKUP_MARGIN).toString())
            .addQueryParameter("end_date", start.plus(LOOKUP_MARGIN).toString())
            .build()
        val entries = when (val result = api.get(url, ListSerializer(ServerTimeEntry.serializer()))) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result
        }
        val starts = setOf(start, serverStart(start))
        return ApiResult.Success(
            entries.firstOrNull { create.couldHaveMade(it, starts) && it.id !in create.existingIds && !store.isMapped(it.id) },
        )
    }

    /**
     * The queued [start] as it goes to the server: as queued while nothing runs there or the
     * running entry started at the same time or earlier, or ended where the running entry started
     * when that came later (docs/PRD.md O3).
     */
    private fun endBeforeALaterTimer(api: TogglApiClient, start: PendingChange): ApiResult<PendingChange> {
        val current = api.get(api.server.api("me", "time_entries", "current"), ServerTimeEntry.serializer().nullable)
        val running = when (current) {
            is ApiResult.Success -> current.value?.toCached()
            is ApiResult.Failure -> return current
        }
        val later = running?.takeIf { it.stop == null && it.start.isAfter(start.start.truncatedTo(ChronoUnit.SECONDS)) }
        return ApiResult.Success(if (later == null) start else store.endBefore(start, later, switchSpan))
    }

    /** The change as it went to the server, the server's id for its entry, and the entry as the server stored it. */
    private class Sent(val change: PendingChange, val serverId: Long, val entry: CachedTimeEntry?)

    companion object {
        /** How long to wait after a 429 that names no whole number of seconds. */
        val DEFAULT_RETRY_AFTER: Duration = Duration.ofSeconds(60)

        /** Twice the farthest a snap moves a start: half of Tokkl's coarsest time resolution, an hour. */
        val LOOKUP_MARGIN: Duration = Duration.ofHours(1)

        /** Bad gateway, service unavailable and gateway timeout, which a proxy answers while its server is down. */
        private val PROXY_UNREACHABLE = setOf(502, 503, 504)

        /** What Tokkl answers to a delete of an entry it does not have, or has in the trash. */
        private const val NOT_FOUND = 404

        /** What a server may answer to a token it does not take, where Tokkl answers 403. */
        private const val UNAUTHORIZED = 401

        /** The server gave up waiting for the request, which a later try may get past. */
        private const val REQUEST_TIMEOUT = 408

        /** What a server answers to a stop of an entry stopped already, as Tokkl's stop endpoint does. */
        private const val CONFLICT = 409
    }
}

/**
 * True when [entry] could be the one this create made: same workspace, description, project, task
 * and tags, the tags as [tagKey] compares them, and a start among [starts], the create's own and
 * where the server snaps it. Tokkl leaves `created_with` out of its answers, so that cannot tell
 * entries apart. Billable and the stop do not count, so the entry still matches after the server
 * or another client changed them.
 */
internal fun PendingChange.couldHaveMade(entry: ServerTimeEntry, starts: Set<Instant>): Boolean {
    val made = entry.toCached() ?: return false
    return made.workspaceId == workspaceId &&
        made.description == description &&
        made.projectId == projectId &&
        made.taskId == taskId &&
        made.tags.mapTo(HashSet(), ::tagKey) == tags.mapTo(HashSet(), ::tagKey) &&
        made.start in starts
}

/**
 * The tag [name] as Tokkl matches it to a tag it has: trimmed, with A to Z in lower case. Tokkl
 * 1.3.1 trims each name a create sends (`requireName` in packages/api/src/lib/values.ts) and looks
 * it up with SQLite's `COLLATE NOCASE` (`findByName` in packages/api/src/models/tags.ts), which
 * folds ASCII letters only. So an entry queued with "work" comes back tagged "Work" when the
 * server has that tag.
 */
private fun tagKey(name: String): String =
    buildString { name.jsTrim().forEach { append(if (it in 'A'..'Z') it.lowercaseChar() else it) } }

/**
 * True when the request that failed with this error cannot have changed anything on the server:
 * the server refused it with a 4xx, or it never left the phone, as with no route or a trust
 * refusal. A 5xx, a timeout, a dropped connection or a 2xx the phone could not read may come after
 * the server stored it.
 */
internal fun ApiError.leftNothingOnTheServer(): Boolean =
    when (this) {
        is ApiError.Http -> status in 400..499
        is ApiError.Network -> cause is ConnectException || cause is UnknownHostException || cause is NoRouteToHostException
        is ApiError.InvalidResponse -> false
        is ApiError.HostNotAllowed, is ApiError.TrustRefused -> true
    }

/**
 * True when a request that failed with this error is worth sending again at once: its connection
 * failed after the request left the phone, as a pooled connection does when the server closed it
 * while it sat idle. Tokkl closes one after Node's keep-alive timeout of 5 seconds, and OkHttp
 * checks a pooled connection only after 10. A timeout would wait as long again, and a server the
 * phone cannot reach at all stays unreachable a moment later.
 */
internal fun ApiError.worthAnotherTryAtOnce(): Boolean =
    this is ApiError.Network && cause !is InterruptedIOException && !leftNothingOnTheServer()

/** One change as an HTTP request. */
internal class ChangeRequest(val method: String, val url: HttpUrl, val body: JsonObject?)

/** What the app names itself as in a created entry's `created_with`, which Toggl requires. */
internal const val CREATED_WITH = "Tokkl for Android"

/**
 * The Toggl API v9 request for this change, under [server]. A create is a POST, an update and a
 * legacy stop a PUT with every field; a new Stop sends its frozen tap instant as PATCH. A delete is
 * a DELETE and a short Stop's Undo is a POST restore. Times go in whole seconds, the precision
 * the server stores; a running entry sends `duration: -1`, and a stopped one the duration that
 * matches its start and stop. Tags go by name.
 */
internal fun PendingChange.request(server: ServerUrl): ChangeRequest {
    val collection = server.api("workspaces", workspaceId.toString(), "time_entries")
    val url = if (kind == ChangeKind.CREATE) collection else collection.newBuilder().addPathSegment(entryId.toString()).build()
    return when (kind) {
        ChangeKind.CREATE -> ChangeRequest("POST", url, body())
        ChangeKind.UPDATE -> ChangeRequest("PUT", url, body())
        ChangeKind.STOP -> if (capturedStop == null) ChangeRequest("PUT", url, body()) else
            ChangeRequest("PATCH", url.newBuilder().addPathSegment("stop").build(), buildJsonObject {
                put("stop", capturedStop.truncatedTo(ChronoUnit.SECONDS).toString())
            })
        ChangeKind.RESTORE -> ChangeRequest("POST", server.app("time_entries", entryId.toString(), "restore"), JsonObject(emptyMap()))
        ChangeKind.DELETE -> ChangeRequest("DELETE", url, body = null)
    }
}

// Android 12's Instant.parse rejects Tokkl's +00:00 timestamps; OffsetDateTime accepts them.
private fun ServerTimeEntry.matchesStop(change: PendingChange): Boolean =
    id == change.entryId && workspaceId == change.workspaceId &&
        runCatching { OffsetDateTime.parse(start).toInstant() }.getOrNull() == change.start.truncatedTo(ChronoUnit.SECONDS) &&
        stop?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } == change.stop?.truncatedTo(ChronoUnit.SECONDS) &&
        (serverDeletedAt != null) == change.shortStop

private fun TrashEntry.matchesStop(change: PendingChange): Boolean =
    id == change.entryId && deletedAt != null &&
        runCatching { OffsetDateTime.parse(start).toInstant() }.getOrNull() == change.start.truncatedTo(ChronoUnit.SECONDS) &&
        stop?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } == change.stop?.truncatedTo(ChronoUnit.SECONDS)

private fun PendingChange.body(): JsonObject {
    val start = start.truncatedTo(ChronoUnit.SECONDS)
    val stop = stop?.truncatedTo(ChronoUnit.SECONDS)
    return buildJsonObject {
        put("workspace_id", workspaceId)
        if (kind == ChangeKind.CREATE) put("created_with", CREATED_WITH)
        put("description", description)
        put("project_id", projectId)
        put("task_id", taskId)
        put("tags", JsonArray(tags.map(::JsonPrimitive)))
        put("billable", billable)
        put("start", start.toString())
        if (stop == null) {
            put("stop", JsonNull)
            put("duration", -1)
        } else {
            put("stop", stop.toString())
            put("duration", stop.epochSecond - start.epochSecond)
        }
    }
}
