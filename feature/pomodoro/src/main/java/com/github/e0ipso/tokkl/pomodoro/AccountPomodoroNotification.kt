package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOutOrder
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.TimerActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext

/**
 * The signed-in account's Pomodoro session notification. Made on first use, restoring what the
 * last process showed. Sign-out [renew]s it, so the next account starts with nothing shown.
 */
class AccountPomodoroNotification internal constructor(
    private val context: Context,
    private val session: Session,
    private val alarm: PomodoroPhaseAlarm,
    private val actions: TimerActions,
    private val held: HeldTimerFlows,
    private val capabilities: CapabilitiesSource,
    private val ringing: ServicePomodoroRinging,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AccountDataOwner,
    SignedInListener {

    override val signOutOrder = SignOutOrder.POMODORO_NOTIFICATION

    override suspend fun forgetAccount() = current.forget()

    /** Starts the notification of the account that has just signed in. */
    override fun signedIn() = current.start()

    private val lock = Any()
    private var forAccount = newNotification(restore = true)

    /** The notification of the account signed in now. Take it once per action. */
    val current: PomodoroSessionNotificationSync
        get() = synchronized(lock) { forAccount.value }

    /** Replaces [current] with one that restores nothing. */
    fun renew() {
        synchronized(lock) { forAccount = newNotification(restore = false) }
    }

    private fun newNotification(restore: Boolean): Lazy<PomodoroSessionNotificationSync> = lazy {
        val store = PomodoroSessionNotificationStore(context)
        val notifier = PomodoroSessionNotifier(context)
        PomodoroSessionNotificationSync(
            alarm = alarm,
            actions = actions,
            changes = combine(held.held, flow { emitAll(capabilities.state) }, ringing.state) { _, _, _ -> Unit },
            signedIn = { session.isSignedIn() },
            serviceRunning = { ringing.state.value.serviceRunning },
            load = {
                if (restore) store.load() else null
            }, save = store::save, post = notifier::post, remove = notifier::remove,
            scope = scope, io = io,
        )
    }
}
