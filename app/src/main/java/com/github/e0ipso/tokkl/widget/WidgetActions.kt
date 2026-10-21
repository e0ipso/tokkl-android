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
 * The widget's Stop. It stops the entry the widget showed on the phone and queues the change, as
 * the notification's Stop does, so it works offline.
 */
class StopTimerAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryId = parameters[ENTRY_ID] ?: return
        val app = context.applicationContext as TokklApplication
        try {
            // Not running any more, or not cached since a sign-out: nothing to stop.
            app.changes.stop(entryId)
        } catch (e: SQLiteException) {
            // The disk refused the change. The timer keeps running, and the widget shows it.
        }
    }
}

/** Starts a timer with [fields], the empty ones for Start and a favorite's for that favorite. */
fun startTimer(fields: EntryFields = EntryFields()): Action = actionRunCallback<StartTimerAction>(fields.toParameters())

/** Stops the running entry [entryId]. */
fun stopTimer(entryId: Long): Action = actionRunCallback<StopTimerAction>(actionParametersOf(ENTRY_ID to entryId))

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
private val DESCRIPTION = ActionParameters.Key<String>("description")
private val PROJECT_ID = ActionParameters.Key<Long>("projectId")
private val TASK_ID = ActionParameters.Key<Long>("taskId")
private val TAGS = ActionParameters.Key<Array<String>>("tags")
private val BILLABLE = ActionParameters.Key<Boolean>("billable")
