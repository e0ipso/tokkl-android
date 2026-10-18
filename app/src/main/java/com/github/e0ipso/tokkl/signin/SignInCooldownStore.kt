package com.github.e0ipso.tokkl.signin

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * The sign-in cooldowns in the app's private preferences file
 * `sign_in_cooldown`: one entry per server origin, holding the wall-clock
 * time at which that server's 429 stops blocking sign-in. The file holds
 * nothing else, and the backup rules exclude it with every other file.
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class SignInCooldownStore(context: Context) : SignInCooldowns {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    override fun load(): Map<String, Long> =
        preferences.all.mapNotNull { (origin, deadline) -> (deadline as? Long)?.let { origin to it } }.toMap()

    @Throws(IOException::class)
    override fun save(origin: String, deadline: Long) {
        commit { putLong(origin, deadline) }
    }

    @Throws(IOException::class)
    override fun clear(origin: String) {
        commit { remove(origin) }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    internal companion object {
        const val FILE_NAME = "sign_in_cooldown"
    }
}
