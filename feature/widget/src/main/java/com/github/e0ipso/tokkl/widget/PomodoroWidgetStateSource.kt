package com.github.e0ipso.tokkl.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timersync.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timersync.PomodoroRingingPhase
import com.github.e0ipso.tokkl.timersync.PomodoroWidgetRedraw
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/**
 * The Pomodoro widget's held session and idle defaults. Read the current account under its
 * holder lock, rather than combining a previous account's emitted state with new settings.
 * The ringing service and a single in-process phase-end wait trigger redraws. The latter
 * also works with denied exact alarms. No ticking job or idle work is added.
 */
class PomodoroWidgetStateSource(
    fields: PomodoroWidgetFieldsStore,
    timerStates: AccountTimerStates,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    ringing: PomodoroRingingPhase,
    capabilities: Flow<Capabilities>,
    accountSettings: Flow<AccountSettings?>,
    pomodoroDefaults: PomodoroDefaultsSetting,
    signedIn: Session,
    clock: Clock,
    io: CoroutineContext,
) {

    val state: Flow<PomodoroWidgetState> = run {
        val deadlines = pomodoroWidgetDeadlineChanges(
            timerStates.held.map { held ->
                held?.let { it.pomodoro?.phase?.plannedEnd?.minus(it.clockOffset) }
            },
            clock,
        )
        val catalog = combine(
            projects,
            tasks,
            ringing.ringingPhase,
            deadlines,
        ) { cachedProjects, cachedTasks, ring, _ ->
            Triple(cachedProjects, cachedTasks, ring)
        }
        combine(
            timerStates.held,
            capabilities,
            accountSettings,
            pomodoroDefaults.loadedDefaults,
            catalog,
        ) { _, flags, settings, defaults, (cachedProjects, cachedTasks, ringing) ->
            val isSignedIn = signedIn.isSignedIn()
            timerStates.withHeld { owner, held ->
                pomodoroWidgetState(
                    workspaceId = settings?.defaultWorkspaceId,
                    account = owner.takeIf { isSignedIn },
                    available = flags.timerState,
                    held = held,
                    defaults = defaults,
                    remembered = fields.load(owner),
                    projects = cachedProjects,
                    tasks = cachedTasks,
                    deviceNow = clock.instant(),
                    ringing = ringing,
                )
            }
        }.flowOn(io).distinctUntilChanged()
    }
}

/**
 * A single redraw at the held phase's corrected end, independent of alarm permission. The
 * live sync process needs no alarm, polling or wake lock for this wait. A changed phase or
 * session end cancels it; idle schedules nothing. A restored overdue phase draws immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun pomodoroWidgetDeadlineChanges(ends: Flow<Instant?>, clock: Clock): Flow<Unit> =
    ends.distinctUntilChanged().flatMapLatest { end ->
        flow {
            emit(Unit)
            val now = clock.instant()
            val remaining = end?.let {
                val milliseconds = Duration.between(now, it).toMillis()
                // delay() takes whole milliseconds. Rounding down could redraw just before the
                // boundary, leaving ended=false with no later event when exact alarms are denied.
                milliseconds + if (now.plusMillis(milliseconds).isBefore(it)) 1L else 0L
            } ?: 0L
            if (remaining > 0L) {
                delay(remaining)
                emit(Unit)
            }
        }
    }

/**
 * Redraws the Pomodoro widget once the held phase that [PomodoroWidgetRedraw.redraw] names has
 * ended, or arms the redraw again at its planned end. Another phase changes nothing.
 */
internal class PomodoroWidgetRedrawer(
    private val context: Context,
    private val states: PomodoroWidgetStateSource,
    private val scheduler: PomodoroAlarmScheduler,
) : PomodoroWidgetRedraw {

    override suspend fun redraw(phase: PomodoroPhaseKey) {
        val state = states.state.first() as? PomodoroWidgetState.Active
        if (state?.session?.phase?.start == phase.start) {
            if (state.ended) {
                PomodoroWidget().updateAll(context)
            } else {
                scheduler.schedule(state.end, phase)
            }
        }
    }
}
