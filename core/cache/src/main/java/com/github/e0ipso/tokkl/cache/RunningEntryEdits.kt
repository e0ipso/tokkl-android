package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.fields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [fields] typed for the running entry [target], shown over the cache's entry until the cache has
 * them, and [sent] once queued. The timer keys an edit by the entry's identity, the Pomodoro page
 * by its id.
 */
data class PendingEdit<out K>(val target: K, val fields: EntryFields, val sent: Boolean)

/**
 * What this edit on show becomes once the write of [written] has ended: the same when a newer
 * edit replaced it, nothing once the write settled, and [written] unsent again when the phone's
 * storage [refused] it, for the next send to take.
 */
fun <K> PendingEdit<K>?.afterWrite(written: PendingEdit<K>, refused: Boolean): PendingEdit<K>? =
    if (this != written) this else written.copy(sent = false).takeIf { refused }

/**
 * The field edits of the running entry on their way to the cache, from the timer or the Pomodoro
 * page. [write] queues one through [changes] and waits until the cache shows it, and
 * [awaitQueued] lets a server action that has to follow the edits wait for them first: every edit
 * of the running entry moves the server's revision.
 */
class RunningEntryEdits(private val changes: RunningEntryChanges) {

    /** How many edits are on their way into the queue. */
    private val writing = MutableStateFlow(0)

    /** Whether an edit reached the queue that the server may not have yet. */
    private val unflushed = AtomicBoolean(false)

    /**
     * Queues [fields] for the running entry [identity], then waits until [running], the cache's
     * running entry, has them or anything newer than [before], or runs another entry, for at most
     * [SETTLE_MS]: far longer than a write to the phone's database takes, so only a cache that
     * never shows the edit reaches it. Returns whether the phone's storage refused the write
     * ([LocalWrite.Failed]), which leaves nothing queued.
     */
    suspend fun write(
        identity: RunningEntryIdentity,
        fields: EntryFields,
        before: EntryFields?,
        running: Flow<TimeEntry?>,
    ): Boolean {
        writing.update { it + 1 }
        val written = try {
            localWrite { changes.updateRunning(identity, fields, identity.start) }
        } finally {
            writing.update { it - 1 }
        }
        if (written !is LocalWrite.Written) return true
        if (written.value) {
            unflushed.set(true)
            withTimeoutOrNull(SETTLE_MS) {
                running.first { entry ->
                    entry == null || (entry.id != identity.entryId && entry.start != identity.start) ||
                        entry.fields().let { it == fields || it != before }
                }
            }
        }
        return false
    }

    /**
     * Waits until every edit [write] started has reached the queue, or failed to. Returns whether
     * one reached it since the last call, which the server may not have yet.
     */
    suspend fun awaitQueued(): Boolean {
        writing.first { it == 0 }
        return unflushed.getAndSet(false)
    }

    private companion object {
        const val SETTLE_MS = 5_000L
    }
}

/**
 * What a write to the phone's database came to: [Written] with what the write returned, or
 * [Failed] when the database or the disk refused it.
 */
sealed interface LocalWrite<out T> {
    data class Written<out T>(val value: T) : LocalWrite<T>

    data class Failed(val cause: Exception) : LocalWrite<Nothing>
}

/**
 * Runs [write], such as a call to the change queue, and says how it went. Only the storage's own
 * failures, [SQLiteException] and [IOException], become [LocalWrite.Failed]. Anything else still
 * throws: a programming error, and cancellation, which has to reach the caller's scope.
 */
suspend fun <T> localWrite(write: suspend () -> T): LocalWrite<T> = try {
    LocalWrite.Written(write())
} catch (refused: SQLiteException) {
    LocalWrite.Failed(refused)
} catch (refused: IOException) {
    LocalWrite.Failed(refused)
}
