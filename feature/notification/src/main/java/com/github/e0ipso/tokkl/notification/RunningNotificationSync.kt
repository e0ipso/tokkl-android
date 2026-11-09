package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.collectDurably
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/** The running timer's notification, such as [RunningNotifier]. */
interface RunningNotice {

    /** Shows the notification for [content], or replaces the one on show. */
    fun post(content: RunningNotificationContent)

    fun remove()
}

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
 * @param shown the entry last followed, null for none.
 * @param currentId the id the cache holds an entry under now, given the id it was followed under:
 *   the server's once the server has answered the phone's create (`ChangeQueue.currentId`).
 * @param notice shows, replaces and takes away the notification.
 * @param io where the store and the notification calls run.
 */
class RunningNotificationSync(
    private val running: Flow<RunningNotificationContent?>,
    private val shown: ValueStore<FollowedEntry?>,
    private val currentId: suspend (EntryId) -> EntryId,
    private val notice: RunningNotice,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = forget()

    private val lock = Mutex()
    private var latest: RunningNotificationContent? = null

    /** Follows the running entry until the scope ends. Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the running entry until [running] ends, reading it again after a failure, as [collectDurably] does. */
    suspend fun follow() {
        running.distinctUntilChanged().collectDurably("RunningNotificationSync", action = ::apply)
    }

    /**
     * Applies the running entry [content]. When the server has given the followed entry its id,
     * nothing changes on show, and the new id is kept.
     */
    suspend fun apply(content: RunningNotificationContent?) {
        lock.withLock {
            latest = content
            val stored = shown.load()
            val followed = stored?.let { it.copy(entryId = it.entryId?.let { id -> currentId(id) }) }
            when (val change = runningNotificationChange(followed, content)) {
                null -> if (content == null || stored?.entryId == content.entryId) return
                is RunningNotificationChange.Post -> notice.post(change.content)
                RunningNotificationChange.Remove -> notice.remove()
            }
            remember(content?.let { FollowedEntry(it.key, it.entryId) })
        }
    }

    /**
     * A newly granted notification permission makes the current timer visible, even if its start
     * was already followed while notifications were blocked. Ordinary foreground trips never call
     * this, so a notification dismissed by the user stays dismissed.
     */
    suspend fun repost() {
        lock.withLock { latest?.let(notice::post) }
    }

    /** Takes the notification away and forgets the entry it followed. Signing out calls this. */
    suspend fun forget() {
        lock.withLock {
            latest = null
            notice.remove()
            remember(null)
        }
    }

    /** A failed write leaves the old entry, so the next emission makes the change again. */
    private suspend fun remember(followed: FollowedEntry?) {
        try {
            shown.save(followed)
        } catch (_: IOException) {
            // Keeps following: the notification is right, and the store catches up later.
        }
    }
}

/** Right after the refresh requests, since later hooks may change the running notification. */
class RunningNotificationHook(private val sync: Lazy<RunningNotificationSync>) : AppHook {
    /**
     * Reads the cache off the main thread, and posts or removes nothing for the entry the
     * notification already followed, so opening the app never brings back a dismissed one.
     */
    override fun processStart() {
        sync.value.start()
    }

    /** A grant in notification settings makes the already-running timer visible too. */
    override suspend fun notificationsAllowed() = sync.value.repost()
}
