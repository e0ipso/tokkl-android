package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * One job at a time in [scope]: [launch] returns the job that runs, or starts a new one when none
 * does. A job runs until it completes, also while it is being cancelled.
 *
 * The new job starts after [launch] has recorded it, so a job that calls [launch] itself, or runs
 * at once on an unconfined dispatcher, gets itself back. Any thread may call it.
 *
 * @param context what each job runs in, on top of [scope]'s context.
 */
class SingleFlight(private val scope: CoroutineScope, private val context: CoroutineContext = EmptyCoroutineContext) {

    /** The last job [launch] made, which runs until it completes. */
    private val current = AtomicReference<Job?>(null)

    /** The job that runs now, or null when none does. */
    fun running(): Job? = current.get()?.takeUnless { it.isCompleted }

    /**
     * The job that runs now, or a new one that runs [block]. Callers on several threads race to
     * put their new job in place; the one that loses cancels its job, which has not started, and
     * returns the winner's.
     */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        while (true) {
            val last = current.get()
            if (last != null && !last.isCompleted) {
                last.start()
                return last
            }
            val next = scope.launch(context, CoroutineStart.LAZY, block)
            if (current.compareAndSet(last, next)) {
                next.start()
                return next
            }
            next.cancel()
        }
    }

    /** Cancels the job that runs now, if any, so the next [launch] starts a new one. */
    fun cancel() {
        current.getAndSet(null)?.cancel()
    }
}
