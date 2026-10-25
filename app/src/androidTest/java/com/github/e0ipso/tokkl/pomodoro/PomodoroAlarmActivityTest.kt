package com.github.e0ipso.tokkl.pomodoro

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.notification.RunningNotifications
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real notification/Activity/player acceptance source for API 31 and 36; not executed here. */
@SignedOut
class PomodoroAlarmActivityTest {
    private companion object {
        const val LOCK_PIN = "1111"
    }

    @get:Rule val session = SessionRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var database: CacheDatabase
    private var originalFullScreenMode: String? = null
    private var screenWasOn = true
    private var restoreLockscreenDisabled = false
    private var restorePin = false

    @Before fun prepareDisposableSessionAndGrantFullScreenAccess() {
        screenWasOn = device.isScreenOn
        database = CacheDatabase.open(context)
        runBlocking { app.pomodoroSessions.discardRunningSession() }
        RunningNotifications.allow()
        if (Build.VERSION.SDK_INT >= 34) {
            val output = DeviceShell.run("cmd appops get ${context.packageName} USE_FULL_SCREEN_INTENT").trim()
            originalFullScreenMode = if (output.startsWith("No operations")) "default" else {
                Regex("USE_FULL_SCREEN_INTENT: (allow|deny|ignore|default|foreground)").find(output)?.groupValues?.get(1)
                    ?: error("Cannot restore full-screen app-op: $output")
            }
            setFullScreenMode("allow")
            assertTrue(notifications.canUseFullScreenIntent())
        }
    }

