package com.github.e0ipso.tokkl.platform

import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/** What a long-lived observer does with a failure it did not expect, before it starts over. */
fun interface ObserverFailures {
    fun unexpected(observer: String, failure: Exception)
}

/** Writes each unexpected failure to the phone's log, which stays on the phone. */
object LogcatObserverFailures : ObserverFailures {
    override fun unexpected(observer: String, failure: Exception) {
        Log.e("Tokkl", "$observer failed and starts over", failure)
    }
}

/**
 * Collects this flow with [action] until the flow ends or the calling coroutine is cancelled, for
 * an observer that lives as long as the process, such as a notification or a widget that follows
 * the cache. A failure does not end it:
 *
 * - When the flow fails, the observer waits and collects it again from the start.
 * - When [action] fails, the observer waits and hands it the same value again, until it goes
 *   through. Meanwhile the newest value waits and the values before it are dropped, unless
 *   [everyValue] keeps them all, for a flow of events of which each one counts.
 * - A failure of the disk or the database ([IOException], [SQLiteException]) is expected. Any
 *   other exception goes to [failures] before the wait.
 * - The waits start at one second and double after each failure in a row, up to two minutes.
 * - Cancellation ends it, as do errors, such as running out of memory.
 *
 * [observer] names the observer in [failures].
 */
suspend fun <T> Flow<T>.collectDurably(
    observer: String,
    failures: ObserverFailures = LogcatObserverFailures,
    everyValue: Boolean = false,
    action: suspend (T) -> Unit,
) {
    var failedReads = 0
    onEach { failedReads = 0 }
        .retryWhen { failure, _ ->
            val again = failure is Exception && failure !is CancellationException
            if (again) {
                failed(observer, failure as Exception, failures)
                delay(observerRetryDelay(failedReads++))
            }
            again
        }
        .let { if (everyValue) it else it.conflate() }
        .collect { value -> applyUntilDone(observer, value, failures, action) }
}

/** Runs [action] for [value] again after each failure, with the waits of [collectDurably], until it goes through. */
private suspend fun <T> applyUntilDone(
    observer: String,
    value: T,
    failures: ObserverFailures,
    action: suspend (T) -> Unit,
) {
    var failed = 0
    while (true) {
        try {
            action(value)
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(observer, e, failures)
            delay(observerRetryDelay(failed++))
        }
    }
}

private fun failed(observer: String, failure: Exception, failures: ObserverFailures) {
    if (!failure.isStorageFailure()) failures.unexpected(observer, failure)
}

/** A failure of the disk or the database, after which a read or a write can go through later. */
fun Throwable.isStorageFailure(): Boolean = this is IOException || this is SQLiteException

private const val FIRST_RETRY_MS = 1_000L
private const val LAST_RETRY_MS = 120_000L
private const val DOUBLINGS = 7

/** The wait after failure [failed] in a row, counted from 0: one second, doubling up to two minutes. */
internal fun observerRetryDelay(failed: Int): Long =
    minOf(LAST_RETRY_MS, FIRST_RETRY_MS shl failed.coerceAtMost(DOUBLINGS))
