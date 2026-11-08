package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.builtins.nullable
import java.time.temporal.ChronoUnit

/** The change as it went to the server, the server's id for its entry, and the entry as the server stored it. */
internal class SentChange(val change: PendingChange, val serverId: Long, val entry: CachedTimeEntry?)

/**
 * One try at sending one queued change, by kind, for [ChangeSender]. Each try sends its change
 * once: creates, updates and deletes go out with `resend` false, so OkHttp does not send one again
 * on a new connection by itself, and a lost answer always reaches the sender, whose
 * [PendingChange.maybeSent] marks account for its own retries. Blocks on the disk, so call it on the
 * IO dispatcher.
 *
 * @param spans where the server puts a start once the entry is stopped, which finds the entry a
 *   lost create made, and the span of a queued start ended before a later timer.
 * @param allDay whether the server stores no-time entries, read for each request.
 */
internal class ChangeTries(
    private val store: ChangeStore,
    private val spans: ServerSpans,
    private val allDay: () -> Boolean,
) {

    /** Sends an update, a legacy stop or a restore as [request] builds it. */
    suspend fun update(api: TogglApiClient, change: PendingChange): ApiResult<SentChange> {
        val request = change.request(api.server, allDay())
        return when (
            val result = api.send(
                request.method,
                request.url,
                request.body,
                ServerTimeEntry.serializer(),
                resend = false,
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(SentChange(change, result.value.id, result.value.toCached()))
            is ApiResult.Failure -> result
        }
    }

    /** A captured Stop may already have reached the server even when its reply was lost. */
    suspend fun capturedStop(api: TogglApiClient, change: PendingChange): ApiResult<SentChange> {
        if (change.maybeSent) {
            when (val found = stoppedOnServer(api, change)) {
                is ApiResult.Success -> if (found.value) {
                    return ApiResult.Success(
                        SentChange(change, change.entryId, if (change.shortStop) null else change.entry()),
                    )
                }

                is ApiResult.Failure -> return found
            }
        }
        store.maybeSent(change, true)
        val request = change.request(api.server, allDay())
        return when (
            val result = api.send(
                request.method,
                request.url,
                request.body,
                ServerTimeEntry.serializer(),
                resend = false,
            )
        ) {
            is ApiResult.Success -> if (result.value.matchesStop(change)) {
                ApiResult.Success(
                    SentChange(change, result.value.id, if (change.shortStop) null else result.value.toCached()),
                )
            } else {
                ApiResult.Failure(ApiError.UnexpectedStatus(CONFLICT, "The server stored a different Stop outcome"))
            }

            is ApiResult.Failure -> result
        }
    }

    /**
     * Posts the queued create [queued] once. Before posting it, marks it [PendingChange.maybeSent]
     * on disk, and clears the mark when a failure shows the server stored nothing, such as a 4xx or
     * no route. Neither Tokkl nor Toggl takes an idempotency key on a create, so before posting a
     * marked create again it reads the entries around its start ([earlierCopy]), and the entry an
     * earlier post made stands for it instead. A start goes as [endBeforeALaterTimer] has it.
     */
    suspend fun create(api: TogglApiClient, queued: PendingChange): ApiResult<SentChange> {
        if (queued.maybeSent) {
            when (val found = earlierCopy(api, queued, spans, store)) {
                is ApiResult.Success -> found.value?.let {
                    return ApiResult.Success(
                        SentChange(queued, it.id, it.toCached()),
                    )
                }

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
        val request = change.request(api.server, allDay())
        return when (
            val result = api.send(
                request.method,
                request.url,
                request.body,
                ServerTimeEntry.serializer(),
                resend = false,
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(SentChange(change, result.value.id, result.value.toCached()))

            is ApiResult.Failure -> {
                if (result.error.leftNothingOnTheServer()) store.maybeSent(change, false)
                result
            }
        }
    }

    /**
     * Sends the queued delete [change], marked [PendingChange.maybeSent] first, or returns null
     * when Undo took it back before then. The caller holds the lock Undo holds. A 404 on a retry
     * counts as sent: the server no longer has the entry, because an earlier try or another client
     * deleted it, which is what the delete asked for (docs/PRD.md O6). On a first try, the mark
     * clears when another failure shows that the server deleted nothing. Once a try may have
     * reached the server, the mark stays whatever later tries get, so Undo goes through the server.
     */
    suspend fun delete(api: TogglApiClient, change: PendingChange): ApiResult<SentChange>? {
        if (!store.maybeSent(change, true)) return null
        val request = change.request(api.server, allDay())
        return when (val result = api.sendNoContent(request.method, request.url, request.body, resend = false)) {
            is ApiResult.Success -> ApiResult.Success(SentChange(change, change.entryId, entry = null))

            is ApiResult.Failure -> when {
                (result.error as? ApiError.Http)?.status == NOT_FOUND && change.maybeSent -> ApiResult.Success(
                    SentChange(change, change.entryId, entry = null),
                )

                !change.maybeSent -> {
                    if (result.error.leftNothingOnTheServer()) store.maybeSent(change, false)
                    result
                }

                else -> result
            }
        }
    }

    /**
     * The queued [start] as it goes to the server (docs/PRD.md O3). A queued start is the exception
     * to "the change that reaches the server last wins", because Tokkl stops the running entry when
     * a new one starts. So this reads the running entry, one GET per start. When that entry started
     * after the queued start, the start goes as an entry stopped where the later timer starts, and
     * the later timer keeps running ([ChangeStore.endBefore]). Otherwise the start goes as queued,
     * and the server stops an earlier timer itself.
     */
    private suspend fun endBeforeALaterTimer(api: TogglApiClient, start: PendingChange): ApiResult<PendingChange> {
        val current = api.get(api.server.api("me", "time_entries", "current"), ServerTimeEntry.serializer().nullable)
        val running = when (current) {
            is ApiResult.Success -> current.value?.toCached()
            is ApiResult.Failure -> return current
        }
        val later = running?.takeIf { it.stop == null && it.start.isAfter(start.start.truncatedTo(ChronoUnit.SECONDS)) }
        return ApiResult.Success(if (later == null) start else store.endBefore(start, later, spans::switched))
    }
}
