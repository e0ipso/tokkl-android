package com.github.e0ipso.tokkl.reminder

import android.content.Context
import com.github.e0ipso.tokkl.cache.CurrentEntryIds
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.session.SignInStatus
import com.github.e0ipso.tokkl.timerstate.CurrentTimerState
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The signed-in account's reminder: one per running id and start, followed independently of any
 * screen, with its delay in WorkManager and its notification from [ReminderNotifier]. Its Pause
 * shows, and goes to the server, only where the timer controls offer Pause for that entry.
 */
internal fun accountReminderSync(
    context: Context,
    store: ReminderStore,
    account: AccountReminder,
    changes: CurrentEntryIds,
    work: ReminderWork,
): ReminderSync {
    val notifier = ReminderNotifier(context)
    return ReminderSync(
        account = account,
        ids = changes,
        timers = store.timers,
        delays = work,
        notice = object : ReminderNotice {
            override suspend fun post(timer: ReminderTimer) = notifier.post(
                timer,
                pausable = account.runningNow(changes.currentId(EntryId(timer.id)))?.pausable == true,
            )

            override fun remove() = notifier.remove()
        },
    )
}

/**
 * The reminder's view of the signed-in account: its running entry, its Stop in the queue and its
 * Pause.
 *
 * @param running the cache's running entry, or null while none runs.
 */
internal class AccountReminder(
    private val session: SignInStatus,
    private val running: Flow<EntryWithSendState?>,
    private val settings: ReminderSetting,
    private val changes: RunningEntryChanges,
    private val controls: CurrentTimerState,
    private val surfaceActions: TimerSurfaceActions,
) : ReminderAccount {

    override suspend fun input() =
        ReminderInput(running.first()?.entry?.takeIf { session.isSignedIn() }, settings.current())

    override suspend fun stop(entryId: EntryId) = changes.stop(entryId)

    override suspend fun pause(entryId: EntryId) =
        runningNow(entryId)?.let { surfaceActions.tap(TimerSurfaceAction.Pause, it.target) as? SurfaceTap.Request }

    /** The running controls of [entryId], or null while another entry or none runs. */
    suspend fun runningNow(entryId: EntryId) =
        (controls.currentControls() as? TimerControls.Running)?.takeIf { it.entry.id == entryId }
}
