package com.github.e0ipso.tokkl.platform

import android.content.Context
import androidx.work.WorkManager

/**
 * The process's [WorkManager], looked up on each call as `WorkManager.getInstance` does. Koin's
 * worker factory initialises it at process start. Components keep this rather than the instance,
 * because the instrumented tests put an empty test WorkManager in its place before each test,
 * after the app's components were made.
 */
class CurrentWorkManager(private val context: Context) {
    fun get(): WorkManager = WorkManager.getInstance(context)
}
