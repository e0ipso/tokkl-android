package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timernotice.runFromSurface
import com.github.e0ipso.tokkl.timernotice.runSurfaceCommand
import com.github.e0ipso.tokkl.timernotice.sendTimerAction
import com.github.e0ipso.tokkl.timerstate.CurrentTimerState
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.flatEntryId
import com.github.e0ipso.tokkl.timerstate.flatRevision
import com.github.e0ipso.tokkl.timerstate.timerTarget
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The widget's Start and its timer templates (docs/PRD.md N3). It starts a timer on the phone
 * and queues the change, as Start in the app does, without opening the app or waiting for the network. A
 * running entry stops at the new one's start. The start goes in the account's default workspace,
 * which the phone keeps, so it works offline. Each carries the account the widget was drawn for,
 * as Stop, Pause and Resume do, and starts nothing in another one.
 *
 * Glance's own receiver runs it, which is not exported: only the widget's PendingIntents reach it.
 */
class StartTimerAction :
    ActionCallback,
    KoinComponent {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val start = WidgetStart(parameters[ACCOUNT].orEmpty(), parameters.fields())
        val queue = get<ChangeQueue>()
        // A start the disk refused starts nothing and says so, while the widget stays as it was.
        runSurfaceCommand(get<TimerFailureNotice>()) {
            start.run(
                account = get<CurrentTimerState>().account(),
                workspaceId = get<AccountSettingsSource>().current()?.defaultWorkspaceId,
            ) { workspaceId, fields -> queue.start(workspaceId, fields) }
            null
        }
    }
}

/** A Start or a template's start, with the [account] the widget drew it for and the [fields] it starts. */
internal data class WidgetStart(val account: String, val fields: EntryFields)

/**
 * Queues this start in [workspaceId], the default workspace of the account signed in now, whose
 * timer controls carry the name [account], unless the widget drew it for another account, as a
 * template kept from before a sign-out, or for none, as a widget an older version drew. Returns
 * whether it queued it.
 */
internal suspend fun WidgetStart.run(
    account: String,
    workspaceId: WorkspaceId?,
    queue: suspend (WorkspaceId, EntryFields) -> Unit,
): Boolean {
    if (this.account != account) return false
    // Signed out since the widget was drawn: nothing to start in.
    workspaceId ?: return false
    queue(workspaceId, fields)
    return true
}

/**
 * The widget's Stop: of the running entry it showed, on the phone and queued, as the
 * notification's Stop does, so it works offline; or of the paused timer it showed, on the server.
 */
class StopTimerAction :
    ActionCallback,
    KoinComponent {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tapTimer(TimerSurfaceAction.Stop, parameters)
    }
}

/** The widget's Pause (docs/PRD.md T1): it pauses the entry the widget showed, on the server. */
class PauseTimerAction :
    ActionCallback,
    KoinComponent {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tapTimer(TimerSurfaceAction.Pause, parameters)
    }
}

/**
 * The widget's Resume (docs/PRD.md T1): the server starts a new entry with the paused timer's
 * fields, at the revision the widget showed.
 */
class ResumeTimerAction :
    ActionCallback,
    KoinComponent {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tapTimer(TimerSurfaceAction.Resume, parameters)
    }
}

/**
 * Runs [action] for the target [parameters] carry, as the notification's actions run. A Stop of a
 * running entry is changed on the phone and queued before this returns, so it works offline. Pause,
 * Resume and Stop while paused need the server, so they go to WorkManager ([sendTimerAction]),
 * which checks them against the timer when it runs and sends each once; one that fails, offline
 * included, says why in a toast while the widget keeps showing what it showed.
 */
private suspend fun KoinComponent.tapTimer(action: TimerSurfaceAction, parameters: ActionParameters) {
    val target = parameters.target() ?: return
    if (action != TimerSurfaceAction.Stop || target !is TimerTarget.Running) {
        get<CurrentWorkManager>().sendTimerAction(action, target)
        return
    }
    // A Stop the disk refused keeps the timer running, as the widget shows, and says so.
    get<TimerSurfaceActions>().runFromSurface(action, target, get<TimerFailureNotice>())
}

/**
 * Starts a timer with [fields], the empty ones for Start and a template's for that template, for
 * [account], the account the widget was drawn for.
 */
fun startTimer(account: String, fields: EntryFields = EntryFields()): Action =
    actionRunCallback<StartTimerAction>(fields.toParameters(account))

/** Stops what [target] shows: the running entry or the paused timer. */
fun stopTimer(target: TimerTarget): Action = actionRunCallback<StopTimerAction>(target.toParameters())

/** Pauses the running entry [target] shows. */
fun pauseTimer(target: TimerTarget): Action = actionRunCallback<PauseTimerAction>(target.toParameters())

/** Resumes the paused timer [target] shows. */
fun resumeTimer(target: TimerTarget): Action = actionRunCallback<ResumeTimerAction>(target.toParameters())

private fun TimerTarget.toParameters(): ActionParameters = actionParametersOf(
    ACCOUNT to account,
    REVISION to flatRevision,
    ENTRY_ID to flatEntryId,
)

/** The target [toParameters] put here, or null for a widget an older version drew, which names no account. */
private fun ActionParameters.target(): TimerTarget? =
    timerTarget(this[ACCOUNT], this[REVISION] ?: 0, this[ENTRY_ID] ?: 0)

/**
 * The fields as the PendingIntent carries them. Glance puts each parameter in the intent's
 * extras, so an unset project or task is left out rather than null.
 */
private fun EntryFields.toParameters(account: String): ActionParameters {
    val pairs = buildList {
        add(ACCOUNT to account)
        add(DESCRIPTION to description)
        projectId?.let { add(PROJECT_ID to it.value) }
        taskId?.let { add(TASK_ID to it.value) }
        add(TAGS to tags.toTypedArray())
        add(BILLABLE to billable)
    }
    return actionParametersOf(*pairs.toTypedArray())
}

private fun ActionParameters.fields(): EntryFields = EntryFields(
    description = this[DESCRIPTION].orEmpty(),
    projectId = this[PROJECT_ID]?.let(::ProjectId),
    taskId = this[TASK_ID]?.let(::TaskId),
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
