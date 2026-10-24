package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.github.e0ipso.tokkl.TokklApplication
import java.io.IOException
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
 * The store calls start/stop under its lock. Stop releases both outputs on the main thread
 * before returning, including when the phone has no network. Queued service intents still
 * have to validate the stored phase before playing.
 */
internal class ServicePomodoroRinging(private val context: Context) : PomodoroRinging {
    internal var service: PomodoroRingingService? = null
    private val observed = MutableStateFlow(PomodoroRingingState())
    internal val state = observed.asStateFlow()

    internal fun update(value: PomodoroRingingState) { observed.value = value }

    override suspend fun start(ring: PomodoroRing) = withContext(Dispatchers.Main.immediate) {
        val active = service
        if (active != null && active.current == ring) {
            active.repost(ring.phase)
        } else {
            context.startForegroundService(
                Intent(context, PomodoroRingingService::class.java).setAction(START).putPhase(ring.phase),
            )
        }
        Unit
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        service?.finishRinging()
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
 * after re-reading the persisted phase-end record and current session. Swiping its notification
 * reposts it, and removing the app's task leaves playback running.
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
        // Meet startForeground's deadline before opening Room. This provisional notification
        // plays nothing. A sticky restart gets its actions only after validation.
        promote(notifier.build(current?.phase ?: expected))
        if (intent != null && (intent.action != ServicePomodoroRinging.START || expected == null)) {
            if (current == null) finishRinging(startId)
            done?.countDown()
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                val valid = app.pomodoroSessions.withRingingPhase(expected) { ring ->
                    withContext(Dispatchers.Main.immediate) { controller.start(ring) }
                }
                // An old start must neither play nor stop a newer valid ring.
                if (!valid && current == null) finishRinging(startId)
            } catch (failure: android.database.sqlite.SQLiteException) {
                if (current == null) finishRinging(startId)
            } catch (failure: IOException) {
                if (current == null) finishRinging(startId)
            } finally {
                done?.countDown()
            }
        }
        return START_STICKY
    }

    internal fun promote(notification: Notification) {
        startForeground(PomodoroSessionNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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

    internal fun repost(phase: PomodoroPhaseKey) = controller.dismissed(phase)

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
