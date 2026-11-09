package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.session.AccountRefreshRequests

/**
 * Counts the queued changes the [ChangeSender] discarded because the server no longer had their
 * entries, for the notice that says so (docs/PRD.md O6). The sync feature keeps the count.
 */
fun interface ChangeDiscards {
    fun add(count: Int)
}

/**
 * What the [ChangeSender]'s drains lead to. Once a drain has sent some changes, the account
 * refreshes with a read that starts after the send, so a refresh that read the server before it
 * cannot put the cache back. A drain that empties the queue cancels the [background] send's
 * waiting retry, and the changes it discards count in [discards].
 */
internal class QueueDrainEvents(
    private val refreshes: AccountRefreshRequests,
    private val background: BackgroundSend,
    private val store: ChangeStore,
    private val discards: ChangeDiscards,
) : ChangeSenderEvents {

    override fun sent() = refreshes.refreshAfterChange()

    override fun emptied() = background.cancelWaiting(store::readsEmpty)

    override fun discarded(count: Int) = discards.add(count)
}

/**
 * Whether the queue holds no change that can go out. A queue the disk cannot read counts as
 * holding one, so the background send stays. Blocks on disk I/O.
 */
internal fun ChangeStore.readsEmpty(): Boolean = runCatching { first() == null }.getOrDefault(false)
