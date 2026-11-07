package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.api.TimerPomodoro
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant

/** Session-only inputs. Editing these never changes the account's defaults. */
data class PomodoroLengths(
    val work: String,
    val shortRest: String,
    val longRest: String,
    val every: String,
) {
    /**
     * The four lengths a session starts with, or null while one is not a whole number in the
     * range Tokkl accepts. Tokkl answers a length outside its range with 400, so the form checks first.
     */
    fun sessionLengths(): SessionLengths? {
        val work = work.toIntOrNull()?.takeIf { it in PomodoroDefaults.WORK_MINUTES } ?: return null
        val short = shortRest.toIntOrNull()?.takeIf { it in PomodoroDefaults.SHORT_REST_MINUTES } ?: return null
        val long = longRest.toIntOrNull()?.takeIf { it in PomodoroDefaults.LONG_REST_MINUTES } ?: return null
        val every = every.toIntOrNull()?.takeIf { it in PomodoroDefaults.LONG_REST_EVERY } ?: return null
        return SessionLengths(work, short, long, every)
    }

    companion object {
        fun from(defaults: PomodoroDefaults) = PomodoroLengths(
            defaults.workMinutes.toString(), defaults.shortRestMinutes.toString(),
            defaults.longRestMinutes.toString(), defaults.longRestEvery.toString(),
        )
    }
}

enum class PomodoroAction { START, NEXT, END }

/** The action that did not go through, and how it ended. [result] is never [TimerActionResult.Done]. */
data class PomodoroProblem(val action: PomodoroAction, val result: TimerActionResult)

/**
 * What the page drew its controls from: the server revision, the phase it showed and the open
 * pomodoro's entry. Next phase and End send [revision], and a field edit goes to [entryId], so a
 * tap on a page drawn before another device changed the timer gets the server's refusal and
 * cannot act on a phase or an entry the user never saw.
 */
data class PomodoroShown(val revision: Long, val phaseStart: Instant, val entryId: Long)

data class PomodoroPageState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val lengths: PomodoroLengths = PomodoroLengths.from(PomodoroDefaults.DEFAULT),
    /** The start form's entry fields while idle, and the open pomodoro's while a session runs. */
    val fields: EntryFields = EntryFields(),
    /** The server's timer state with the time the phone got it. */
    val held: HeldTimerState? = null,
    val account: AccountSettings? = null,
    val alarmDenied: Boolean = false,
    val fullScreenDenied: Boolean = false,
    val notificationsDenied: Boolean = false,
    val unsentCount: Int? = null,
    val refused: List<PendingChange> = emptyList(),
    val problem: PomodoroProblem? = null,
) {
    /** The server's session, or null without one. */
    val pomodoro: TimerPomodoro? get() = held?.state?.pomodoro

    val shown: PomodoroShown?
        get() {
            val state = held?.state ?: return null
            val session = state.pomodoro ?: return null
            return PomodoroShown(state.revision, session.phase.start, session.entryId)
        }

    /** The session's workspace while it runs, and the account's default for a new one. */
    val workspaceId: Long?
        get() = held?.state?.takeIf { it.pomodoro != null }?.runningEntry?.workspaceId ?: account?.defaultWorkspaceId

    val canStart: Boolean get() = !loading && !busy && pomodoro == null && workspaceId != null && lengths.sessionLengths() != null
}
