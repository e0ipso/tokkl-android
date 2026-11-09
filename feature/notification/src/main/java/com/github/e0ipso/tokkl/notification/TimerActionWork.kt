package com.github.e0ipso.tokkl.notification

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Hands the notification's Pause, Resume and Stop while paused to WorkManager as expedited work,
 * which keeps the process alive while the server answers. Without a quota for expedited work it
 * runs as ordinary work. It has no network constraint: offline, the request fails at once and the
 * toast says so, as before.
 */
internal fun CurrentWorkManager.sendTimerAction(action: TimerSurfaceAction, target: TimerTarget) {
    val request = OneTimeWorkRequestBuilder<TimerActionWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(
            workDataOf(
                ACTION to action.name,
                ACCOUNT to target.account,
                REVISION to target.revision,
                ENTRY_ID to (target.entryId?.value ?: 0L),
            ),
        )
        .build()
    get().enqueue(request)
}

/**
 * Sends one of the notification's server actions: [TimerSurfaceActions] checks it against what the
 * phone holds now and sends it once. One that fails, offline included, changes nothing and says
 * why in a toast.
 */
class TimerActionWorker(
    context: Context,
    parameters: WorkerParameters,
    private val surfaceActions: TimerSurfaceActions,
    private val failures: TimerFailureNotice,
    private val io: CoroutineContext,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(io) {
        val action = inputData.action() ?: return@withContext Result.success()
        try {
            val tap = surfaceActions.tap(action, inputData.target())
            if (tap is SurfaceTap.Request) tap.send()?.let { failures.show(it) }
        } catch (_: SQLiteException) {
            // The disk could not be read. The timer stays as it was, and the notification with it.
        }
        Result.success()
    }
}

private const val ACTION = "action"
private const val ACCOUNT = "account"
private const val REVISION = "revision"
private const val ENTRY_ID = "entry_id"

private fun Data.action(): TimerSurfaceAction? = TimerSurfaceAction.entries.firstOrNull { it.name == getString(ACTION) }

private fun Data.target() = TimerTarget.server(
    revision = getLong(REVISION, 0),
    entryId = getLong(ENTRY_ID, 0).takeIf { it != 0L }?.let(::EntryId),
    account = getString(ACCOUNT).orEmpty(),
)