    @After fun removeFixturesAndRestorePermissionAndScreen() {
        try {
            onMain {
                for (stage in listOf(Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.STOPPED)) {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                        .filterIsInstance<PomodoroAlarmActivity>().toList().forEach(Activity::finish)
                }
            }
            runBlocking {
                app.pomodoroSessions.discardRunningSession()
                withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning } }
                withContext(Dispatchers.IO) { app.cache.clear() }
                assertNull(app.pomodoroSessions.current())
                assertNull(app.pomodoroSessions.reachedPhaseEnd())
                withContext(Dispatchers.IO) { assertNull(database.changes().first()) }
            }
            assertFalse(notifications.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
            assertFalse(app.pomodoroRinging.state.value.playing)
            assertFalse(app.pomodoroRinging.state.value.vibrationRequested)
        } finally {
            try {
                originalFullScreenMode?.let(::setFullScreenMode)
                if (restorePin) {
                    if (!device.isScreenOn) device.wakeUp()
                    DeviceShell.run("locksettings clear --old $LOCK_PIN")
                    restorePin = false
                }
                if (restoreLockscreenDisabled) {
                    DeviceShell.run("locksettings set-disabled true")
                    restoreLockscreenDisabled = false
                }
                // Clearing the pin leaves the lock screen showing, and every later test would
                // launch its activity behind it.
                if (screenWasOn) {
                    device.wakeUp()
                    dismissKeyguard()
                } else {
                    device.sleep()
                }
            } finally {
                if (::database.isInitialized) database.close()
            }
        }
    }

    @Test fun lockedScreenPhaseEndLaunchesAlarmTurnsScreenOnAndKeepsItAwake() {
        // The API 31 emulator boots with the lock screen disabled and no credential, so sleep
        // never locks it. A pin makes the lock screen secure, which sleep then locks.
        if (DeviceShell.run("settings get secure lockscreen.disabled").trim() == "1") {
            restoreLockscreenDisabled = true
            DeviceShell.run("locksettings set-disabled false")
        }
        DeviceShell.run("locksettings set-pin $LOCK_PIN")
        restorePin = true
        device.pressHome()
        device.sleep()
        device.wakeUp()
        device.sleep()
        assertFalse(device.isScreenOn)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var locked = false
        while (SystemClock.elapsedRealtime() < deadline) {
            locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (locked) break
            SystemClock.sleep(200)
        }
        assertTrue(locked)
        val due = seedDuePhase()
        deliverAlarm()
        assertTrue(device.wait(Until.hasObject(By.text("Work done: start short rest")), 10_000))
        assertTrue(device.isScreenOn)
        val activity = resumedAlarm()
        onMain {
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            assertTrue(activity.window.decorView.isShown)
        }
        // The Activity is resumed and visible while keyguard remains locked. Manifest tests
        // separately pin showWhenLocked/turnScreenOn; no hidden ActivityInfo flags are used.
        assertTrue(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        assertEquals(due.phaseKey(), awaitPlaying().ring?.phase)
        val notification = ringingNotification()
        assertNotNull(notification.fullScreenIntent)
        assertEquals(PomodoroRingingNotifier.CHANNEL_ID, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notifications.getNotificationChannel(notification.channelId).importance)
        assertEquals(listOf("Next phase", "End session"), notification.actions.map { it.title.toString() })
        assertEquals(1, notifications.activeNotifications.count { it.id == 3 })
    }

    @Test fun backAndHomeLeaveTheSameAlarmSoundAndDurablePhaseRunning() {
        val due = seedDuePhase()
        deliverAlarm()
        val before = awaitPlaying()
        openAlarm(due.phaseKey())
        device.pressBack()
        assertKeepsRinging(before, due)
        openAlarm(due.phaseKey())
        device.pressHome()
        assertKeepsRinging(before, due)
        openAlarm(due.phaseKey())
        assertTrue(device.wait(Until.hasObject(By.text("Next phase")), 10_000))
    }

    @Test fun nextAndEndUseTheDurableGuardedActionsAndStopBothOutputs() {
        val due = seedDuePhase()
        deliverAlarm()
        awaitPlaying()
        openAlarm(due.phaseKey())
        click("Next phase")
        awaitStopped()
        val rest = checkNotNull(runBlocking { app.pomodoroSessions.current() })
        assertEquals("shortRest", rest.phaseKind)
        assertNotEquals(due.phaseKey(), rest.phaseKey())
        assertNull(runBlocking { app.pomodoroSessions.reachedPhaseEnd() })
        val dueRest = seedDuePhase("shortRest", 1.0)
        deliverAlarm()
        awaitPlaying()
        openAlarm(dueRest.phaseKey())
        click("End session")
        awaitStopped()
        assertNull(runBlocking { app.pomodoroSessions.current() })
        assertTrue(runBlocking { withContext(Dispatchers.IO) { database.changes().first() != null } })
    }

    @Test fun recreationDuplicateAndStaleIntentsCannotAdvanceOrReplaceTheAlarm() {
        val due = seedDuePhase()
        deliverAlarm()
        val before = awaitPlaying()
        openAlarm(due.phaseKey())
        val original = resumedAlarm()
        onMain { original.recreate() }
        assertTrue(device.wait(Until.hasObject(By.text("Work done: start short rest")), 10_000))
        openAlarm(due.phaseKey())
        // Different workspace and phase identities must leave the active alarm unchanged.
        context.startActivity(pomodoroAlarmIntent(context, due.phaseKey().copy(workspaceId = 99)))
        instrumentation.waitForIdleSync()
        assertTrue(device.wait(Until.hasObject(By.text("Work done: start short rest")), 10_000))
        assertKeepsRinging(before, due)
        click("Next phase")
        awaitStopped()
        val rest = runBlocking { app.pomodoroSessions.current() }
        context.startActivity(pomodoroAlarmIntent(context, due.phaseKey()))
        instrumentation.waitForIdleSync()
        assertTrue(device.wait(Until.gone(By.text("Work done: start short rest")), 10_000))
        assertEquals(rest, runBlocking { app.pomodoroSessions.current() })
        assertFalse(app.pomodoroRinging.state.value.playing)
    }

    @Test @SdkSuppress(minSdkVersion = 34)
    fun deniedAndGrantedFullScreenPermissionFollowSystemAccessAndUsePerAppSettings() {
        setFullScreenMode("deny")
        assertFalse(notifications.canUseFullScreenIntent())
        assertFalse(canShowPomodoroFullScreen(context))
        val settings = checkNotNull(pomodoroFullScreenSettings(context))
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, settings.action)
        assertEquals("package:${context.packageName}", settings.dataString)
        assertNotNull(settings.resolveActivity(context.packageManager))
        setFullScreenMode("allow")
        assertTrue(notifications.canUseFullScreenIntent())
        assertTrue(canShowPomodoroFullScreen(context))
    }

    @Test @SdkSuppress(maxSdkVersion = 33)
    fun api31Through33HaveNoSpecialAccessSettingsAction() {
        assertTrue(canShowPomodoroFullScreen(context))
        assertNull(pomodoroFullScreenSettings(context))
    }

    @Test @SdkSuppress(minSdkVersion = 34)
    fun deniedAccessKeepsRingingAndNotificationActionsWithoutOpeningTheLockScreenActivity() {
        setFullScreenMode("deny")
        device.pressHome()
        device.sleep()
        val due = seedDuePhase()
        deliverAlarm()
        val playing = awaitPlaying()
        instrumentation.waitForIdleSync()
        onMain {
            assertFalse(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is PomodoroAlarmActivity })
        }
        assertKeepsRinging(playing, due)
        assertNotNull(PomodoroRingingNotifier(context).build(due.phaseKey()).fullScreenIntent)
        assertEquals(listOf("Next phase", "End session"), ringingNotification().actions.map { it.title.toString() })
    }

    @Test fun alarmPendingIntentsAreImmutableAndKeepDistinctPhaseIdentities() {
        val due = seedDuePhase().phaseKey()
        val first = pomodoroAlarmPendingIntent(context, due)
        val duplicate = pomodoroAlarmPendingIntent(context, due)
        val other = pomodoroAlarmPendingIntent(context, due.copy(completed = 1.0))
        assertEquals(first, duplicate)
        assertNotEquals(first, other)
        assertTrue(first.isImmutable)
        val intent = pomodoroAlarmIntent(context, due)
        assertEquals(due, intent.phaseKey())
        assertEquals(PomodoroAlarmActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.flags)
        assertNull(Intent(context, PomodoroAlarmActivity::class.java).phaseKey())
        assertNull(intent.putExtra("kind", "invalid").phaseKey())
        // A provisional sticky-service notification cannot open an unvalidated phase.
        assertNull(PomodoroRingingNotifier(context).build(null).fullScreenIntent)
        first.cancel()
        other.cancel()
    }

    private fun seedDuePhase(kind: String = "work", completed: Double = 0.0): PomodoroSession = runBlocking {
        app.pomodoroSessions.discardRunningSession()
        awaitStopped()
        val at = Instant.ofEpochMilli(Instant.now().minusSeconds(120).toEpochMilli())
        val due = PomodoroSession(workspaceId = 1, workMinutes = 1.0, shortRestMinutes = 1.0, longRestMinutes = 1.0,
            longRestEvery = 4.0, phaseKind = kind, phaseStartedAt = at, pomodoroStartedAt = at,
            completed = completed, fields = EntryFields(description = "Disposable full-screen alarm"))
        withContext(Dispatchers.IO) { database.pomodoroSessions().upsert(due) }
        due
    }

    private fun deliverAlarm() = runBlocking { app.pomodoroSessions.rearm(deferRingingToAlarm = true) }
    private fun openAlarm(phase: PomodoroPhaseKey) {
        context.startActivity(pomodoroAlarmIntent(context, phase))
        assertTrue(device.wait(Until.hasObject(By.text("Next phase")), 10_000))
    }
    private fun click(text: String) {
        val button = checkNotNull(device.wait(Until.findObject(By.text(text)), 10_000))
        button.click()
    }
    private fun resumedAlarm(): PomodoroAlarmActivity {
        var activity: PomodoroAlarmActivity? = null
        onMain { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<PomodoroAlarmActivity>().single() }
        return checkNotNull(activity)
    }
    private fun assertKeepsRinging(before: PomodoroRingingState, due: PomodoroSession) {
        val after = app.pomodoroRinging.state.value
        assertTrue(after.playing)
        assertTrue(after.vibrationRequested)
        assertEquals(before.audioSessionId, after.audioSessionId)
        assertEquals(due.phaseKey(), after.ring?.phase)
        assertEquals(due, runBlocking { app.pomodoroSessions.current() })
    }
    private fun awaitPlaying(): PomodoroRingingState = runBlocking {
        withTimeout(10_000) { app.pomodoroRinging.state.first { it.playing && it.vibrationRequested } }
    }
    private fun awaitStopped() = runBlocking {
        withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning && !it.playing && !it.vibrationRequested } }
    }
    private fun dismissKeyguard() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (keyguard.isKeyguardLocked && SystemClock.elapsedRealtime() < deadline) {
            DeviceShell.run("wm dismiss-keyguard")
            SystemClock.sleep(200)
        }
        assertFalse("the lock screen is still showing", keyguard.isKeyguardLocked)
    }
    private fun ringingNotification() = notifications.activeNotifications.single { it.id == PomodoroSessionNotification.ID }.notification
    private fun setFullScreenMode(mode: String) { DeviceShell.run("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT $mode") }
    private fun onMain(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync { try { block() } catch (caught: Throwable) { failure.set(caught) } }
        failure.get()?.let { throw it }
    }
}
