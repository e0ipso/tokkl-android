package com.github.e0ipso.tokkl.notification

import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the running timer's notification in step with the running entry on the phone
 * (docs/PRD.md N1). Every start, stop and edit lands in the cache first, whichever surface makes
 * it, so following the cache covers the app, an offline start, the notification's own Stop, the
 * widget, the tile and a refresh that finds a timer started elsewhere.
 *
 * The key and the id of the entry the notification last followed are kept on disk, so a new
 * process, as when the app opens, posts nothing for the entry the notification already followed. A
 * notification the user swiped away stays away until the running entry changes. Nothing here
 * schedules work or wakes the phone: the system ticks the chronometer.
 *
 * @param running the notification for the running entry, null when none runs.
 * @param loadShown the entry last followed, null for none.
 * @param saveShown replaces it.
 * @param currentId the id the cache holds an entry under now, given the id it was followed under:
 *   the server's once the server has answered the phone's create (`ChangeQueue.currentId`).
 * @param post shows the notification, or replaces the one on show.
 * @param remove takes it away.
 * @param io where the store and the notification calls run.
 */
class RunningNotificationSync(
    private val running: Flow<RunningNotificationContent?>,
    private val loadShown: () -> FollowedEntry?,
    private val saveShown: (FollowedEntry?) -> Unit,
    private val currentId: suspend (Long) -> Long,
    private val post: (RunningNotificationContent) -> Unit,
    private val remove: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    private val lock = Mutex()

    /** Follows the running entry until the scope ends. Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the running entry until [running] ends. */
    suspend fun follow() {
        running.distinctUntilChanged().collect(::apply)
    }

    /**
     * Applies the running entry [content]. When the server has given the followed entry its id,
     * nothing changes on show, and the new id is kept.
     */
    suspend fun apply(content: RunningNotificationContent?) {
        lock.withLock {
            val stored = loadShown()
            val followed = stored?.let { it.copy(entryId = it.entryId?.let { id -> currentId(id) }) }
            when (val change = runningNotificationChange(followed, content)) {
                null -> if (content == null || stored?.entryId == content.entryId) return
                is RunningNotificationChange.Post -> post(change.content)
                RunningNotificationChange.Remove -> remove()
            }
            remember(content?.let { FollowedEntry(it.key, it.entryId) })
        }
    }

    /** Takes the notification away and forgets the entry it followed. Signing out calls this. */
    suspend fun forget() {
        lock.withLock {
            remove()
            remember(null)
        }
    }

    /** A failed write leaves the old entry, so the next emission makes the change again. */
    private fun remember(followed: FollowedEntry?) {
        try {
            saveShown(followed)
        } catch (e: IOException) {
            // Keeps following: the notification is right, and the store catches up later.
        }
    }
}
