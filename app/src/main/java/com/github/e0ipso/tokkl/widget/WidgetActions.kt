package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.showTimerActionFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The widget's Start and its timer templates (docs/PRD.md N3). It starts a timer on the phone
 * and queues the change, as Start in the app does, without opening the app or waiting for the network. A
 * running entry stops at the new one's start. The start goes in the account's default workspace,
 * which the phone keeps, so it works offline.
 *
 * Glance's own receiver runs it, which is not exported: only the widget's PendingIntents reach it.
 */
class StartTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TokklApplication
        // Signed out since the widget was drawn: nothing to start in.
        val workspaceId = app.accountSettings.state.value?.defaultWorkspaceId ?: return
        try {
            app.changes.start(workspaceId, parameters.fields())
        } catch (e: SQLiteException) {
            // The disk refused the change. Nothing starts, and the widget stays as it was.
        }
    }
}

/**
 * The widget's Stop: of the running entry it showed, on the phone and queued, as the
 * notification's Stop does, so it works offline; or of the paused timer it showed, on the server.
 */
class StopTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tapTimer(context, TimerSurfaceAction.Stop, parameters)
    }
}

/** The widget's Pause (docs/PRD.md T1): it pauses the entry the widget showed, on the server. */
class PauseTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tapTimer(context, TimerSurfaceAction.Pause, parameters)
    }
}

/**
 * The widget's Resume (docs/PRD.md T1): the server starts a new entry with the paused timer's
 * fields, at the revision the widget showed.
 */
class ResumeTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tapTimer(context, TimerSurfaceAction.Resume, parameters)
    }
}

/**
 * Runs [action] for the target [parameters] carry through [TokklApplication.timerSurfaceActions].
 * A Stop of a running entry is queued before this returns. A server action goes out in a scope of
 * its own, so Glance's broadcast does not wait for the server, and one that fails, offline
 * included, says why in a toast while the widget keeps showing what it showed.
 */
private suspend fun tapTimer(context: Context, action: TimerSurfaceAction, parameters: ActionParameters) {
    val app = context.applicationContext as TokklApplication
    val tap = try {
        app.timerSurfaceActions.tap(action, parameters.target())
    } catch (e: SQLiteException) {
        // The disk refused the change. The timer stays as it was, and the widget shows it.
        null
    }
    if (tap is SurfaceTap.Request) {
        CoroutineScope(Dispatchers.IO).launch { tap.send()?.let { showTimerActionFailure(app, it) } }
    }
}

/** Starts a timer with [fields], the empty ones for Start and a template's for that template. */
fun startTimer(fields: EntryFields = EntryFields()): Action = actionRunCallback<StartTimerAction>(fields.toParameters())

/** Stops what [target] shows: the running entry or the paused timer. */
fun stopTimer(target: TimerTarget): Action = actionRunCallback<StopTimerAction>(target.toParameters())

/** Pauses the running entry [target] shows. */
fun pauseTimer(target: TimerTarget): Action = actionRunCallback<PauseTimerAction>(target.toParameters())

/** Resumes the paused timer [target] shows. */
fun resumeTimer(target: TimerTarget): Action = actionRunCallback<ResumeTimerAction>(target.toParameters())

private fun TimerTarget.toParameters(): ActionParameters = actionParametersOf(
    ACCOUNT to account,
    REVISION to revision,
    ENTRY_ID to entryId,
)

/** The target [toParameters] put here. A widget drawn by an older version names no account, so it acts on nothing. */
private fun ActionParameters.target(): TimerTarget = TimerTarget.server(
    revision = this[REVISION] ?: 0,
    entryId = this[ENTRY_ID] ?: 0,
    account = this[ACCOUNT].orEmpty(),
)

/**
 * The fields as the PendingIntent carries them. Glance puts each parameter in the intent's
 * extras, so an unset project or task is left out rather than null.
 */
private fun EntryFields.toParameters(): ActionParameters {
    val pairs = buildList {
        add(DESCRIPTION to description)
        projectId?.let { add(PROJECT_ID to it) }
        taskId?.let { add(TASK_ID to it) }
        add(TAGS to tags.toTypedArray())
        add(BILLABLE to billable)
    }
    return actionParametersOf(*pairs.toTypedArray())
}

private fun ActionParameters.fields(): EntryFields = EntryFields(
    description = this[DESCRIPTION].orEmpty(),
    projectId = this[PROJECT_ID],
    taskId = this[TASK_ID],
    tags = this[TAGS]?.toList().orEmpty(),
    billable = this[BILLABLE] ?: false,
)

private val ENTRY_ID = ActionParameters.Key<Long>("entryId")
private val ACCOUNT = ActionParameters.Key<String>("timerAccount")
private val REVISION = ActionParameters.Key<Long>("pauseRevision")
private val DESCRIPTION = ActionParameters.Key<String>("description")
private val PROJECT_ID = ActionParameters.Key<Long>("projectId")
private val TASK_ID = ActionParameters.Key<Long>("taskId")
private val TAGS = ActionParameters.Key<Array<String>>("tags")
private val BILLABLE = ActionParameters.Key<Boolean>("billable")
