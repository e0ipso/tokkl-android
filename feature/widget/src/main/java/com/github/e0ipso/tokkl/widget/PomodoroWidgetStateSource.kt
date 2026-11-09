package com.github.e0ipso.tokkl.widget

import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timersync.PomodoroRingingPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * The Pomodoro widget's held session and idle defaults. Read the current account under its
 * holder lock, rather than combining a previous account's emitted state with new settings.
 * The ringing service and a single in-process phase-end wait trigger redraws. The latter
 * also works with denied exact alarms. No ticking job or idle work is added.
 *
 * Public for the app's device tests, which read the widget's state from the app's Koin graph.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
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
