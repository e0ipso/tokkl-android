package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/**
 * Sends the queue to the configured server, oldest change first (docs/PRD.md O1). A failure stops
 * the drain and keeps the change queued, so the order holds and nothing is lost: no network, a
 * 5xx, a 429 and, until the queue learns what each means (TKA-0073), any other failure. After a
 * 429 the next try waits for `Retry-After`, or [DEFAULT_RETRY_AFTER] when the header is missing.
 * The wait is kept in memory, and the queue itself on disk.
 *
 * A queued start is the exception to "the change that reaches the server last wins" (O3), because
 * Tokkl stops the running entry when a new one starts. Before posting it, the sender reads the
 * running entry, one GET per start. When that entry started after the queued start, the start goes
 * as an entry stopped where the later timer starts, and the later timer keeps running (see
 * [ChangeStore.endBefore]). Otherwise the start goes as queued, and the server stops an earlier
 * timer itself.
 *
 * [send] runs when a change is queued and after each refresh, and once more when a 429's wait
 * ends. Background sending when the network returns comes with WorkManager (TKA-0037).
 *
 * @param client the signed-in account's client, or null when signed out. Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a drain.
 * @param onSent runs on [io] once a drain has sent at least one change, so the app can refresh:
 *   after the drain's last pass, not after each change or pass.
 */
class ChangeSender(
    private val store: ChangeStore,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val onSent: () -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
) {

    /** What one [drain] did. */
    sealed interface Outcome {
        /** How many changes the drain sent. */
        val sent: Int

        /** The queue is empty. */
        data class Empty(override val sent: Int) : Outcome

        /** The oldest change stays queued, because the server did not accept it. */
        data class Stopped(override val sent: Int, val error: ApiError) : Outcome

        /** A 429 asked the phone to wait until [until] before the next try. */
        data class Waiting(override val sent: Int, val until: Instant) : Outcome

        /** Signed out: nothing goes anywhere. */
        data class SignedOut(override val sent: Int) : Outcome
    }

    /** Guards [sending], [again] and [retry]. */
    private val lock = Any()

    private var sending: Job? = null

    /** A [send] call came while a drain ran, which may have looked at the queue before it. */
    private var again = false

    private var retry: Job? = null

    /** No request before this instant, after a 429. */
    @Volatile
    private var retryAt: Instant? = null

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
        // One refresh for the whole drain, however many changes and passes it took.
        if (sent > 0) onSent()
    }

    /**
     * Sends the queued changes in order until the queue is empty or one fails. Each sent change
     * leaves the queue as [ChangeStore.sent] describes. Blocks on the network and the disk, so
     * call it off the main thread, and from one thread at a time.
     */
    fun drain(): Outcome {
        var sent = 0
        while (true) {
            val wait = retryAt
            if (wait != null) {
                if (clock.instant().isBefore(wait)) return Outcome.Waiting(sent, wait)
                retryAt = null
            }
            val queued = store.first() ?: return Outcome.Empty(sent)
            val api = client() ?: return Outcome.SignedOut(sent)
            when (val result = send(api, queued)) {
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
                    return Outcome.Stopped(sent, error)
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

    private fun send(api: TogglApiClient, queued: PendingChange): ApiResult<Sent> {
        val change = if (queued.kind == ChangeKind.CREATE && queued.stop == null) {
            when (val result = endBeforeALaterTimer(api, queued)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> return result
            }
        } else {
            queued
        }
        val request = change.request(api.server)
        if (change.kind == ChangeKind.DELETE) {
            return when (val result = api.sendNoContent(request.method, request.url, request.body)) {
                is ApiResult.Success -> ApiResult.Success(Sent(change, change.entryId, entry = null))
                is ApiResult.Failure -> result
            }
        }
        return when (val result = api.send(request.method, request.url, request.body, ServerTimeEntry.serializer())) {
            is ApiResult.Success -> ApiResult.Success(Sent(change, result.value.id, result.value.toCached()))
            is ApiResult.Failure -> result
        }
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
        return ApiResult.Success(if (later == null) start else store.endBefore(start, later))
    }

    /** The change as it went to the server, the server's id for its entry, and the entry as the server stored it. */
    private class Sent(val change: PendingChange, val serverId: Long, val entry: CachedTimeEntry?)

    companion object {
        /** How long to wait after a 429 that names no whole number of seconds. */
        val DEFAULT_RETRY_AFTER: Duration = Duration.ofSeconds(60)
    }
}

/** One change as an HTTP request. */
internal class ChangeRequest(val method: String, val url: HttpUrl, val body: JsonObject?)

/** What the app names itself as in a created entry's `created_with`, which Toggl requires. */
internal const val CREATED_WITH = "Tokkl for Android"

/**
 * The Toggl API v9 request for this change, under [server]. A create is a POST, an update and a
 * stop a PUT with every field, so the stop keeps the tap time where Tokkl's `PATCH .../stop`
 * would take the server's clock. A delete is a DELETE. Times go in whole seconds, the precision
 * the server stores; a running entry sends `duration: -1`, and a stopped one the duration that
 * matches its start and stop. Tags go by name.
 */
internal fun PendingChange.request(server: ServerUrl): ChangeRequest {
    val collection = server.api("workspaces", workspaceId.toString(), "time_entries")
    val url = if (kind == ChangeKind.CREATE) collection else collection.newBuilder().addPathSegment(entryId.toString()).build()
    return when (kind) {
        ChangeKind.CREATE -> ChangeRequest("POST", url, body())
        ChangeKind.UPDATE, ChangeKind.STOP -> ChangeRequest("PUT", url, body())
        ChangeKind.DELETE -> ChangeRequest("DELETE", url, body = null)
    }
}

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
