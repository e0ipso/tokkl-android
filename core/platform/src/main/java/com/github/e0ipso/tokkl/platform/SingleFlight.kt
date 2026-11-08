package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * One job at a time in [scope]: [launch] returns the job that runs, or starts a new one when none
 * does. A job runs until it completes, also while it is being cancelled.
 *
 * The new job starts after [launch] has recorded it, outside the lock, so a job that calls
 * [launch] itself, or runs at once on an unconfined dispatcher, gets itself back.
 *
 * @param context what each job runs in, on top of [scope]'s context.
 */
class SingleFlight(private val scope: CoroutineScope, private val context: CoroutineContext = EmptyCoroutineContext) {

    private var job: Job? = null

    /** The job that runs now, or null when none does. */
    @Synchronized
    fun running(): Job? = job?.takeUnless { it.isCompleted }

    /** The job that runs now, or a new one that runs [block]. */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        val job = synchronized(this) {
            running() ?: scope.launch(context, CoroutineStart.LAZY, block).also { job = it }
        }
        job.start()
        return job
    }

    /** Cancels the job that runs now, if any, so the next [launch] starts a new one. */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
    }
}
