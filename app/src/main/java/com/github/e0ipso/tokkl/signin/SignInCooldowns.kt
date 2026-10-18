package com.github.e0ipso.tokkl.signin

import java.io.IOException

/**
 * Where the 429 deadlines of sign-in persist, by server origin
 * (`scheme://host:port`), so a new process still waits them out. A deadline
 * is a wall-clock time in milliseconds: Tokkl's lockout runs on the server's
 * clock, so a reboot of the phone does not lift it. Holds nothing else. Calls
 * do disk I/O.
 */
interface SignInCooldowns {

    /** Every stored deadline, by origin. */
    fun load(): Map<String, Long>

    @Throws(IOException::class)
    fun save(origin: String, deadline: Long)

    @Throws(IOException::class)
    fun clear(origin: String)
}
