package com.github.e0ipso.tokkl.timerstate

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.session.Session

/**
 * Decides whether the timer event stream and [TimerSyncService] run, and starts, points or stops
 * them to match.
 */
class TimerSyncFollower(
    private val context: Context,
    private val session: Session,
    private val timerStates: AccountTimerStates,
    private val timerSync: Lazy<TimerSync<TimerStateSource>>,
    private val capabilities: Lazy<CapabilitiesSource>,
    private val sender: Lazy<ChangeSender>,
    private val alarmClock: AlarmClockPomodoroScheduler,
) {

    /** [TimerSyncService] while it runs in the foreground. The service sets it. */
    @Volatile
    internal var service: TimerSyncService? = null

    /**
     * Whether the timer event stream should run: signed in to a server with the timer API, whose
     * token the server has not refused. Reads the disk.
     */
    internal fun wanted(): Boolean =
        capabilities.value.state.value.timerState && !sender.value.signInNeeded.value && session.isSignedIn()

    /**
     * Starts [TimerSyncService], or points the running one at the account signed in now, while
     * [wanted], and stops the stream and the service otherwise. App open, every account refresh,
     * sign-in and the boot and update receiver call it. A start the platform refuses, as in the
     * background, is dropped: the next app open starts the service, and an exact alarm already
     * set still rings. Blocks on disk I/O.
     */
    fun follow() {
        alarmClock.watchTimerSync()
        val running = service
        if (!wanted()) {
            timerSync.value.stop()
            stopService()
        } else if (running != null) {
            run(running)
        } else {
            TimerSyncService.start(context)
        }
    }

    /**
     * Runs the stream for the account signed in now, for as long as [running] is the service and
     * the stream is still wanted.
     */
    internal fun run(running: TimerSyncService) {
        timerSync.value.run(timerStates.source) {
            service === running && capabilities.value.state.value.timerState && !sender.value.signInNeeded.value
        }
    }

    /** Stops [TimerSyncService] unless a newer start is on its way, which then decides for itself. */
    fun stopService() {
        Handler(Looper.getMainLooper()).post { service?.stopLatest() }
    }
}
