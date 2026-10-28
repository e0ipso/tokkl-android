package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The ringing service at a held phase's planned end, on API 31..36; no audible emulator output is
 * assumed. Inspect the actual MediaPlayer, applied attributes and vibrator request. Each test
 * holds a server phase already past its end, and the phone runs offline, so the alarm rings from
 * the held state alone. Full-screen alarm Activity acceptance is covered separately in
 * PomodoroAlarmActivityTest. Instrumentation shares the app process, so this cannot model
 * force-stop/process death.
 */
class PomodoroRingingServiceTest {
    @get:Rule val session = SessionRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var zen = "0"
    private var ringer = AudioManager.RINGER_MODE_NORMAL
    private var alarmVolume = 0
    private var airplane = false
    private var screenWasOn = true

    @Before
    fun learnTheTimerApiThenGoOfflineAndRememberDeviceState() {
        zen = DeviceShell.run("settings get global zen_mode").trim()
        ringer = audio.ringerMode
        alarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        airplane = DeviceShell.run("settings get global airplane_mode_on").trim() == "1"
        screenWasOn = device.isScreenOn
        HeldPhases.learnTheTimerApi()
        // Reuse the suite's permission helper. Revoking this permission kills instrumentation,
        // so, like the other notification tests, leave this grant to the runner's setup.
        RunningNotifications.allow()
        DeviceShell.run("cmd notification set_dnd off")
        DeviceShell.run("cmd media_session volume --stream 4 --set ${maxOf(1, alarmVolume)}")
        DeviceShell.run("cmd connectivity airplane-mode enable")
        assertEquals("1", DeviceShell.run("settings get global airplane_mode_on").trim())
    }

