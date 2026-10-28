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
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerState
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The full-screen alarm of a held phase on API 31 and 36, with the real notification, Activity and
 * player. Most tests hold a server phase already past its end; the Next phase and End session test
 * runs a real one-minute session on the test server.
 */
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
    private var originalFullScreenMode: String? = null
    private var screenWasOn = true
    private var restoreLockscreenDisabled = false
    private var restorePin = false

    @Before fun learnTheTimerApiAndGrantFullScreenAccess() {
        screenWasOn = device.isScreenOn
        HeldPhases.learnTheTimerApi()
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
            if (app.capabilities.state.value.timerState) HeldPhases.holdNoSession()
            awaitStopped()
            awaitNoSharedNotification()
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
                    // Android 12 keeps a lock screen that already shows until the screen goes off
                    // and on again; only then can it be dismissed.
                    device.sleep()
                    device.wakeUp()
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
                SessionRule.signOut()
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
        assertEquals(HeldPhases.key(due), awaitPlaying().ring?.phase)
        val notification = ringingNotification()
        assertNotNull(notification.fullScreenIntent)
        assertEquals(PomodoroRingingNotifier.CHANNEL_ID, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notifications.getNotificationChannel(notification.channelId).importance)
        assertEquals(listOf("Next phase", "End session"), notification.actions.map { it.title.toString() })
        assertEquals(1, notifications.activeNotifications.count { it.id == 3 })
    }

    @Test fun backAndHomeLeaveTheSameAlarmSoundAndDurablePhaseRunning() {
        val due = seedDuePhase()
        val before = awaitPlaying()
        openAlarm(HeldPhases.key(due))
        device.pressBack()
        assertKeepsRinging(before, due)
        openAlarm(HeldPhases.key(due))
        device.pressHome()
        assertKeepsRinging(before, due)
        openAlarm(HeldPhases.key(due))
        assertTrue(device.wait(Until.hasObject(By.text("Next phase")), 10_000))
    }

    @Test fun nextAndEndGoToTheServerWithTheShownRevisionAndItsAnswerClosesTheAlarm() {
        val fixture = TimerAcceptanceFixture()
        try {
            fixture.prepare()
            val lengths = SessionLengths(workMinutes = 1, shortRestMinutes = 1, longRestMinutes = 1, longRestEvery = 4)
            assertEquals(TimerActionResult.Done, runBlocking {
                app.timerActions.startSession(fixture.account.workspaceId, EntryFields("Alarm acceptance"), lengths)
            })
            // The server's one-minute work phase ends, and the phone rings from its planned end.
            val work = awaitPlaying(timeoutMillis = 90_000).ring!!
            openAlarm(work.phase)
            click("Next phase")
            awaitStopped()
            assertTrue(device.wait(Until.gone(By.text("Work done: start short rest")), 10_000))
            val rest = checkNotNull(serverTimer().pomodoro)
            assertEquals(PhaseKind.SHORT_REST, rest.phase.kind)
            assertEquals(rest.phase.start, app.timerState.state.value?.state?.pomodoro?.phase?.start)

            val restRing = awaitPlaying(timeoutMillis = 90_000).ring!!
            assertEquals(PomodoroPhaseKey(rest.phase.start), restRing.phase)
            openAlarm(restRing.phase)
            click("End session")
            awaitStopped()
            assertTrue(device.wait(Until.gone(By.text("Short rest done: start work")), 10_000))
            assertNull(serverTimer().pomodoro)
        } finally {
            fixture.cleanup()
        }
    }

    @Test fun recreationDuplicateAndStaleIntentsCannotReplaceTheAlarmAndAConfirmElsewhereClosesIt() {
        val due = seedDuePhase()
        val before = awaitPlaying()
        openAlarm(HeldPhases.key(due))
        val original = resumedAlarm()
        onMain { original.recreate() }
        assertTrue(device.wait(Until.hasObject(By.text("Work done: start short rest")), 10_000))
        openAlarm(HeldPhases.key(due))
        // Another phase's identity must leave the active alarm unchanged.
        context.startActivity(pomodoroAlarmIntent(context, PomodoroPhaseKey(HeldPhases.key(due).start.minusSeconds(60))))
        instrumentation.waitForIdleSync()
        assertTrue(device.wait(Until.hasObject(By.text("Work done: start short rest")), 10_000))
        assertKeepsRinging(before, due)
        // Another device confirms Next phase: the rest has five minutes left.
        val rest = HeldPhases.hold(PhaseTransition.SHORT_REST_TO_WORK, overtime = Duration.ofSeconds(-300))
        awaitStopped()
        assertTrue(device.wait(Until.gone(By.text("Work done: start short rest")), 10_000))
        context.startActivity(pomodoroAlarmIntent(context, HeldPhases.key(due)))
        instrumentation.waitForIdleSync()
        assertTrue(device.wait(Until.gone(By.text("Next phase")), 10_000))
        assertEquals(rest, app.timerState.state.value)
        assertFalse(app.pomodoroRinging.state.value.playing)
    }

    @Test fun withoutTheTimerApiTheAlarmStopsAndItsIntentShowsNothing() {
        val due = seedDuePhase()
        awaitPlaying()
        app.capabilities.forget()
        awaitStopped()
        context.startActivity(pomodoroAlarmIntent(context, HeldPhases.key(due)))
        instrumentation.waitForIdleSync()
        // The activity finishes as soon as it reads that the server has no timer API.
        assertTrue(device.wait(Until.gone(By.text("Next phase")), 10_000))
        instrumentation.waitForIdleSync()
        assertFalse(app.pomodoroRinging.state.value.serviceRunning)
        onMain {
            assertFalse(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is PomodoroAlarmActivity })
        }
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
        val playing = awaitPlaying()
        instrumentation.waitForIdleSync()
        onMain {
            assertFalse(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is PomodoroAlarmActivity })
        }
        assertKeepsRinging(playing, due)
        assertNotNull(PomodoroRingingNotifier(context).build(due.ring()).fullScreenIntent)
        assertEquals(listOf("Next phase", "End session"), ringingNotification().actions.map { it.title.toString() })
    }

    @Test fun alarmPendingIntentsAreImmutableAndKeepDistinctPhaseIdentities() {
        val due = HeldPhases.key(seedDuePhase())
        val first = pomodoroAlarmPendingIntent(context, due)
        val duplicate = pomodoroAlarmPendingIntent(context, due)
        val other = pomodoroAlarmPendingIntent(context, PomodoroPhaseKey(due.start.plusMillis(1)))
        assertEquals(first, duplicate)
        assertNotEquals(first, other)
        assertTrue(first.isImmutable)
        val intent = pomodoroAlarmIntent(context, due)
        assertEquals(due, intent.phaseKey())
        assertEquals(PomodoroAlarmActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.flags)
        assertNull(Intent(context, PomodoroAlarmActivity::class.java).phaseKey())
        // A provisional sticky-service notification cannot open an unvalidated phase.
        assertNull(PomodoroRingingNotifier(context).build(null).fullScreenIntent)
        first.cancel()
        other.cancel()
    }

    /** Holds a work phase whose planned end passed 65 s ago; its alarm rings at once. */
    private fun seedDuePhase(): HeldTimerState {
        HeldPhases.holdNoSession()
        awaitStopped()
        return HeldPhases.hold()
    }

    private fun serverTimer(): TimerState = runBlocking {
        assertEquals(TimerActionResult.Done, app.timerActions.read())
        checkNotNull(app.timerState.state.value).state
    }

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
    private fun assertKeepsRinging(before: PomodoroRingingState, due: HeldTimerState) {
        val after = app.pomodoroRinging.state.value
        assertTrue(after.playing)
        assertTrue(after.vibrationRequested)
        assertEquals(before.audioSessionId, after.audioSessionId)
        assertEquals(HeldPhases.key(due), after.ring?.phase)
        assertEquals(due, app.timerState.state.value)
    }
    private fun awaitPlaying(timeoutMillis: Long = 10_000): PomodoroRingingState = runBlocking {
        withTimeout(timeoutMillis) { app.pomodoroRinging.state.first { it.serviceRunning && it.playing && it.vibrationRequested } }
    }
    private fun awaitStopped() = HeldPhases.awaitStopped()
    private fun awaitNoSharedNotification() {
        // cancel() returns before the system drops the notification from its list.
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (notifications.activeNotifications.any { it.id == PomodoroSessionNotification.ID }) {
            assertTrue("the shared notification is removed", SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(50)
        }
    }
    private fun dismissKeyguard() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        // Android 12 lets the menu key dismiss a lock screen that wm dismiss-keyguard leaves.
        val ways = listOf("wm dismiss-keyguard", "input keyevent KEYCODE_MENU")
        var attempt = 0
        while (keyguard.isKeyguardLocked && SystemClock.elapsedRealtime() < deadline) {
            DeviceShell.run(ways[attempt++ % ways.size])
            SystemClock.sleep(300)
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
