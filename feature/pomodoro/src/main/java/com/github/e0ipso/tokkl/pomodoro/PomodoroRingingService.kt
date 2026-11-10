package com.github.e0ipso.tokkl.pomodoro

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timersync.PomodoroRingingPhase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext

/**
 * Stop releases both outputs on the main thread before returning, including when the phone has no
 * network. A queued service start still checks the held phase before it plays.
 */
class ServicePomodoroRinging(private val context: Context, private val main: CoroutineContext) :
    PomodoroRinging,
    PomodoroRingingPhase {
    internal var service: PomodoroRingingService? = null
    private val observed = MutableStateFlow(PomodoroRingingState())

    /** Whether the service runs, and what it rings, which the session notification follows. */
    internal val state = observed.asStateFlow()
    override val ringingPhase: Flow<PomodoroPhaseKey?> = state.map { it.ring?.phase }

    fun running(): Boolean = state.value.serviceRunning

    internal fun update(value: PomodoroRingingState) {
        observed.value = value
    }

    override suspend fun start(ring: PomodoroRing) = withContext(main) {
        val active = service
        if (active != null && active.current?.phase == ring.phase) {
            active.show(ring)
        } else {
            try {
                context.startForegroundService(
                    Intent(context, PomodoroRingingService::class.java).setAction(START).putPhase(ring.phase),
                )
            } catch (_: ForegroundServiceStartNotAllowedException) {
                // A start the platform refuses from the background rings nothing. An alarm
                // delivery is allowed to start it, and the armed alarm still goes off.
            }
        }
        Unit
    }

    override suspend fun update(ring: PomodoroRing) = withContext(main) {
        service?.takeIf { it.current?.phase == ring.phase }?.show(ring)
        Unit
    }

    override suspend fun stop(keep: PomodoroPhaseKey?) = withContext(main) {
        val active = service
        // A start still checking the held phase stops itself when that phase has gone.
        if (keep != null && (active?.current == null || active.current?.phase == keep)) return@withContext
        active?.finishRinging()
        context.stopService(Intent(context, PomodoroRingingService::class.java))
        Unit
    }

    companion object {
        const val START = "com.github.e0ipso.tokkl.action.RING_POMODORO"
    }
}

/** Whether the ringing service runs, and the ring it plays, or null while it plays none. */
internal data class PomodoroRingingState(val serviceRunning: Boolean = false, val ring: PomodoroRing? = null)

/**
 * One indefinite alarm, with no phase advancement. START_STICKY restores a killed service only
 * while the persisted held state still has the phase it rang, past its planned end. Swiping its
 * notification reposts it, and removing the app's task leaves playback running.
 *
 * mediaPlayback permits background audio with FOREGROUND_SERVICE and, on API 34+,
 * FOREGROUND_SERVICE_MEDIA_PLAYBACK. It has no shortService/dataSync timeout. Boot recovery
 * delivers an exact alarm instead of starting this service from BOOT_COMPLETED on API 35+.
 * https://developer.android.com/develop/background-work/services/fgs/service-types#media
 * https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#exemptions
 */
class PomodoroRingingService : Service() {
    private val main: CoroutineDispatcher by inject(MainDispatcher)
    private val scope by lazy { CoroutineScope(SupervisorJob() + main) }
    private val ringing: ServicePomodoroRinging by inject()
    private val ringCheck: PomodoroRingCheck by inject()

    // These two need the service as a Context, which Android attaches only just before
    // onCreate, and onCreate makes them in this order before anything reads them.
    private lateinit var notifier: PomodoroRingingNotifier
    private lateinit var controller: PomodoroRingController

    internal val current: PomodoroRing? get() = controller.current

    /** Whether this service has posted its notification yet. */
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        notifier = PomodoroRingingNotifier(this)
        notifier.createChannel()
        controller = PomodoroRingController(AndroidPomodoroRingOutput(this, notifier))
        ringing.service = this
        publishState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val expected = intent?.phaseKey()
        if (!promoted) {
            // The session notification shares the id. Android opens a full-screen intent only for
            // a notification it adds, not for one that replaces another, so remove it first.
            getSystemService(NotificationManager::class.java).cancel(PomodoroSessionNotifier.NOTIFICATION_ID)
        }
        // Meet startForeground's deadline before reading the held state. This provisional
        // notification plays nothing and has no actions; it gets them only after the check below.
        promote(notifier.build(current, current?.phase ?: expected))
        if (intent != null && (intent.action != ServicePomodoroRinging.START || expected == null)) {
            if (current == null) finishRinging(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            val valid = ringCheck.withRingingPhase(expected) { ring -> withContext(main) { show(ring) } }
            // An old start must neither play nor stop a newer valid ring.
            if (!valid && current == null) finishRinging(startId)
        }
        return START_STICKY
    }

    internal fun promote(notification: Notification) {
        startForeground(
            PomodoroSessionNotifier.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        promoted = true
    }

    private fun publishState() {
        if (ringing.service === this) ringing.update(PomodoroRingingState(serviceRunning = true, ring = current))
    }

    /** Rings [ring], or shows it for the phase already ringing, as after a swipe or a new revision. */
    internal fun show(ring: PomodoroRing) {
        controller.start(ring)
        publishState()
    }

    internal fun finishRinging(startId: Int? = null) {
        controller.stop()
        publishState()
        if (startId == null) stopSelf() else stopSelfResult(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        controller.stop()
        if (ringing.service === this) {
            ringing.service = null
            ringing.update(PomodoroRingingState())
        }
        super.onDestroy()
    }
}
