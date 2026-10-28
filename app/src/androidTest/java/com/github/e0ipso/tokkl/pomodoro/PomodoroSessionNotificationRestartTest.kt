package com.github.e0ipso.tokkl.pomodoro

import android.app.NotificationManager
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import java.io.File
import java.time.Duration
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * The session notification across a process restart, from the stored timer state rather than any
 * session of the phone's own. Run both ordered steps through the test orchestrator, each in a fresh
 * app process.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PomodoroSessionNotificationRestartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val handover = File(context.cacheDir, "PomodoroSessionNotificationRestartTest.properties")

    @Test fun step1PostsAHeldSessionAndLeavesItsImmutableTapForTheNextProcess() {
        SessionRule.signOut()
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
        HeldPhases.learnTheTimerApi()
        RunningNotifications.allow()
        // Two hours of a three-hour work phase are left, so nothing rings across the restart.
        val held = HeldPhases.hold(PhaseTransition.WORK_TO_SHORT_REST, overtime = Duration.ofHours(-2), length = Duration.ofHours(3))
        runBlocking { app.pomodoroSessionNotification.refresh(force = true) }
        val posted = manager.activeNotifications.single { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
        val identity = checkNotNull(PomodoroSessionNotificationStore(context).load()).account
        Properties().apply {
            setProperty("process", processIdentity())
            setProperty("account", identity)
            setProperty("posted", posted.postTime.toString())
            setProperty("phase", HeldPhases.key(held).start.toEpochMilli().toString())
            setProperty("revision", held.state.revision.toString())
            handover.outputStream().use { store(it, null) }
        }
    }

    @Test fun step2TheStoredStateKeepsTheNotificationAndItsTapOpensPomodoroThenSignOutErasesIt() {
        assertTrue("step 1 must precede step 2", handover.exists())
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val saved = Properties().apply { handover.inputStream().use { load(it) } }
        try {
            assertNotEquals(saved.getProperty("process"), processIdentity())
            // The new process holds the stored state, with no request for it.
            val held = checkNotNull(app.timerState.state.value)
            assertEquals(saved.getProperty("revision").toLong(), held.state.revision)
            val phase = HeldPhases.key(held)
            assertEquals(saved.getProperty("phase").toLong(), phase.start.toEpochMilli())
            assertTrue(runBlocking { app.pomodoroSessionNotification.acceptsTap(saved.getProperty("account"), phase) })
            // The process follows the persisted shown hash without reposting this notification.
            runBlocking { app.pomodoroSessionNotification.refresh() }
            val posted = manager.activeNotifications.single { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
            assertEquals(saved.getProperty("posted").toLong(), posted.postTime)
            posted.notification.contentIntent.send()
            val device = UiDevice.getInstance(instrumentation)
            assertTrue(device.wait(Until.hasObject(By.text("Pomodoro")), 10_000))
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().forEach { it.finish() }
            }
            runBlocking { app.signOut.run() }
            assertNull(PomodoroSessionNotificationStore(context).load())
            assertNull(TimerStateStore(context).load())
            assertNull(app.timerState.state.value)
            assertFalse(manager.activeNotifications.any { it.id == PomodoroSessionNotifier.NOTIFICATION_ID })
            assertTrue(handover.delete())
            assertFalse(handover.exists())
            SessionRule.signOut()
        }
    }

    private fun processIdentity() = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"
}
