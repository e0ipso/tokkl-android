package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.session.SignInStatus
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timernotice.runSurfaceCommand
import com.github.e0ipso.tokkl.timernotice.toWorkResult
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerStateIntakes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Hands a Pomodoro widget tap to WorkManager as expedited work, which keeps the process alive
 * while the server answers, as the timer's server actions do. Without a quota for expedited work
 * it runs as ordinary work. It has no network constraint: offline, the request fails at once and
 * [TimerFailureNotice] says so. It runs once, never again after a failure.
 */
internal fun CurrentWorkManager.sendPomodoroWidgetTap(tap: PomodoroWidgetTap) {
    val request = OneTimeWorkRequestBuilder<PomodoroWidgetActionWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(tap.toData())
        .build()
    get().enqueue(request)
}

/**
 * Sends one Pomodoro widget tap: Start, Next phase or End session. It checks the account, the
 * phase and the revision the tap carries against the widget's state when it runs
 * ([sendPomodoroWidgetAction]), and sends with the account's timer state and API as they are
 * then, which it keeps together until the answer: a sign-out or another account meanwhile drops
 * the answer. One that fails, offline or refused by the phone's storage included, says why
 * through [failures] ([runSurfaceCommand]) and ends the work as failed.
 */
class PomodoroWidgetActionWorker(
    context: Context,
    parameters: WorkerParameters,
    private val timerStates: TimerStateIntakes,
    private val client: ApiClientProvider,
    private val signIn: SignInStatus,
    private val widgetStates: PomodoroWidgetStateSource,
    private val failures: TimerFailureNotice,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(io) {
        val tap = inputData.pomodoroWidgetTap() ?: return@withContext Result.success()
        runSurfaceCommand(failures) { send(tap) }.toWorkResult()
    }

    /** Sends [tap] and returns what it came to, or null when it sent nothing. */
    private suspend fun send(tap: PomodoroWidgetTap): TimerActionResult? {
        val intake = timerStates.intake()
        val current = widgetStates.state.first()
        if (timerStates.intake() !== intake || !signIn.isSignedIn()) return null
        val api = client()
        // Keep the captured holder and credential together across sign-out/account replacement.
        val actions = TimerActions(
            client = { api.takeIf { timerStates.intake() === intake && signIn.isSignedIn() } },
            // The captured holder, whichever account signs in meanwhile.
            states = { intake },
            scope = scope,
            io = io,
        )
        return sendPomodoroWidgetAction(tap, current, actions)
    }
}

internal fun PomodoroWidgetTap.toData(): Data = when (this) {
    is PomodoroWidgetTap.Start -> workDataOf(
        ACCOUNT to account,
        FIELDS to rememberedFieldsJson(account, entry),
        WORK to defaults.workMinutes,
        SHORT to defaults.shortRestMinutes,
        LONG to defaults.longRestMinutes,
        EVERY to defaults.longRestEvery,
    )

    is PomodoroWidgetTap.Phase -> workDataOf(
        ACCOUNT to account,
        REVISION to revision,
        PHASE to start.toString(),
        END to endSession,
    )
}

/** The tap [toData] put here: a Start when it carries fields, Next phase or End session otherwise. */
internal fun Data.pomodoroWidgetTap(): PomodoroWidgetTap? {
    val values = keyValueMap
    val account = values[ACCOUNT] as? String
    return if (FIELDS in values) {
        startTap(
            account,
            values[FIELDS] as? String,
            values[WORK] as? Int,
            values[SHORT] as? Int,
            values[LONG] as? Int,
            values[EVERY] as? Int,
        )
    } else {
        phaseTap(account, values[REVISION] as? Long, values[PHASE] as? String, values[END] as? Boolean)
    }
}

private const val ACCOUNT = "account"
private const val FIELDS = "fields"
private const val WORK = "work"
private const val SHORT = "short"
private const val LONG = "long"
private const val EVERY = "every"
private const val REVISION = "revision"
private const val PHASE = "phase"
private const val END = "end"
