package com.github.e0ipso.tokkl.platform

import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/** Writes an observer's unexpected [failure] to the phone's log, which stays on the phone. */
private fun logUnexpected(observer: String, failure: Exception) {
    Log.e("Tokkl", "$observer failed and starts over", failure)
}

/**
 * Collects this flow with [action] until the flow ends or the calling coroutine is cancelled, for
 * an observer that lives as long as the process, such as a notification or a widget that follows
 * the cache. A failure does not end it:
 *
 * - When the flow fails, the observer waits and collects it again from the start.
 * - When [action] fails, the observer waits and hands it the same value again, until it goes
 *   through. The values that come meanwhile wait. With [latestOnly], only the newest of them
 *   waits, also while [action] runs, for an observer that needs no step in between.
 * - A failure of the disk or the database ([IOException], [SQLiteException]) is expected. Any
 *   other exception goes to [failures] before the wait.
 * - The waits start at one second and double after each failure in a row, up to two minutes.
 * - Cancellation ends it, as do errors, such as running out of memory.
 *
 * [observer] names the observer in [failures].
 */
suspend fun <T> Flow<T>.collectDurably(
    observer: String,
    failures: (observer: String, failure: Exception) -> Unit = ::logUnexpected,
    latestOnly: Boolean = false,
    action: suspend (T) -> Unit,
) {
    var failedReads = 0
    onEach { failedReads = 0 }
        .retryWhen { failure, _ -> waitAfter(failure, failedReads++, observer, failures) }
        .let { if (latestOnly) it.conflate() else it }
        .collect { value ->
            var failed = 0
            flow { emit(action(value)) }
                .retryWhen { failure, _ -> waitAfter(failure, failed++, observer, failures) }
                .collect()
        }
}

/**
 * Whether to try again after [failure], the [failed]th in a row counted from 0, having waited as
 * [collectDurably] says: false for cancellation and for errors.
 */
private suspend fun waitAfter(
    failure: Throwable,
    failed: Int,
    observer: String,
    failures: (String, Exception) -> Unit,
): Boolean {
    if (failure !is Exception || failure is CancellationException) return false
    if (!failure.isStorageFailure()) failures(observer, failure)
    delay(observerRetryDelay(failed))
    return true
}

/** A failure of the disk or the database, after which a read or a write can go through later. */
fun Throwable.isStorageFailure(): Boolean = this is IOException || this is SQLiteException

private const val FIRST_RETRY_MS = 1_000L
private const val LAST_RETRY_MS = 120_000L
private const val DOUBLINGS = 7

/** The wait after failure [failed] in a row, counted from 0: one second, doubling up to two minutes. */
internal fun observerRetryDelay(failed: Int): Long =
    minOf(LAST_RETRY_MS, FIRST_RETRY_MS shl failed.coerceAtMost(DOUBLINGS))
