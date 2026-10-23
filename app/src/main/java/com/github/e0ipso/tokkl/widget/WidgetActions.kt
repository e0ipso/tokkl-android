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

/**
 * The widget's Start and its favorites (docs/PRD.md N3). It starts a timer on the phone and queues
 * the change, as Start in the app does, without opening the app or waiting for the network. A
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
 * The widget's Stop. It stops what the widget showed on the phone, the running entry or the paused
 * timer, as the notification's Stop does, so it works offline.
 */
class StopTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TokklApplication
        try {
            // Not running or paused any more, or shown before a sign-out: nothing to stop. A
            // widget drawn before Pause existed carries the entry alone.
            val target = parameters.target()
            if (target.session == 0L) parameters[ENTRY_ID]?.let { app.changes.stop(it) } else app.changes.stop(target)
        } catch (e: SQLiteException) {
            // The disk refused the change. The timer keeps running, and the widget shows it.
        }
    }
}

/**
 * The widget's Pause (docs/PRD.md T1): it pauses the entry the widget showed on the phone and
 * queues the change, so it works offline.
 */
class PauseTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TokklApplication
        try {
            app.changes.pause(parameters.target())
        } catch (e: SQLiteException) {
            // The disk refused the change. The timer keeps running, and the widget shows it.
        }
    }
}

/**
 * The widget's Resume (docs/PRD.md T1): it starts a new entry with the paused timer's fields on
 * the phone and queues it, so it works offline.
 */
class ResumeTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TokklApplication
        try {
            app.changes.resume(parameters.target())
        } catch (e: SQLiteException) {
            // The disk refused the change. The timer stays paused, and the widget shows it.
        }
    }
}

/** Starts a timer with [fields], the empty ones for Start and a favorite's for that favorite. */
fun startTimer(fields: EntryFields = EntryFields()): Action = actionRunCallback<StartTimerAction>(fields.toParameters())

/** Stops what [target] shows: the running entry or the paused timer. */
fun stopTimer(target: TimerTarget): Action = actionRunCallback<StopTimerAction>(target.toParameters())

/** Pauses the running entry [target] shows. */
fun pauseTimer(target: TimerTarget): Action = actionRunCallback<PauseTimerAction>(target.toParameters())

/** Resumes the paused timer [target] shows. */
fun resumeTimer(target: TimerTarget): Action = actionRunCallback<ResumeTimerAction>(target.toParameters())

private fun TimerTarget.toParameters(): ActionParameters = actionParametersOf(
    SESSION to session,
    CHAIN to chainId,
    REVISION to revision,
    ENTRY_ID to entryId,
)

private fun ActionParameters.target(): TimerTarget = TimerTarget(
    session = this[SESSION] ?: 0,
    chainId = this[CHAIN] ?: 0,
    revision = this[REVISION] ?: 0,
    entryId = this[ENTRY_ID] ?: 0,
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
private val SESSION = ActionParameters.Key<Long>("timerSession")
private val CHAIN = ActionParameters.Key<Long>("pauseChain")
private val REVISION = ActionParameters.Key<Long>("pauseRevision")
private val DESCRIPTION = ActionParameters.Key<String>("description")
private val PROJECT_ID = ActionParameters.Key<Long>("projectId")
private val TASK_ID = ActionParameters.Key<Long>("taskId")
private val TAGS = ActionParameters.Key<Array<String>>("tags")
private val BILLABLE = ActionParameters.Key<Boolean>("billable")
