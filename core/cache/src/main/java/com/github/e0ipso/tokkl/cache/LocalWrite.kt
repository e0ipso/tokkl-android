package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteException
import java.io.IOException

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
