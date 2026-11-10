package com.github.e0ipso.tokkl.pomodoro

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import com.github.e0ipso.tokkl.feature.pomodoro.R
import java.io.IOException

/** Alarm usage routes playback to the alarm stream, including silent mode and alarms-allowed DND.
 * https://developer.android.com/reference/android/media/AudioAttributes#USAGE_ALARM
 */
internal fun pomodoroAlarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

internal val POMODORO_VIBRATION_PATTERN: List<Long> = listOf(0L, 700L, 300L, 700L, 1_300L)
internal const val POMODORO_VIBRATION_REPEAT = 0

internal fun PomodoroSound.rawResource(): Int = when (this) {
    PomodoroSound.WORK_TO_SHORT_REST -> R.raw.pomodoro_work_to_short_rest
    PomodoroSound.WORK_TO_LONG_REST -> R.raw.pomodoro_work_to_long_rest
    PomodoroSound.SHORT_REST_TO_WORK -> R.raw.pomodoro_short_rest_to_work
    PomodoroSound.LONG_REST_TO_WORK -> R.raw.pomodoro_long_rest_to_work
}

/** Main-thread-only framework output. A foreground service owns its entire lifetime. */
internal class AndroidPomodoroRingOutput(
    private val service: PomodoroRingingService,
    private val notifier: PomodoroRingingNotifier,
) : PomodoroRingOutput {
    private val audio = service.getSystemService(AudioManager::class.java)
    private val vibrator = service.getSystemService(VibratorManager::class.java).defaultVibrator
    private val audioAttributes: AudioAttributes = pomodoroAlarmAudioAttributes()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { /* The phase remains awaiting Next/End. Android owns routing. */ }
        .build()
    private var player: MediaPlayer? = null

    override fun show(ring: PomodoroRing) {
        service.promote(notifier.build(ring))
    }

    override fun play(sound: PomodoroSound) {
        // Called after startForeground: API 35+ requires a top app or foreground service for focus.
        audio.requestAudioFocus(focus)
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(audioAttributes)
            next.setWakeMode(service, PowerManager.PARTIAL_WAKE_LOCK)
            service.resources.openRawResourceFd(sound.rawResource()).use { asset ->
                next.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            next.isLooping = true
            next.setOnPreparedListener { prepared -> if (player === prepared) prepared.start() }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    player = null
                    failed.release()
                }
                true
            }
            next.prepareAsync()
        } catch (_: IOException) {
            player = null
            next.release()
        } catch (_: IllegalStateException) {
            player = null
            next.release()
        }
    }

    override fun vibrate() {
        val effect = VibrationEffect.createWaveform(POMODORO_VIBRATION_PATTERN.toLongArray(), POMODORO_VIBRATION_REPEAT)
        // API 31/32 accepts AudioAttributes; API 33+ has the equivalent VibrationAttributes.
        // Alarm usage is required for an attentional vibration from the background.
        // https://developer.android.com/reference/android/os/Vibrator#vibrate(android.os.VibrationEffect,%20android.media.AudioAttributes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build()
            vibrator.vibrate(effect, attributes)
        } else {
            // The only overload with alarm usage before API 33, deprecated there for the one above.
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
        }
    }

    override fun stopAudio() {
        val previous = player
        player = null
        try {
            previous?.release()
        } finally {
            audio.abandonAudioFocusRequest(focus)
        }
    }

    override fun stopVibration() {
        vibrator.cancel()
    }

    override fun removeNotification() {
        service.stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
    }
}
