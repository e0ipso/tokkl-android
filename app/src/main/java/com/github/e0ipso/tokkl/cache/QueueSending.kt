package com.github.e0ipso.tokkl.cache

import android.content.Context

/**
 * Sends each change the queue takes at once through the [ChangeSender], with [BackgroundSend] as
 * the fallback that sends what is left when the network returns, also with the app closed.
 */
internal class SendOnQueue(private val context: Context, private val sender: ChangeSender) {

    /** For [ChangeQueue]'s `onQueued`. */
    fun queued() {
        // Register the fallback before a fast drain can finish and cancel it.
        BackgroundSend.schedule(context)
        sender.send()
    }
}

/** Cancels [BackgroundSend]'s waiting retry once a drain has left the queue empty. */
internal class CancelWaitingSend(private val context: Context, private val store: ChangeStore) {

    /** For [ChangeSender]'s `onEmptied`. Blocks on WorkManager and the disk. */
    fun emptied() {
        // A queue the disk cannot read counts as holding a change, so the work stays.
        BackgroundSend.cancelWaiting(context) { runCatching { store.first() == null }.getOrDefault(false) }
    }
}
