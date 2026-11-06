package com.github.e0ipso.tokkl.session

/**
 * Serializes export publication/chooser launch with account replacement. No token is retained.
 * Invalidation cancels in-flight HTTP before clearing files; old leases can never publish again,
 * even when the replacement account has the same server, user id and credentials.
 */
class ExportAccount {
    private var generation = 0L
    private val leases = mutableSetOf<Lease>()

    inner class Lease internal constructor(internal val generation: Long, internal val cancel: () -> Unit) {
        fun <T> current(action: () -> T): T? = synchronized(this@ExportAccount) {
            if (generation == this@ExportAccount.generation && this in leases) action() else null
        }

        fun close() = synchronized(this@ExportAccount) { leases.remove(this) }
    }

    @Synchronized
    fun open(cancel: () -> Unit): Lease = Lease(generation, cancel).also { leases.add(it) }

    @Synchronized
    fun <T> change(action: () -> T): T {
        invalidate()
        return action()
    }

    @Synchronized
    fun invalidate(clearFiles: () -> Unit = {}) {
        generation++
        leases.forEach { it.cancel() }
        leases.clear()
        clearFiles()
    }
}
