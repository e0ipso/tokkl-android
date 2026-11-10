package com.github.e0ipso.tokkl.pomodoro

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.components.pomodoroRingingPhase
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The Pomodoro ring as the platform shows it.
 *
 * @property serviceRunning whether the ringing service runs, as the activity manager lists it.
 * @property foregroundType the running service's foreground service types, or null when it is not
 *   in the foreground.
 * @property player the app's started player with alarm usage in the audio service, or null.
 * @property vibration the vibration the vibrator service plays for the app now, or null.
 * @property phase the phase the ringing service says it rings, as the widget reads it.
 */
internal data class ObservedRing(
    val serviceRunning: Boolean,
    val foregroundType: Int?,
    val player: AlarmPlayer?,
    val vibration: AlarmVibration?,
    val phase: PomodoroPhaseKey?,
) {
    val playing: Boolean get() = player != null

    val vibrating: Boolean get() = vibration != null
}

/** A started player: [id] is the audio service's player id, new for each player the app makes. */
internal data class AlarmPlayer(val id: Int)

/**
 * A vibration: whether its usage is the alarm's, its step [timings] in milliseconds, and its
 * repeat index, as parsed from [dump], the vibrator service's section about it.
 */
internal data class AlarmVibration(val alarmUsage: Boolean, val timings: List<Long>, val repeat: Int?, val dump: String)

/**
 * Reads the Pomodoro ring from the platform, never from the app's internals: the activity
 * manager's running services, and the dumps of the audio and vibrator services, which the shell
 * user reads ([DeviceShell]). The instrumentation runs in the app's process, so the app's uid is
 * this process's.
 */
internal object PomodoroRingObservation {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app: TokklApplication get() = instrumentation.targetContext.applicationContext as TokklApplication
    private val packageName: String get() = instrumentation.targetContext.packageName
    private val service = PomodoroRingingService::class.java.name

    fun now(): ObservedRing {
        val running = serviceRunning()
        return ObservedRing(
            serviceRunning = running,
            foregroundType = if (running) foregroundType() else null,
            player = player(),
            vibration = vibration(),
            phase = runBlocking { app.pomodoroRingingPhase.ringingPhase.first() },
        )
    }

    /** Waits until the ringing service runs, plays and vibrates. */
    fun awaitPlaying(timeoutMillis: Long = TIMEOUT_MS): ObservedRing =
        await("the ring plays and vibrates", timeoutMillis) { it.serviceRunning && it.playing && it.vibrating }

    /** Waits until the ringing service has stopped both outputs and ended. */
    fun awaitStopped(timeoutMillis: Long = TIMEOUT_MS): ObservedRing =
        await("the ring stopped", timeoutMillis) { !it.serviceRunning && !it.playing && !it.vibrating }

    /**
     * Whether [player] loops: it is still the app's started alarm player once the longest Pomodoro
     * sound has had time to end. MediaPlayer tells the audio service it stopped when a sound that
     * does not loop ends.
     */
    fun loops(player: AlarmPlayer): Boolean {
        SystemClock.sleep(LONGEST_SOUND_MS + POLL_MS)
        return player() == player
    }

    fun await(what: String, timeoutMillis: Long = TIMEOUT_MS, condition: (ObservedRing) -> Boolean): ObservedRing {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (true) {
            val ring = now()
            if (condition(ring)) return ring
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("timed out until $what; last saw $ring")
            SystemClock.sleep(POLL_MS)
        }
    }

    @Suppress("DEPRECATION") // Still returns the caller's own services.
    private fun serviceRunning(): Boolean = instrumentation.targetContext.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.packageName == packageName && it.service.className == service }

    /** Android 12 omits the type from dumpsys; read the live service's platform getter instead. */
    @SuppressLint("PrivateApi") // Test-only lookup of the running service; no app observation hooks.
    private fun foregroundType(): Int? {
        var type: Int? = null
        instrumentation.runOnMainSync {
            val activityThread = Class.forName("android.app.ActivityThread")
            val thread = activityThread.getDeclaredMethod("currentActivityThread").invoke(null)
            val services = activityThread.getDeclaredField("mServices").apply { isAccessible = true }
                .get(thread) as Map<*, *>
            type = services.values.filterIsInstance<PomodoroRingingService>().singleOrNull()
                ?.foregroundServiceType?.takeUnless { it == 0 }
        }
        return type
    }

    /**
     * The app's started player with alarm usage, from the players the audio service lists, such as
     * `AudioPlaybackConfiguration piid:63 ... u/pid:10123/4567 state:started attr:AudioAttributes:
     * usage=USAGE_ALARM ...`.
     */
    private fun player(): AlarmPlayer? {
        val uid = Process.myUid()
        val line = DeviceShell.run("dumpsys audio").lineSequence()
            .filter { "AudioPlaybackConfiguration piid:" in it && "u/pid:$uid/" in it }
            .firstOrNull { "state:started" in it && "usage=USAGE_ALARM " in it }
            ?: return null
        val id = Regex("piid:(\\d+)").find(line)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("no player id in: $line")
        return AlarmPlayer(id)
    }

    /**
     * The vibration the vibrator service plays for the app now: the section after
     * `CurrentVibration`, up to the next vibration's, when it names the app.
     */
    private fun vibration(): AlarmVibration? {
        val dump = DeviceShell.run("dumpsys vibrator_manager")
        val start = dump.indexOf("CurrentVibration").takeIf { it >= 0 } ?: return null
        val end = listOf("NextVibration", "Recent vibrations", "Vibrators:", "mCurrentExternalVibration")
            .map { dump.indexOf(it, start + 1) }
            .filter { it > start }
            .minOrNull() ?: dump.length
        val current = dump.substring(start, end)
        if (packageName !in current && "uid=${Process.myUid()}" !in current) return null
        val effect = current.substringAfter("segments=[", "").substringBefore("]")
        return AlarmVibration(
            alarmUsage = Regex("(?:mUsage|[Uu]sage)[=:]\\s*(?:USAGE_)?ALARM\\b").containsMatchIn(current),
            timings = Regex("duration=(\\d+)").findAll(effect).map { it.groupValues[1].toLong() }.toList(),
            repeat = Regex("(?:repeatIndex|repeat)=(-?\\d+)").find(current)?.groupValues?.get(1)?.toInt(),
            dump = current,
        )
    }

    /** The longest bundled sound, pomodoro_work_to_long_rest.wav, lasts 3.0 s. */
    private const val LONGEST_SOUND_MS = 3_000L
    private const val TIMEOUT_MS = 10_000L
    private const val POLL_MS = 200L
}
