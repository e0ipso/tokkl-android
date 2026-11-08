package com.github.e0ipso.tokkl.storage

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * One of the app's private preference files, [name]. The first use of [preferences] opens it, which
 * loads the file from disk. [commit] writes on the calling thread and throws when the write fails,
 * so no store takes an unwritten value for a stored one.
 */
class PreferencesFile(context: Context, private val name: String) {

    val preferences: SharedPreferences by lazy { context.getSharedPreferences(name, Context.MODE_PRIVATE) }

    /** Applies [change] and commits it, or throws [IOException] with [failure] when the commit fails. */
    @Throws(IOException::class)
    fun commit(failure: String = "Could not write $name", change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException(failure)
    }
}
