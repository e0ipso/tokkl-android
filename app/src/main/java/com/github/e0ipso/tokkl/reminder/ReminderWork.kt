package com.github.e0ipso.tokkl.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.e0ipso.tokkl.TokklApplication
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single inexact delayed wakeup, with neither a network constraint nor an exact-alarm permission. */
object ReminderWork {
    const val NAME = "long-timer-reminder"
    const val ID = "entry_id"
    const val START = "entry_start"
    const val DUE = "entry_due"

    fun schedule(context: Context, timer: ReminderTimer) {
        val delay = Duration.between(Instant.now(), Instant.ofEpochMilli(timer.due)).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(ID to timer.id, START to timer.start, DUE to timer.due))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request).result.get()
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME).result.get()
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as TokklApplication
        app.reminder.fire(inputData.getLong(ReminderWork.ID, 0), inputData.getLong(ReminderWork.START, 0), inputData.getLong(ReminderWork.DUE, 0))
        Result.success()
    }
}
