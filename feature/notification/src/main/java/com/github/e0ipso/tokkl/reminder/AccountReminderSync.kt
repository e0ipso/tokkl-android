package com.github.e0ipso.tokkl.reminder

import android.content.Context
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import kotlinx.coroutines.flow.first

/**
 * The signed-in account's reminder: one per running id and start, followed independently of any
 * screen, with its delay in WorkManager and its notification from [ReminderNotifier]. Its Pause
 * shows, and goes to the server, only where the timer controls offer Pause for that entry.
 */
internal fun accountReminderSync(
    context: Context,
    store: ReminderStore,
    session: Session,
    changes: ChangeQueue,
    cache: AccountCache,
    settings: ReminderSetting,
    controls: TimerControlsSource,
    surfaceActions: TimerSurfaceActions,
    work: ReminderWork,
): ReminderSync {
    val notifier = ReminderNotifier(context)

    suspend fun runningNow(entryId: Long) = (controls.current() as? TimerControls.Running)?.takeIf {
        it.entry.id ==
            entryId
    }

    return ReminderSync(
        latest = { ReminderInput(cache.running.first()?.entry?.takeIf { session.isSignedIn() }, settings.state.value) },
        currentId = changes::currentId,
        timers = store.timers,
        schedule = work::schedule,
        cancel = work::cancel,
        post = { timer -> notifier.post(timer, pausable = runningNow(changes.currentId(timer.id))?.pausable == true) },
        remove = notifier::remove,
        stop = changes::stop,
        pause = { entryId ->
            runningNow(entryId)?.let { surfaceActions.tap(TimerSurfaceAction.Pause, it.target) as? SurfaceTap.Request }
        },
    )
}
