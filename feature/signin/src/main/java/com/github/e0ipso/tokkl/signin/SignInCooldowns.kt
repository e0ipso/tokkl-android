package com.github.e0ipso.tokkl.signin

import android.content.Context
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * Where the 429 deadlines of sign-in persist, by server origin (`scheme://host:port`), so a new
 * process still waits them out. A deadline is a wall-clock time in milliseconds: Tokkl's lockout
 * runs on the server's clock, so a reboot of the phone does not lift it. Holds nothing else. Calls
 * do disk I/O.
 */
interface SignInCooldowns {

    suspend fun load(): Map<String, Long>

    @Throws(IOException::class)
    suspend fun save(origin: String, deadline: Long)

    @Throws(IOException::class)
    suspend fun clear(origin: String)
}

/**
 * The sign-in cooldowns in the app's private preferences file `sign_in_cooldown`: one entry per
 * server origin, holding the wall-clock time at which that server's 429 stops blocking sign-in. The
 * file holds nothing else, and the backup rules exclude it with every other file.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class SignInCooldownStore(context: Context, private val io: CoroutineContext) : SignInCooldowns {

    private val file = PreferencesFile(context, FILE_NAME, io)

    override suspend fun load(): Map<String, Long> =
        file.read { all }.mapNotNull { (origin, deadline) -> (deadline as? Long)?.let { origin to it } }.toMap()

    @Throws(IOException::class)
    override suspend fun save(origin: String, deadline: Long) {
        file.commit { putLong(origin, deadline) }
    }

    @Throws(IOException::class)
    override suspend fun clear(origin: String) {
        file.commit { remove(origin) }
    }

    companion object {
        internal const val FILE_NAME = "sign_in_cooldown"
    }
}
