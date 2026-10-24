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
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.io.File
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Run both ordered steps through the test orchestrator, each in a fresh app process. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PomodoroSessionNotificationRestartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val handover = File(context.cacheDir, "PomodoroSessionNotificationRestartTest.properties")

    @Test fun step1PostsAnOfflineSessionAndLeavesItsImmutableTapForTheNextProcess() {
        SessionRule.signOut()
        // Disposable credentials to an unused port, as in RunningNotificationRestartTest.
        // No queue create or request can reach an account on a real server.
        val server = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, "not a token")
        RunningNotifications.allow()
        runBlocking {
            app.pomodoroSessions.start(PomodoroSessionConfig(180.0, 60.0, 60.0, 4.0), EntryFields("Session across processes"), 1)
            app.pomodoroSessionNotification.refresh(force = true)
        }
        val posted = manager.activeNotifications.single { it.id == PomodoroSessionNotification.ID }
        val identity = checkNotNull(PomodoroSessionNotificationStore(context).load()).account
        Properties().apply {
            setProperty("process", processIdentity())
            setProperty("account", identity)
            setProperty("posted", posted.postTime.toString())
            handover.outputStream().use { store(it, null) }
        }
    }

    @Test fun step2ThePersistedTapOpensPomodoroInTheNewProcessAndSignOutErasesTheSession() {
        assertTrue("step 1 must precede step 2", handover.exists())
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val saved = Properties().apply { handover.inputStream().use { load(it) } }
        try {
            assertNotEquals(saved.getProperty("process"), processIdentity())
            val phase = checkNotNull(runBlocking { app.pomodoroSessions.current() }).phaseKey()
            assertTrue(runBlocking { app.pomodoroSessionNotification.acceptsTap(saved.getProperty("account"), phase) })
            // The process follows the persisted shown hash without reposting this notification.
            runBlocking { app.pomodoroSessionNotification.refresh() }
            val posted = manager.activeNotifications.single { it.id == PomodoroSessionNotification.ID }
            assertEquals(saved.getProperty("posted").toLong(), posted.postTime)
            posted.notification.contentIntent.send()
            val device = UiDevice.getInstance(instrumentation)
            assertTrue(device.wait(Until.hasObject(By.text("Pomodoro")), 10_000))
            assertEquals("Session across processes", runBlocking { app.pomodoroSessions.current() }?.fields?.description)
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().forEach { it.finish() }
            }
            runBlocking { app.signOut.run() }
            assertNull(PomodoroSessionNotificationStore(context).load())
            assertNull(runBlocking { app.pomodoroSessions.current() })
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
            assertFalse(manager.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
            assertTrue(handover.delete())
            assertFalse(handover.exists())
            SessionRule.signOut()
        }
    }

    private fun processIdentity() = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"
}
