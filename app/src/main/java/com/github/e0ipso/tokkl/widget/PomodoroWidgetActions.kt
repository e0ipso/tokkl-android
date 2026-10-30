package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timer.showTimerActionFailure
import com.github.e0ipso.tokkl.timerstate.TimerActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant

internal sealed interface PomodoroWidgetTap {
    val account: String
    data class Start(override val account: String, val entry: PomodoroWidgetEntry, val defaults: PomodoroDefaults) : PomodoroWidgetTap
    data class Phase(override val account: String, val revision: Long, val start: Instant, val endSession: Boolean) : PomodoroWidgetTap
}

internal suspend fun sendPomodoroWidgetAction(tap: PomodoroWidgetTap, current: PomodoroWidgetState, actions: SessionActions): TimerActionResult? {
    val account = when (current) {
        is PomodoroWidgetState.Idle -> current.account
        is PomodoroWidgetState.Active -> current.account
        else -> return null
    }
    if (tap.account != account) return null
    return when (tap) {
        is PomodoroWidgetTap.Start -> {
            if (current !is PomodoroWidgetState.Idle) return TimerActionResult.Refused
            val lengths = tap.defaults.run { com.github.e0ipso.tokkl.timerstate.SessionLengths(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery) }
            actions.startSession(tap.entry.workspaceId, tap.entry.fields, lengths)
        }
        is PomodoroWidgetTap.Phase -> {
            if (current !is PomodoroWidgetState.Active || current.session.phase.start != tap.start) return null
            if (tap.endSession) actions.endSession(tap.revision) else actions.nextPhase(tap.revision)
        }
    }
}

internal data class PomodoroWidgetClock(val base: Long, val countDown: Boolean, val progress: Float)
internal fun pomodoroWidgetClock(state: PomodoroWidgetState.Active, now: Instant, elapsedRealtime: Long): PomodoroWidgetClock {
    val duration = java.time.Duration.between(state.start, state.end).toMillis()
    val elapsed = java.time.Duration.between(state.start, now).toMillis()
    return PomodoroWidgetClock(
        base = elapsedRealtime + java.time.Duration.between(now, state.end).toMillis(),
        countDown = !state.ended,
        progress = if (state.ended || duration <= 0) 1f else (elapsed.toDouble() / duration).toFloat().coerceIn(0f, 1f),
    )
}

/** Parameters capture what was drawn; a later state never substitutes its revision or defaults. */
internal fun startPomodoroWidget(state: PomodoroWidgetState.Idle): Action {
    val entry = state.entry
    val fields = entry.fields
    val saved = RememberedPomodoroFields(state.account, entry.workspaceId, fields.description, fields.projectId, fields.taskId, fields.tags, fields.billable)
    return actionRunCallback<StartPomodoroWidgetAction>(actionParametersOf(
        ACCOUNT to state.account,
        FIELDS to ApiJson.encodeToString(RememberedPomodoroFields.serializer(), saved),
        WORK to state.defaults.workMinutes, SHORT to state.defaults.shortRestMinutes,
        LONG to state.defaults.longRestMinutes, EVERY to state.defaults.longRestEvery,
    ))
}

internal fun phasePomodoroWidget(state: PomodoroWidgetState.Active, end: Boolean = false): Action =
    actionRunCallback<PhasePomodoroWidgetAction>(actionParametersOf(
        ACCOUNT to state.account, REVISION to state.revision,
        PHASE to state.session.phase.start.toString(), END to end,
    ))

class StartPomodoroWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tap = runCatching {
            val account = parameters[ACCOUNT] ?: return
            val saved = ApiJson.decodeFromString(RememberedPomodoroFields.serializer(), parameters[FIELDS] ?: return)
            if (saved.account != account) return
            PomodoroWidgetTap.Start(account, PomodoroWidgetEntry(saved.workspaceId, saved.fields(), null, null, null),
                PomodoroDefaults(parameters[WORK] ?: return, parameters[SHORT] ?: return, parameters[LONG] ?: return, parameters[EVERY] ?: return))
        }.getOrNull() ?: return
        dispatchPomodoroWidget(context, tap)
    }
}

class PhasePomodoroWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tap = runCatching {
            PomodoroWidgetTap.Phase(parameters[ACCOUNT] ?: return, parameters[REVISION] ?: return,
                Instant.parse(parameters[PHASE] ?: return), parameters[END] ?: return)
        }.getOrNull() ?: return
        dispatchPomodoroWidget(context, tap)
    }
}

/** A server request must outlive Glance's broadcast, like the shipped Timer widget actions. */
private fun dispatchPomodoroWidget(context: Context, tap: PomodoroWidgetTap) {
    val app = context.applicationContext as TokklApplication
    val source = app.timerState
    CoroutineScope(Dispatchers.IO).launch {
        val current = app.pomodoroWidgetState.first()
        if (app.timerState !== source || !Session(app).isSignedIn()) return@launch
        val client = Session(app).client()
        // Keep the captured holder and credential together across sign-out/account replacement.
        val actions = TimerActions(client = { client.takeIf { app.timerState === source && Session(app).isSignedIn() } }, source = { source }, io = Dispatchers.IO)
        sendPomodoroWidgetAction(tap, current, actions)?.let { showTimerActionFailure(app, it) }
    }
}

private val ACCOUNT = ActionParameters.Key<String>("pomodoroWidgetAccount")
private val REVISION = ActionParameters.Key<Long>("pomodoroWidgetRevision")
private val PHASE = ActionParameters.Key<String>("pomodoroWidgetPhase")
private val END = ActionParameters.Key<Boolean>("pomodoroWidgetEnd")
private val FIELDS = ActionParameters.Key<String>("pomodoroWidgetFields")
private val WORK = ActionParameters.Key<Int>("pomodoroWidgetWork")
private val SHORT = ActionParameters.Key<Int>("pomodoroWidgetShort")
private val LONG = ActionParameters.Key<Int>("pomodoroWidgetLong")
private val EVERY = ActionParameters.Key<Int>("pomodoroWidgetEvery")
