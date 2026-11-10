package com.github.e0ipso.tokkl.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * One of the app's private preference files, [name]. Each call switches to [io], where the first
 * one loads the file from disk. [commit] throws when the write fails, so no store takes an
 * unwritten value for a stored one.
 */
class PreferencesFile(context: Context, private val name: String, private val io: CoroutineContext) {

    /**
     * The file itself, which the first use loads from disk. Only for code that runs on a worker
     * thread and cannot suspend: OkHttp's trust checks, and the timer state's acceptance.
     */
    val preferences: SharedPreferences by lazy { context.getSharedPreferences(name, Context.MODE_PRIVATE) }

    suspend fun <T> read(read: SharedPreferences.() -> T): T = withContext(io) { preferences.read() }

    /** Applies [change] and commits it, or throws [IOException] with [failure] when the commit fails. */
    @Throws(IOException::class)
    suspend fun commit(failure: String = "Could not write $name", change: SharedPreferences.Editor.() -> Unit) {
        withContext(io) { commitHere(failure, change) }
    }

    /**
     * Applies [change] at once and writes it in the background, for a value whose lost write costs
     * only an older value.
     */
    suspend fun write(change: SharedPreferences.Editor.() -> Unit) {
        withContext(io) { preferences.edit().apply(change).apply() }
    }

    /**
     * [commit] on the calling thread, for the callers of [preferences]. It blocks on disk I/O, so
     * never call it on the main thread.
     */
    @Throws(IOException::class)
    fun commitHere(failure: String = "Could not write $name", change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException(failure)
    }
}
