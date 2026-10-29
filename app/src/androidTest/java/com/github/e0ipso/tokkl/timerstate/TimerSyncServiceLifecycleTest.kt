package com.github.e0ipso.tokkl.timerstate

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.BackgroundTrip
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmRearmReceiver
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotifier
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timerState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The timer sync service's lifecycle on the device, against the real Tokkl 1.10.0 server. Opening
 * the app signed in to a server with the timer API runs the service in the foreground as a
 * `specialUse` service, with its silent notification, and its stream brings another device's
 * session to the backgrounded phone with the session notification and the phase alarm. Signing out
 * and the server losing the timer API each stop the service, the stream, the session notification
 * and the alarm. Signing out leaves the server's session to the other devices.
 *
 * Boot, package replacement and Doze need a reboot, a reinstall or device idle, which end the
 * instrumentation, so plan 02's task 12 probed them by hand on both emulators. Its execution record
 * has the results.
 */
class TimerSyncServiceLifecycleTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val other get() = fixture.account.otherDevice

    @Before
    fun signIn() {
        RunningNotifications.allow()
        fixture.prepare()
    }

    @After
    fun endTheSessionAndSignOut() {
        try {
            val left = other.timerState()
            if (left.pomodoro != null) postTimer("pomodoro", "end", revision = left.revision)
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun anAppOpenRunsTheSilentStreamAndSignOutStopsItButLeavesTheSessionToOtherDevices() {
        val session = openTheAppAndReceiveASessionInTheBackground()

        runBlocking { app.signOut.run() }

        awaitEverythingStopped("sign-out")
        assertNull("sign-out clears the held state", app.timerState.state.value)
        assertNull("sign-out clears the stored state", TimerStateStore(context).load())
        val kept = other.timerState().pomodoro
        assertEquals("the other devices keep the server's session", session.phase.start, kept?.phase?.start)

        // A Next phase on another device reaches nothing on the signed-out phone.
        val next = postTimer("pomodoro", "next", revision = other.timerState().revision)
        assertNotNull(next.pomodoro)
        SystemClock.sleep(AT_ONCE_MS)
        assertNull(app.timerSyncService)
        assertNull(app.timerState.state.value)
        assertFalse(notifications.activeNotifications.any { it.id == PomodoroSessionNotifier.NOTIFICATION_ID })
        assertNull(alarms.nextAlarmClock)
    }

    /**
     * The stored account's server answers as Tokkl 1.8.1, with no timer API, as after a downgrade
     * of the server. The refresh learns that, and everything the timer API drove stops, while the
     * held state stays for the account.
     */
    @Test
    fun aServerThatLosesTheTimerApiStopsTheStreamTheSessionNotificationAndTheAlarm() {
        openTheAppAndReceiveASessionInTheBackground()

        ServerTrustStore(context).confirmCleartext(TokklTestServer.legacyUrl)
        Session(context).save(TokklTestServer.legacyUrl, TokklTestServer.legacyApiToken)
        // The stream may have started a refresh for the old server when the session replaced
        // its running entry. Wait for that refresh, then read the newly stored server.
        runBlocking { app.accountSettings.refreshAfterChange().join() }

        assertFalse("1.8.1 has no timer API", app.capabilities.state.value.timerState)
        awaitEverythingStopped("the loss of the timer API")
        assertNotNull("the server still holds the session", other.timerState().pomodoro)
    }

    /**
     * Opens the app, waits for the service, checks it and its notification, leaves the app, and
     * has the other device start a session the phone gets only through the stream. Returns it.
     */
    private fun openTheAppAndReceiveASessionInTheBackground(): TimerPomodoro {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            val service = await("the timer sync service runs", STREAM_CONNECT_MS) { app.timerSyncService }
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
            val shown = await("the sync notification", FGS_NOTIFICATION_DEFERRAL_MS) {
                notifications.activeNotifications.singleOrNull { it.id == TimerSyncNotification.ID }
            }
            assertEquals(TimerSyncNotification.CHANNEL_ID, shown.notification.channelId)
            assertTrue("the sync notification stays", shown.isOngoing)
            val channel = checkNotNull(notifications.getNotificationChannel(TimerSyncNotification.CHANNEL_ID))
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertNull("the channel makes no sound", channel.sound)
            assertFalse("the channel does not vibrate", channel.shouldVibrate())
            BackgroundTrip.leave()

            // Wait out the stream's connection with a running entry only the stream can bring.
            val entry = postEntry()
            await("the stream brings the other device's entry", STREAM_CONNECT_MS) {
                app.timerState.state.value?.state?.runningEntry?.id?.takeIf { it == entry }
            }
            val session = checkNotNull(postTimer("pomodoro", body = sessionBody()).pomodoro)
            val held = await("the backgrounded phone holds the session", AT_ONCE_MS) {
                app.timerState.state.value?.takeIf { it.state.pomodoro?.phase?.start == session.phase.start }
            }
            await("the session notification", AT_ONCE_MS) {
                notifications.activeNotifications.singleOrNull { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
            }
            val end = session.phase.plannedEnd.minus(held.clockOffset).toEpochMilli()
            await("the phase alarm at the corrected planned end", AT_ONCE_MS) { alarms.nextAlarmClock?.takeIf { it.triggerTime == end } }
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, rearmReceiverState())
            return session
        }
    }

    private fun awaitEverythingStopped(after: String) {
        await("the timer sync service stops after $after", AT_ONCE_MS) { (app.timerSyncService == null).takeIf { it } }
        await("the notifications go after $after", AT_ONCE_MS) {
            notifications.activeNotifications
                .none { it.id == TimerSyncNotification.ID || it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
                .takeIf { it }
        }
        await("the phase alarm goes after $after", AT_ONCE_MS) { (alarms.nextAlarmClock == null).takeIf { it } }
        assertEquals("boot and updates no longer start the app", PackageManager.COMPONENT_ENABLED_STATE_DISABLED, rearmReceiverState())
    }

    private fun rearmReceiverState(): Int =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, PomodoroAlarmRearmReceiver::class.java))

    private fun <T : Any> await(what: String, timeoutMs: Long, value: () -> T?): T {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            value()?.let { return it }
            if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("$what: not within $timeoutMs ms")
            SystemClock.sleep(POLL_MS)
        }
    }

    private fun postEntry(): Long {
        val workspace = fixture.account.workspaceId
        val body = buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "tokkl-android tests, another device")
            put("description", "Before the session")
            put("start", java.time.Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
            put("duration", -1)
        }
        val created = other.send("POST", other.server.api("workspaces", "$workspace", "time_entries"), body, JsonObject.serializer())
            .orFail("POST a running entry")
        return (created.getValue("id") as JsonPrimitive).content.toLong()
    }

    /** A session long enough that nothing rings during the test. */
    private fun sessionBody() = buildJsonObject {
        put("workspace_id", fixture.account.workspaceId)
        put("created_with", "tokkl-android tests, another device")
        put("description", "Lifecycle session")
        put("tags", JsonArray(emptyList<JsonPrimitive>()))
        put("billable", false)
        put("work_minutes", 25)
        put("short_rest_minutes", 5)
        put("long_rest_minutes", 15)
        put("long_rest_every", 4)
    }

    private fun postTimer(vararg path: String, revision: Long? = null, body: JsonObject? = null): TimerState =
        other.send("POST", other.server.app(*path), body ?: buildJsonObject { if (revision != null) put("revision", revision) }, TimerState.serializer())
            .orFail("POST /api/app/${path.joinToString("/")}")

    private companion object {
        /** Plan 02: a change reaches the phone, and stops what it drove, within 2 s. */
        const val AT_ONCE_MS = 2_000L

        /** How long the stream may take to connect after the app's first start. */
        const val STREAM_CONNECT_MS = 20_000L

        /**
         * Android 12 and later may wait up to 10 s before showing a foreground service's
         * notification, and the sync notification asks for no exception, so allow a little more.
         */
        const val FGS_NOTIFICATION_DEFERRAL_MS = 12_000L

        const val POLL_MS = 50L
    }
}
