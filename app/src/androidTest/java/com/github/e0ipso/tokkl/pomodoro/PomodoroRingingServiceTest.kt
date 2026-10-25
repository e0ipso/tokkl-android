package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.notification.RunningNotifications
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Device acceptance source for API 31..36; no audible emulator output is assumed. Inspect the
 * actual MediaPlayer, applied attributes and vibrator request. Full-screen alarm Activity
 * acceptance is covered separately in PomodoroAlarmActivityTest.
 * Instrumentation shares the app process, so this cannot model force-stop/process death.
 */
@SignedOut
class PomodoroRingingServiceTest {
    @get:Rule val session = SessionRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private lateinit var database: CacheDatabase
    private var zen = "0"
    private var ringer = AudioManager.RINGER_MODE_NORMAL
    private var alarmVolume = 0
    private var airplane = false
    private var screenWasOn = true

    @Before
    fun prepareDisposableLocalSessionAndRememberDeviceState() {
        zen = DeviceShell.run("settings get global zen_mode").trim()
        ringer = audio.ringerMode
        alarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        airplane = DeviceShell.run("settings get global airplane_mode_on").trim() == "1"
        screenWasOn = device.isScreenOn
        database = CacheDatabase.open(context)
        runBlocking { app.pomodoroSessions.discardRunningSession() }
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
            runBlocking {
                app.pomodoroSessions.discardRunningSession()
                withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning } }
                withContext(Dispatchers.IO) { app.cache.clear() }
                assertNull(app.pomodoroSessions.current())
                assertNull(app.pomodoroSessions.reachedPhaseEnd())
                withContext(Dispatchers.IO) { assertNull(database.changes().first()) }
            }
            assertFalse(notifications.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
            assertFalse(app.pomodoroRinging.state.value.vibrationRequested)
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= 36) setRinger(ringer)
                DeviceShell.run("cmd media_session volume --stream 4 --set $alarmVolume")
                val mode = when (zen) { "1" -> "priority"; "2" -> "none"; "3" -> "alarms"; else -> "off" }
                DeviceShell.run("cmd notification set_dnd $mode")
                DeviceShell.run("cmd connectivity airplane-mode ${if (airplane) "enable" else "disable"}")
                if (screenWasOn) device.wakeUp() else device.sleep()
            } finally {
                if (::database.isInitialized) database.close()
            }
        }
    }

    @Test
    fun phaseAlarmStartsForegroundPlaybackWithScreenOffAndLeavesThePhaseAwaitingAction() {
        device.pressHome()
        device.sleep()
        assertFalse(device.isScreenOn)
        val original = seedDuePhase()
        deliverExactAlarm()
        val state = awaitPlaying()
        assertAlarmOutput(state)
        assertEquals(original.phaseKey(), state.ring?.phase)
        assertEquals(original, runBlocking { app.pomodoroSessions.current() })
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
        assertEquals(0L, PomodoroRingingNotifier(context).build(original.phaseKey()).timeoutAfter)
        // The phase alarm now requests the lock-screen Activity. A denied system grant may
        // suppress launch, but never removes sound, vibration or the notification actions.
        assertNotNull(PomodoroRingingNotifier(context).build(original.phaseKey()).fullScreenIntent)
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
        deliverExactAlarm()
        assertAlarmOutput(awaitPlaying())
        onMain { assertTrue(checkNotNull(app.pomodoroRinging.service).output.playing) }
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun alarmsAllowedDndKeepsTheAlarmPlayerLoopingAndVibrationRequested() {
        DeviceShell.run("cmd notification set_dnd alarms")
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, notifications.currentInterruptionFilter)
        seedDuePhase()
        deliverExactAlarm()
        assertAlarmOutput(awaitPlaying())
        onMain { assertTrue(checkNotNull(app.pomodoroRinging.service).output.playing) }
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, notifications.currentInterruptionFilter)
    }

    @Test
    fun everyTransitionUsesItsBundledWavWithAlarmAttributesAndRepeatingVibration() {
        val cases = listOf(
            Triple("work", 0.0, PomodoroSound.WORK_TO_SHORT_REST),
            Triple("work", 3.0, PomodoroSound.WORK_TO_LONG_REST),
            Triple("shortRest", 1.0, PomodoroSound.SHORT_REST_TO_WORK),
            Triple("longRest", 4.0, PomodoroSound.LONG_REST_TO_WORK),
        )
        assertEquals(
            listOf(R.raw.pomodoro_work_to_short_rest, R.raw.pomodoro_work_to_long_rest, R.raw.pomodoro_short_rest_to_work, R.raw.pomodoro_long_rest_to_work),
            cases.map { it.third.rawResource() },
        )
        for ((kind, completed, sound) in cases) {
            val original = seedDuePhase(kind, completed)
            deliverExactAlarm()
            val state = awaitPlaying()
            assertEquals(sound, state.ring?.sound)
            assertAlarmOutput(state)
            assertEquals(original, runBlocking { app.pomodoroSessions.current() })
            runBlocking { app.pomodoroSessions.discardRunningSession() }
            awaitStopped()
        }
    }

    @Test
    fun notificationHasOnlyNextAndEndAndBothStopAudioAndVibrationOffline() {
        val first = seedDuePhase()
        deliverExactAlarm()
        awaitPlaying()
        val notification = ringingNotification()
        assertEquals(listOf("Next phase", "End session"), notification.actions.map { it.title.toString() })
        assertEquals(2, notification.actions.size)
        notification.actions[0].actionIntent.send()
        awaitStopped()
        val rest = checkNotNull(runBlocking { app.pomodoroSessions.current() })
        assertEquals("shortRest", rest.phaseKind)
        assertNotEquals(first.phaseKey(), rest.phaseKey())
        assertNull(runBlocking { app.pomodoroSessions.reachedPhaseEnd() })

        // A fresh due rest lets the End action exercise the same local/offline queue path.
        seedDuePhase("shortRest", 1.0)
        deliverExactAlarm()
        awaitPlaying()
        ringingNotification().actions[1].actionIntent.send()
        awaitStopped()
        assertNull(runBlocking { app.pomodoroSessions.current() })
        assertTrue(runBlocking { withContext(Dispatchers.IO) { database.changes().first() != null } })
        assertEquals("1", DeviceShell.run("settings get global airplane_mode_on").trim())
    }

    @Test
    fun swipedNotificationRepostsWithoutRestartingOrStoppingTheSound() {
        val phase = seedDuePhase().phaseKey()
        deliverExactAlarm()
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
        val old = seedDuePhase().phaseKey()
        deliverExactAlarm()
        val original = awaitPlaying()
        runBlocking { app.pomodoroSessions.onPhaseAlarm(old) }
        assertEquals(original.audioSessionId, app.pomodoroRinging.state.value.audioSessionId)
        val stale = old.copy(workspaceId = old.workspaceId + 1)
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
        assertEquals(old, runBlocking { app.pomodoroSessions.current() }?.phaseKey())
    }

    @Test
    fun staleButtonsCannotAdvanceOrEndThePhaseThatReplacedThem() {
        seedDuePhase()
        deliverExactAlarm()
        awaitPlaying()
        val old = ringingNotification()
        old.actions[0].actionIntent.send()
        awaitStopped()
        val current = checkNotNull(runBlocking { app.pomodoroSessions.current() })
        // Send the old immutable PendingIntents after the phase has advanced.
        awaitActions(3) {
            old.actions[0].actionIntent.send()
            old.actions[1].actionIntent.send()
            old.deleteIntent.send()
        }
        assertEquals(current, runBlocking { app.pomodoroSessions.current() })
        assertFalse(app.pomodoroRinging.state.value.playing)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun physicalSwipeRepostsTheNotificationAndKeepsTheSamePlayer() {
        device.wakeUp()
        seedDuePhase()
        deliverExactAlarm()
        val original = awaitPlaying()
        assertTrue(device.openNotification())
        val title = checkNotNull(device.wait(Until.findObject(By.text("Pomodoro phase ended")), 10_000))
        val bounds = title.visibleBounds
        awaitActions(1) {
            assertTrue(device.swipe(device.displayWidth / 4, bounds.centerY(), device.displayWidth - 1, bounds.centerY(), 20))
        }
        assertEquals(1, notifications.activeNotifications.count { it.id == PomodoroSessionNotification.ID })
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

    private fun seedDuePhase(kind: String = "work", completed: Double = 0.0): PomodoroSession = runBlocking {
        app.pomodoroSessions.discardRunningSession()
        awaitStopped()
        val start = Instant.ofEpochMilli(Instant.now().minusSeconds(120).toEpochMilli())
        val value = PomodoroSession(
            workspaceId = 1, workMinutes = 1.0, shortRestMinutes = 1.0, longRestMinutes = 1.0, longRestEvery = 4.0,
            phaseKind = kind, phaseStartedAt = start, pomodoroStartedAt = start, completed = completed,
            fields = EntryFields(description = "Disposable offline alarm"),
        )
        withContext(Dispatchers.IO) { database.pomodoroSessions().upsert(value) }
        value
    }

    /** Real AlarmManager delivery supplies the background foreground-service start exemption. */
    private fun deliverExactAlarm() = runBlocking { app.pomodoroSessions.rearm(deferRingingToAlarm = true) }

    private fun awaitPlaying(): PomodoroRingingState = runBlocking {
        withTimeout(10_000) { app.pomodoroRinging.state.first { it.serviceRunning && it.playing && it.vibrationRequested } }
    }

    private fun awaitStopped() = runBlocking {
        withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning && !it.playing && !it.vibrationRequested } }
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
