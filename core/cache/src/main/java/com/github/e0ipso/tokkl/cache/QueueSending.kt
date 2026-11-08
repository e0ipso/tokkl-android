package com.github.e0ipso.tokkl.cache

/**
 * Sends each change the queue takes at once through the [ChangeSender], with [BackgroundSend] as
 * the fallback that sends what is left when the network returns, also with the app closed.
 */
class SendOnQueue(private val background: BackgroundSend, private val sender: ChangeSender) : QueuedListener {

    override fun queued() {
        // Register the fallback before a fast drain can finish and cancel it.
        background.schedule()
        sender.send()
    }
}

/** Cancels [BackgroundSend]'s waiting retry once a drain has left the queue empty. */
class CancelWaitingSend(private val background: BackgroundSend, private val store: ChangeStore) {

    /** For [ChangeSenderEvents.emptied]. Blocks on WorkManager and the disk. */
    fun emptied() {
        // A queue the disk cannot read counts as holding a change, so the work stays.
        background.cancelWaiting { runCatching { store.first() == null }.getOrDefault(false) }
    }
}