    @After
    fun removeFixturesAndRestoreDeviceState() {
        try {
            PomodoroRingingActionReceiver.completed = null
            PomodoroRingingService.processed = null
            HeldPhases.holdNoSession()
            awaitStopped()
            awaitSessionNotifications(0)
            assertFalse(app.pomodoroRinging.state.value.vibrationRequested)
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= 36) setRinger(ringer)
                DeviceShell.run("cmd media_session volume --stream 4 --set $alarmVolume")
                val mode = when (zen) { "1" -> "priority"; "2" -> "none"; "3" -> "alarms"; else -> "off" }
                DeviceShell.run("cmd notification set_dnd $mode")
                // Wait for the network to come back, or the next test's first request fails.
                AirplaneMode.set(airplane, TokklTestServer.url)
                if (screenWasOn) device.wakeUp() else device.sleep()
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun phaseAlarmStartsForegroundPlaybackWithScreenOffAndLeavesThePhaseAwaitingAction() {
        device.pressHome()
        device.sleep()
        assertFalse(device.isScreenOn)
        val original = seedDuePhase()
        val state = awaitPlaying()
        assertAlarmOutput(state)
        assertEquals(HeldPhases.key(original), state.ring?.phase)
        // The phase stays where it was until Next phase or End session.
        assertEquals(original, app.timerState.state.value)
        onMain {
            val service = checkNotNull(app.pomodoroRinging.service)
            assertTrue(service.output.playing)
            assertTrue(service.output.looping)
            assertTrue(service.output.vibrationRequested)
        }
        val notification = ringingNotification()
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        // The app sets no timeout. Android 16 gives every posted notification without one a
        // three-day lifetime, so check the notification as the app builds it.
        assertEquals(0L, PomodoroRingingNotifier(context).build(original.ring()).timeoutAfter)
        // The phase alarm now requests the lock-screen Activity. A denied system grant may
        // suppress launch, but never removes sound, vibration or the notification actions.
        assertNotNull(PomodoroRingingNotifier(context).build(original.ring()).fullScreenIntent)
        if (canShowPomodoroFullScreen(context)) assertNotNull(notification.fullScreenIntent)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notifications.getNotificationChannel(PomodoroRingingNotifier.CHANNEL_ID).importance)
        assertNull(notifications.getNotificationChannel(PomodoroRingingNotifier.CHANNEL_ID).sound)
    }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun silentModeKeepsTheAlarmPlayerLoopingAndVibrationRequested() {
        // cmd audio set-ringer-mode is a modern shell API. API 31 silent-mode compatibility
        // remains a separate device-gate criterion; do not turn an unsupported shell into a pass.
        setRinger(AudioManager.RINGER_MODE_SILENT)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        seedDuePhase()
        assertAlarmOutput(awaitPlaying())
        onMain { assertTrue(checkNotNull(app.pomodoroRinging.service).output.playing) }
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun alarmsAllowedDndKeepsTheAlarmPlayerLoopingAndVibrationRequested() {
        DeviceShell.run("cmd notification set_dnd alarms")
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, notifications.currentInterruptionFilter)
        seedDuePhase()
        assertAlarmOutput(awaitPlaying())
        onMain { assertTrue(checkNotNull(app.pomodoroRinging.service).output.playing) }
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, notifications.currentInterruptionFilter)
    }

    @Test
    fun everyTransitionUsesItsBundledWavWithAlarmAttributesAndRepeatingVibration() {
        val cases = listOf(
            PhaseTransition.WORK_TO_SHORT_REST to PomodoroSound.WORK_TO_SHORT_REST,
            PhaseTransition.WORK_TO_LONG_REST to PomodoroSound.WORK_TO_LONG_REST,
            PhaseTransition.SHORT_REST_TO_WORK to PomodoroSound.SHORT_REST_TO_WORK,
            PhaseTransition.LONG_REST_TO_WORK to PomodoroSound.LONG_REST_TO_WORK,
        )
        assertEquals(
            listOf(R.raw.pomodoro_work_to_short_rest, R.raw.pomodoro_work_to_long_rest, R.raw.pomodoro_short_rest_to_work, R.raw.pomodoro_long_rest_to_work),
            cases.map { it.second.rawResource() },
        )
        for ((transition, sound) in cases) {
            val original = seedDuePhase(transition)
            val state = awaitPlaying()
            assertEquals(sound, state.ring?.sound)
            assertAlarmOutput(state)
            assertEquals(original, app.timerState.state.value)
            HeldPhases.holdNoSession()
            awaitStopped()
        }
    }

    @Test
    fun offlineButtonsKeepTheRingAndAConfirmElsewhereStopsBothOutputs() {
        val held = seedDuePhase()
        val original = awaitPlaying()
        val notification = ringingNotification()
        assertEquals(listOf("Next phase", "End session"), notification.actions.map { it.title.toString() })
        assertEquals(2, notification.actions.size)
        awaitActions(2) {
            notification.actions[0].actionIntent.send()
            notification.actions[1].actionIntent.send()
        }
        // Neither reached the server, so the phase and its ring stay as they were.
        assertEquals(held, app.timerState.state.value)
        val after = app.pomodoroRinging.state.value
        assertAlarmOutput(after)
        assertEquals(original.audioSessionId, after.audioSessionId)
        assertEquals("1", DeviceShell.run("settings get global airplane_mode_on").trim())

        // Another device confirms Next phase, and its state reaches the phone.
        HeldPhases.hold(PhaseTransition.SHORT_REST_TO_WORK, overtime = Duration.ofSeconds(-60))
        awaitStopped()
    }

    @Test
    fun swipedNotificationRepostsWithoutRestartingOrStoppingTheSound() {
        val phase = HeldPhases.key(seedDuePhase())
        val original = awaitPlaying()
        val deleted = ringingNotification().deleteIntent
        // Invoke the framework's delete PendingIntent. API 31 may refuse the physical swipe of
        // an ongoing notification; the same callback covers the dismissible API 34+ behavior.
        awaitActions(1) { deleted.send() }
        val after = app.pomodoroRinging.state.value
        assertEquals(original.audioSessionId, after.audioSessionId)
        assertEquals(phase, after.ring?.phase)
        assertAlarmOutput(after)
        assertEquals(1, notifications.activeNotifications.count { it.id == PomodoroSessionNotification.ID })
        onMain { assertTrue(checkNotNull(app.pomodoroRinging.service).output.playing) }
    }

    @Test
    fun duplicateDeliveryAndStaleServiceIntentsCannotReplaceTheCurrentRing() {
        val old = HeldPhases.key(seedDuePhase())
        val original = awaitPlaying()
        runBlocking { app.pomodoroAlarm.onAlarm(old) }
        assertEquals(original.audioSessionId, app.pomodoroRinging.state.value.audioSessionId)
        val stale = PomodoroPhaseKey(old.start.minusSeconds(1))
        val processed = CountDownLatch(1)
        PomodoroRingingService.processed = processed
        try {
            context.startForegroundService(Intent(context, PomodoroRingingService::class.java).setAction(ServicePomodoroRinging.START).putPhase(stale))
            assertTrue("stale start was not processed", processed.await(10, TimeUnit.SECONDS))
        } finally {
            PomodoroRingingService.processed = null
        }
        onMain {
            val current = checkNotNull(app.pomodoroRinging.service)
            assertEquals(old, current.current?.phase)
            assertEquals(original.audioSessionId, current.output.audioSessionId)
            assertTrue(current.output.playing)
        }
        assertEquals(old, runBlocking { app.pomodoroAlarm.current() }?.phase)
    }

    @Test
    fun staleButtonsCannotAdvanceEndOrRepostThePhaseThatReplacedThem() {
        seedDuePhase()
        awaitPlaying()
        val old = ringingNotification()
        // Another device confirmed Next phase: the rest has five minutes left.
        val current = HeldPhases.hold(PhaseTransition.SHORT_REST_TO_WORK, overtime = Duration.ofSeconds(-300))
        awaitStopped()
        // Send the old immutable PendingIntents after the phase has advanced.
        awaitActions(3) {
            old.actions[0].actionIntent.send()
            old.actions[1].actionIntent.send()
            old.deleteIntent.send()
        }
        assertEquals(current, app.timerState.state.value)
        assertFalse(app.pomodoroRinging.state.value.serviceRunning)
        assertFalse(app.pomodoroRinging.state.value.playing)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun physicalSwipeRepostsTheNotificationAndKeepsTheSamePlayer() {
        device.wakeUp()
        seedDuePhase()
        val original = awaitPlaying()
        assertTrue(device.openNotification())
        val title = checkNotNull(device.wait(Until.findObject(By.text("Pomodoro phase ended")), 10_000))
        val bounds = title.visibleBounds
        awaitActions(1) {
            assertTrue(device.swipe(device.displayWidth / 4, bounds.centerY(), device.displayWidth - 1, bounds.centerY(), 20))
        }
        // The receiver hands the repost to the ringing service, which posts it after the receiver ends.
        awaitSessionNotifications(1)
        onMain {
            val service = checkNotNull(app.pomodoroRinging.service)
            assertTrue(service.output.playing)
            assertTrue(service.output.vibrationRequested)
            assertEquals(original.audioSessionId, service.output.audioSessionId)
        }
        device.pressHome()
    }

    private fun awaitActions(count: Int, send: () -> Unit) {
        val done = CountDownLatch(count)
        PomodoroRingingActionReceiver.completed = done
        try {
            send()
            assertTrue("notification action did not finish", done.await(10, TimeUnit.SECONDS))
        } finally {
            PomodoroRingingActionReceiver.completed = null
        }
    }

    /**
     * Holds a [transition] phase whose planned end passed 65 s ago. The held state arms its alarm
     * in the past, and real AlarmManager delivery, which may start a foreground service from the
     * background, rings it.
     */
    private fun seedDuePhase(transition: PhaseTransition = PhaseTransition.WORK_TO_SHORT_REST): HeldTimerState {
        HeldPhases.holdNoSession()
        awaitStopped()
        return HeldPhases.hold(transition)
    }

    private fun awaitPlaying(): PomodoroRingingState = HeldPhases.awaitPlaying()

    private fun awaitStopped() = HeldPhases.awaitStopped()

    private fun awaitSessionNotifications(count: Int) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (notifications.activeNotifications.count { it.id == PomodoroSessionNotification.ID } != count) {
            if (SystemClock.uptimeMillis() > deadline) {
                assertEquals(count, notifications.activeNotifications.count { it.id == PomodoroSessionNotification.ID })
            }
            Thread.sleep(50)
        }
    }

    private fun ringingNotification(): Notification = notifications.activeNotifications.single { it.id == PomodoroSessionNotification.ID }.notification

    private fun assertAlarmOutput(state: PomodoroRingingState) {
        assertTrue(state.serviceRunning)
        assertTrue(state.playing)
        assertTrue(state.looping)
        assertEquals(AudioAttributes.USAGE_ALARM, state.audioUsage)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, state.foregroundType)
        assertTrue(state.vibrationRequested)
        assertEquals(VibrationAttributes.USAGE_ALARM, state.vibrationUsage)
        assertEquals(listOf(0L, 700L, 300L, 700L, 1_300L), state.vibrationPattern)
        assertEquals(0, state.vibrationRepeat)
        assertTrue(checkNotNull(state.audioSessionId) > 0)
    }

    private fun setRinger(mode: Int) {
        val name = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "NORMAL"
        }
        DeviceShell.run("cmd audio set-ringer-mode $name")
    }

    private fun onMain(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync { try { block() } catch (caught: Throwable) { failure.set(caught) } }
        failure.get()?.let { throw it }
    }
}
