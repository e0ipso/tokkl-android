package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Instant

internal sealed interface PomodoroWidgetTap {
    val account: String
    data class Start(override val account: String, val entry: PomodoroWidgetEntry, val defaults: PomodoroDefaults) :
        PomodoroWidgetTap
    data class Phase(override val account: String, val revision: Long, val start: Instant, val endSession: Boolean) :
        PomodoroWidgetTap
}

internal suspend fun sendPomodoroWidgetAction(
    tap: PomodoroWidgetTap,
    current: PomodoroWidgetState,
    actions: SessionActions,
): TimerActionResult? {
    val account = when (current) {
        is PomodoroWidgetState.Idle -> current.account
        is PomodoroWidgetState.Active -> current.account
        else -> return null
    }
    if (tap.account != account) return null
    return when (tap) {
        is PomodoroWidgetTap.Start -> {
            if (current !is PomodoroWidgetState.Idle) return TimerActionResult.Refused
            val lengths = tap.defaults.run {
                com.github.e0ipso.tokkl.timerstate.SessionLengths(
                    workMinutes,
                    shortRestMinutes,
                    longRestMinutes,
                    longRestEvery,
                )
            }
            actions.startSession(tap.entry.workspaceId, tap.entry.fields, lengths)
        }

        is PomodoroWidgetTap.Phase -> {
            if (current !is PomodoroWidgetState.Active || current.session.phase.start != tap.start) return null
            if (tap.endSession) actions.endSession(tap.revision) else actions.nextPhase(tap.revision)
        }
    }
}

internal data class PomodoroWidgetClock(val base: Long, val countDown: Boolean)
internal fun pomodoroWidgetClock(
    state: PomodoroWidgetState.Active,
    now: Instant,
    elapsedRealtime: Long,
): PomodoroWidgetClock = PomodoroWidgetClock(
    base = elapsedRealtime + java.time.Duration.between(now, state.end).toMillis(),
    countDown = !state.ended,
)

/** Parameters capture what was drawn; a later state never substitutes its revision or defaults. */
internal fun startPomodoroWidget(state: PomodoroWidgetState.Idle): Action =
    actionRunCallback<StartPomodoroWidgetAction>(
        actionParametersOf(
            ACCOUNT to state.account,
            FIELDS to rememberedFieldsJson(state.account, state.entry),
            WORK to state.defaults.workMinutes,
            SHORT to state.defaults.shortRestMinutes,
            LONG to state.defaults.longRestMinutes,
            EVERY to state.defaults.longRestEvery,
        ),
    )

internal fun rememberedFieldsJson(account: String, entry: PomodoroWidgetEntry): String {
    val fields = entry.fields
    val saved = RememberedPomodoroFields(
        account,
        entry.workspaceId,
        fields.description,
        fields.projectId,
        fields.taskId,
        fields.tags,
        fields.billable,
    )
    return ApiJson.encodeToString(RememberedPomodoroFields.serializer(), saved)
}

/**
 * The Start tap for [account] with the fields in [fieldsJson], as [rememberedFieldsJson] wrote
 * them, and the four lengths, or null when one is missing or the fields name another account.
 */
internal fun startTap(
    account: String?,
    fieldsJson: String?,
    work: Int?,
    short: Int?,
    long: Int?,
    every: Int?,
): PomodoroWidgetTap.Start? = runCatching {
    val saved = ApiJson.decodeFromString(RememberedPomodoroFields.serializer(), fieldsJson ?: return null)
    if (account == null || saved.account != account) return null
    PomodoroWidgetTap.Start(
        account,
        PomodoroWidgetEntry(saved.workspaceId, saved.fields(), null, null, null),
        PomodoroDefaults(work ?: return null, short ?: return null, long ?: return null, every ?: return null),
    )
}.getOrNull()

/** The Next phase or End session tap, or null when a value is missing or the phase does not read. */
internal fun phaseTap(account: String?, revision: Long?, phase: String?, end: Boolean?): PomodoroWidgetTap.Phase? =
    runCatching {
        PomodoroWidgetTap.Phase(
            account ?: return null,
            revision ?: return null,
            Instant.parse(phase ?: return null),
            end ?: return null,
        )
    }.getOrNull()

internal fun phasePomodoroWidget(state: PomodoroWidgetState.Active, end: Boolean = false): Action =
    actionRunCallback<PhasePomodoroWidgetAction>(
        actionParametersOf(
            ACCOUNT to state.account,
            REVISION to state.revision,
            PHASE to state.session.phase.start.toString(),
            END to end,
        ),
    )

/**
 * The widget's Start. The session starts on the server, so the tap goes to WorkManager
 * ([sendPomodoroWidgetTap]), which keeps the process alive while the server answers.
 */
class StartPomodoroWidgetAction :
    ActionCallback,
    KoinComponent {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // A parameter of another type, as from a widget drawn by another version, starts nothing.
        val tap = runCatching {
            startTap(
                parameters[ACCOUNT],
                parameters[FIELDS],
                parameters[WORK],
                parameters[SHORT],
                parameters[LONG],
                parameters[EVERY],
            )
        }.getOrNull() ?: return
        get<CurrentWorkManager>().sendPomodoroWidgetTap(tap)
    }
}

/** The widget's Next phase and End session, which go to the server through WorkManager as Start does. */
class PhasePomodoroWidgetAction :
    ActionCallback,
    KoinComponent {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tap = runCatching {
            phaseTap(parameters[ACCOUNT], parameters[REVISION], parameters[PHASE], parameters[END])
        }.getOrNull() ?: return
        get<CurrentWorkManager>().sendPomodoroWidgetTap(tap)
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
