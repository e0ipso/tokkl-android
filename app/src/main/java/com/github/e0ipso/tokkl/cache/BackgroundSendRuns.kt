package com.github.e0ipso.tokkl.cache

import java.util.UUID
import java.util.concurrent.ExecutionException

/**
 * Coordinates background workers joining the sender with cancellation of a waiting retry.
 * The WorkManager state read can be stale, so registration and the completed cancellation share
 * a lock. A worker cancelled before registration must fail [join]'s [canJoin] check too.
 * Every callback may block on disk; call these methods off the main thread.
 */
internal class BackgroundSendRuns {
    private val lock = Any()
    private val joined = mutableSetOf<UUID>()

    fun join(id: UUID, canJoin: () -> Boolean): Boolean = synchronized(lock) {
        if (canJoin()) joined.add(id) else false
    }

    fun leave(id: UUID) {
        synchronized(lock) { joined.remove(id) }
    }

    fun hasJoined(id: UUID): Boolean = synchronized(lock) { id in joined }

    fun cancelWaiting(waiting: () -> Boolean, cancel: () -> Unit, queueEmpty: () -> Boolean, schedule: () -> Unit) {
        try {
            if (!waiting()) return
            synchronized(lock) {
                if (joined.isEmpty()) cancel()
            }
        } catch (e: ExecutionException) {
            // A failed WorkManager read or cancel may leave a retry waiting. The queue check
            // still covers a cancellation that committed before WorkManager reported failure.
        }
        if (!queueEmpty()) schedule()
    }
}
