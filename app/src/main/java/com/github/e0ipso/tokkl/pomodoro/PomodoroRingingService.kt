package com.github.e0ipso.tokkl.pomodoro

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.github.e0ipso.tokkl.TokklApplication
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ringing service as [PomodoroPhaseAlarm] drives it. Stop releases both outputs on the main
 * thread before returning, including when the phone has no network. A queued service start still
 * checks the held phase before it plays.
 */
internal class ServicePomodoroRinging(private val context: Context) : PomodoroRinging {
    internal var service: PomodoroRingingService? = null
    private val observed = MutableStateFlow(PomodoroRingingState())
    internal val state = observed.asStateFlow()

    internal fun update(value: PomodoroRingingState) { observed.value = value }

    override suspend fun start(ring: PomodoroRing) = withContext(Dispatchers.Main.immediate) {
        val active = service
        if (active != null && active.current?.phase == ring.phase) {
            active.show(ring)
        } else {
            try {
                context.startForegroundService(
                    Intent(context, PomodoroRingingService::class.java).setAction(START).putPhase(ring.phase),
                )
            } catch (e: ForegroundServiceStartNotAllowedException) {
                // A start the platform refuses from the background rings nothing. An alarm
                // delivery is allowed to start it, and the armed alarm still goes off.
            }
        }
        Unit
    }

    override suspend fun update(ring: PomodoroRing) = withContext(Dispatchers.Main.immediate) {
        service?.takeIf { it.current?.phase == ring.phase }?.show(ring)
        Unit
    }

    override suspend fun stop(keep: PomodoroPhaseKey?) = withContext(Dispatchers.Main.immediate) {
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

/** Observations of the actual framework player and the last vibrator request, for device tests. */
internal data class PomodoroRingingState(
    val serviceRunning: Boolean = false,
    val ring: PomodoroRing? = null,
    val playing: Boolean = false,
    val looping: Boolean = false,
    val audioUsage: Int? = null,
    val audioSessionId: Int? = null,
    val vibrationRequested: Boolean = false,
    val vibrationUsage: Int? = null,
    val vibrationPattern: List<Long> = emptyList(),
    val vibrationRepeat: Int? = null,
    val foregroundType: Int = 0,
)

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as TokklApplication
    private lateinit var notifier: PomodoroRingingNotifier
    internal lateinit var output: AndroidPomodoroRingOutput
        private set
    private lateinit var controller: PomodoroRingController
    internal val current: PomodoroRing? get() = controller.current

    /** Whether this service has posted its notification yet. */
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        notifier = PomodoroRingingNotifier(this)
        notifier.createChannel()
        output = AndroidPomodoroRingOutput(this, notifier)
        controller = PomodoroRingController(output)
        app.pomodoroRinging.service = this
        publishState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val done = processed
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
            done?.countDown()
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                val valid = app.pomodoroAlarm.withRingingPhase(expected) { ring ->
                    withContext(Dispatchers.Main.immediate) { controller.start(ring) }
                }
                // An old start must neither play nor stop a newer valid ring.
                if (!valid && current == null) finishRinging(startId)
            } finally {
                done?.countDown()
            }
        }
        return START_STICKY
    }

    internal fun promote(notification: Notification) {
        startForeground(PomodoroSessionNotifier.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        promoted = true
    }

    internal fun publishState() {
        if (app.pomodoroRinging.service !== this) return
        app.pomodoroRinging.update(
            PomodoroRingingState(
                serviceRunning = true, ring = current, playing = output.playing, looping = output.looping,
                audioUsage = output.audioAttributes.usage, audioSessionId = output.audioSessionId,
                vibrationRequested = output.vibrationRequested, vibrationUsage = output.vibrationUsage,
                vibrationPattern = output.vibrationPattern, vibrationRepeat = output.vibrationRepeat,
                foregroundType = foregroundServiceType,
            ),
        )
    }

    /** Shows [ring] of the phase already ringing, as after a swipe or a new revision. */
    internal fun show(ring: PomodoroRing) = controller.start(ring)

    internal fun finishRinging(startId: Int? = null) {
        controller.stop()
        if (startId == null) stopSelf() else stopSelfResult(startId)
    }

    companion object {
        /** Device tests await intent validation. Production leaves this null. */
        @Volatile internal var processed: CountDownLatch? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        controller.stop()
        if (app.pomodoroRinging.service === this) {
            app.pomodoroRinging.service = null
            app.pomodoroRinging.update(PomodoroRingingState())
        }
        super.onDestroy()
    }
}
