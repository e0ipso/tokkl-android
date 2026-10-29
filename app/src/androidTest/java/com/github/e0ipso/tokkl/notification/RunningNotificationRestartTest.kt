package com.github.e0ipso.tokkl.notification

import android.app.Notification
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.awaitControls
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.io.File
import java.time.Instant
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * A notification swiped away stays away when the app opens in a new process, which is how the
 * app usually opens, and the next edit posts it again (docs/PRD.md N1). The steps run in name
 * order, and the test orchestrator starts a new process for each. With no route to a server,
 * nothing the server says changes the running entry between the steps.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RunningNotificationRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    /** What step 1 left for step 2. */
    private val handOver = File(context.cacheDir, "RunningNotificationRestartTest.properties")

    @Test
    fun step1StartsATimerAndDismissesItsNotification() {
        SessionRule.signOut()
        signIn()
        RunningNotifications.allow()

        val id = runBlocking { app.changes.start(WORKSPACE, EntryFields(description = "Dismissed before the restart")) }
        val start = runBlocking { app.cache.running.first() }!!.entry.start
        val controls = app.awaitControls<TimerControls.Running>("the new running entry") { it.entry.id == id }
        val content = checkNotNull(runningNotificationContent(controls, emptyList(), emptyList()))
        val store = RunningNotificationStore(context)
        // Process-start observers may still be retiring the previous test's notification.
        // Dismiss this start only once its notification and restart record both reached it.
        RunningNotifications.awaitShown("the persisted start") {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == content.description &&
                store.load() == FollowedEntry(content.key, id)
        }
        RunningNotifications.dismiss()

        Properties().apply {
            setProperty("process", currentProcess())
            setProperty("id", id.toString())
            setProperty("start", start.toString())
            handOver.outputStream().use { store(it, null) }
        }
    }

    @Test
    fun step2OpensTheAppInANewProcessAndAnEditPostsTheNotificationAgain() {
        assertTrue("step 1 did not run before step 2", handOver.exists())
        // As SessionRule does: the work step 1 queued must never send in another test.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val saved = Properties().apply { handOver.inputStream().use { load(it) } }
        try {
            assertNotEquals("step 1 ran in this same process", saved.getProperty("process"), currentProcess())

            ActivityScenario.launch(MainActivity::class.java).use {
                RunningNotifications.assertStaysGone("the app opened in a new process")

                val edited = EntryFields(description = "Edited after the restart")
                val start = Instant.parse(saved.getProperty("start"))
                assertTrue(runBlocking { app.changes.update(saved.getProperty("id").toLong(), edited, start, stop = null) })
                RunningNotifications.awaitShown("the edit") {
                    it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == edited.description
                }
            }
        } finally {
            SessionRule.signOut()
            handOver.delete()
        }
        RunningNotifications.awaitGone("the notification after sign-out cleared the cache")
    }

    /** Nothing listens on this port, so every request fails for want of a route. */
    private fun signIn() {
        val server = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, "not a token")
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"

    private companion object {
        /** Any workspace: nothing reaches a server. */
        const val WORKSPACE = 1L
    }
}
