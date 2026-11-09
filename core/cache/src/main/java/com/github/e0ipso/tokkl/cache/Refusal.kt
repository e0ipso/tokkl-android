package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.model.EntryId

/** What the drain does with a refused change. */
internal sealed interface Refusal {
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
 * What the drain does with [change], which the server, or the way to it, refused with [error]
 * (docs/PRD.md O6):
 * - A 409 on a captured Stop is reconciled against the exact server span or Trash row. A legacy
 *   queued Stop without a captured instant retains its pre-migration handling.
 * - A 404 on an update or a legacy stop reads the entry, one GET, to tell an entry the server no
 *   longer has from a value it no longer has, such as a deleted project. When the entry is gone,
 *   every queued change to it leaves the queue. Any other 404, such as a create or an update whose
 *   project the server no longer has, is held as a 400 is.
 * - A 400, and any other 4xx, is held for the user with the server's message.
 * - A 403, or a 401, pauses the whole queue and discards nothing.
 */
internal suspend fun refusal(server: EntrySends, change: PendingChange, error: ApiError): Refusal {
    if (error == ApiError.AuthFailed) return Refusal.SignInAgain
    val refused = error as? ApiError.UnexpectedStatus ?: return Refusal.Wait(error)
    val status = refused.status
    return when {
        status == UNAUTHORIZED -> Refusal.SignInAgain

        status !in HttpStatus.CLIENT_ERRORS || status == REQUEST_TIMEOUT -> Refusal.Wait(error)

        status == CONFLICT && change.kind == ChangeKind.STOP && change.capturedStop == null -> Refusal.AlreadyDone

        change.kind == ChangeKind.STOP && change.capturedStop != null && status in setOf(CONFLICT, NOT_FOUND) ->
            capturedStopRefusal(server, change, refused)

        status == NOT_FOUND && change.kind != ChangeKind.CREATE -> missingEntryRefusal(server, change, refused)

        else -> Refusal.Hold(refused.message)
    }
}

/** A captured Stop the server refused: done when the server holds that stop, held or waiting otherwise. */
private suspend fun capturedStopRefusal(
    server: EntrySends,
    change: PendingChange,
    refused: ApiError.UnexpectedStatus,
): Refusal = when (val found = stoppedOnServer(server, change)) {
    is ApiResult.Success -> if (found.value) Refusal.AlreadyDone else Refusal.Hold(refused.message)

    is ApiResult.Failure -> if ((found.error as? ApiError.Http)?.status in HttpStatus.CLIENT_ERRORS) {
        Refusal.Hold(refused.message ?: "Captured Stop could not be verified")
    } else {
        Refusal.Wait(found.error)
    }
}

/** A 404 for a change to an entry: gone when the server no longer has the entry. */
private suspend fun missingEntryRefusal(
    server: EntrySends,
    change: PendingChange,
    refused: ApiError.UnexpectedStatus,
): Refusal = when (val entry = entryOnServer(server, change.entryId)) {
    is ApiResult.Success -> if (entry.value) Refusal.Hold(refused.message) else Refusal.EntryGone

    is ApiResult.Failure -> if (entry.error == ApiError.AuthFailed ||
        (entry.error as? ApiError.Http)?.status == UNAUTHORIZED
    ) {
        Refusal.SignInAgain
    } else {
        Refusal.Wait(entry.error)
    }
}

/**
 * Whether the server has the entry [id], preserving a failed lookup's retry or sign-in outcome. A
 * 2xx the phone cannot read as an entry still means the server has it.
 */
private suspend fun entryOnServer(server: EntrySends, id: Long): ApiResult<Boolean> =
    when (val result = server.get(EntryId(id))) {
        is ApiResult.Success -> ApiResult.Success(true)

        is ApiResult.Failure -> when {
            (result.error as? ApiError.Http)?.status == NOT_FOUND -> ApiResult.Success(false)
            result.error is ApiError.InvalidResponse -> ApiResult.Success(true)
            else -> result
        }
    }

/** What a server may answer to a token it does not take, where Tokkl answers 403. */
private const val UNAUTHORIZED = 401

/** The server gave up waiting for the request, which a later try may get past. */
private const val REQUEST_TIMEOUT = 408
