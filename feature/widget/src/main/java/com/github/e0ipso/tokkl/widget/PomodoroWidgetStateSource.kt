package com.github.e0ipso.tokkl.widget

import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.PomodoroRingingPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    held: HeldTimerFlows,
    timerStates: AccountTimerStates,
    cache: AccountCache,
    ringing: PomodoroRingingPhase,
    capabilities: CapabilitiesSource,
    accountSettings: AccountSettingsSource,
    pomodoroDefaults: PomodoroDefaultsSetting,
    session: Session,
    clock: Clock,
    io: CoroutineContext,
) {

    val state: Flow<PomodoroWidgetState> = run {
        val deadlines = pomodoroWidgetDeadlineChanges(
            held.held.map { held ->
                held?.let { it.state.pomodoro?.phase?.plannedEnd?.minus(it.clockOffset) }
            },
            clock,
        )
        val catalog = combine(
            cache.projects,
            cache.tasks,
            ringing.ringingPhase,
            deadlines,
        ) { projects, tasks, ring, _ ->
            Triple(projects, tasks, ring)
        }
        combine(
            held.held,
            flow { emitAll(capabilities.state) },
            flow { emitAll(accountSettings.state) },
            flow { emitAll(pomodoroDefaults.defaults) },
            catalog,
        ) { _, flags, settings, defaults, (projects, tasks, ringing) ->
            val owner = timerStates.current()
            owner.source.withState { held ->
                pomodoroWidgetState(
                    workspaceId = settings?.defaultWorkspaceId,
                    account = owner.account.takeIf { session.isSignedIn() },
                    available = flags.timerState,
                    held = held,
                    defaults = defaults,
                    remembered = fields.load(owner.account),
                    projects = projects,
                    tasks = tasks,
                    deviceNow = clock.instant(),
                    ringing = ringing,
                )
            }
        }.flowOn(io).distinctUntilChanged()
    }
}
