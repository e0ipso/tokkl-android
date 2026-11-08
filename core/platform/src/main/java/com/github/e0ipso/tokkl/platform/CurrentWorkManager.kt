package com.github.e0ipso.tokkl.platform

import androidx.work.WorkManager

/**
 * The process's [WorkManager], looked up on each call as `WorkManager.getInstance` does. Koin's
 * worker factory initialises it at process start. Components keep this rather than the instance,
 * because the instrumented tests put an empty test WorkManager in its place before each test,
 * after the app's components were made.
 */
fun interface CurrentWorkManager {
    fun get(): WorkManager
}
