package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Disposable real-server accounts, the Settings warning, and sign-out of a held phase with its real
 * alarm and ringing service.
 */
class PomodoroSignOutServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val accounts = mutableListOf<TestAccounts.Account>()
    private lateinit var suiteSettings: AccountSettings
    private lateinit var settings: AccountSettings
    private lateinit var account: TestAccounts.Account
    private lateinit var database: CacheDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepareDisposableAccount() {
        runBlocking { app.accountSettings.refresh().join() }
        suiteSettings = checkNotNull(app.accountSettings.state.value)
        account = TestAccounts.create(timezone = "Etc/UTC").also(accounts::add)
        app.storeSignIn(
            TokklTestServer.url, account.apiToken,
            suiteSettings.copy(defaultWorkspaceId = account.workspaceId, userId = account.id),
        )
        runBlocking { app.accountSettings.refresh().join() }
        settings = checkNotNull(app.accountSettings.state.value)
        database = CacheDatabase.open(context)
        RunningNotifications.allow()
        goOffline()
    }

    @After fun removeFixtures() {
        try {
            scenario?.let { finishWithin("closing Pomodoro sign-out activity") { it.close() } }
        } finally {
            try {
                PomodoroRingingActionReceiver.completed = null
                awaitStopped()
                ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                // Restore the suite account before deleting its disposable test accounts.
                app.storeSignIn(TokklTestServer.url, TokklTestServer.apiToken, suiteSettings)
                accounts.forEach(TestAccounts::delete)
            } finally {
                if (::database.isInitialized) database.close()
                SessionRule.signOut()
            }
        }
    }

    @Test fun warningCountsQueuedCompletedManualAndCurrentWorkAndCancelKeepsThemThenConfirmClearsOffline() {
        queueCompletedPomodoro()
        runBlocking {
            app.changes.create(
                account.workspaceId, EntryFields("Unsent manual"),
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600),
            )
        }
        val current = seed("work")
        assertEquals(2, queueCount())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.openBarPage(R.string.settings)
        val inDialog = hasAnyAncestor(isDialog())
        val signOutRow = hasText(context.getString(R.string.settings_sign_out)) and hasClickAction() and !inDialog
        compose.onNode(signOutRow).performScrollTo().performClick()
        compose.awaitText(context.getString(R.string.settings_sign_out_title))
        val warning = context.resources.getQuantityString(R.plurals.settings_sign_out_unsent, 3, 3)
        compose.onNode(hasText(warning) and inDialog).assertExists()
        assertTrue(warning.contains("unsaved Pomodoro work"))
        compose.onNode(hasText(context.getString(R.string.catalog_cancel)) and hasClickAction() and inDialog).performClick()
        compose.waitUntil("warning dismissed", 10_000) { app.signOutModel.state.value.unsentToConfirm == null }
        assertEquals(current, runBlocking { app.pomodoroSessions.current() })
        assertEquals(2, queueCount())
        assertTrue(Session(context).isSignedIn())
        compose.onNode(signOutRow).performScrollTo().performClick()
        compose.awaitText(context.getString(R.string.settings_sign_out_title))
        compose.onNode(hasText(context.getString(R.string.settings_sign_out)) and hasClickAction() and inDialog).performClick()
        compose.awaitText(context.getString(R.string.sign_in_title), 10_000)
        assertCleared()
    }

    @Test fun signingOutDuringEitherRestRemovesItsAlarmAndAllAccountStateOffline() {
        for (transition in listOf(PhaseTransition.SHORT_REST_TO_WORK, PhaseTransition.LONG_REST_TO_WORK)) {
            signIn(account, settings)
            goOffline()
            // Thirty minutes of the rest are left.
            val rest = HeldPhases.hold(transition, overtime = Duration.ofMinutes(-30), length = Duration.ofMinutes(60))
            awaitNextAlarm(checkNotNull(rest.ring()).end.toEpochMilli())
            assertTrue(runBlocking { app.signOut.run() })
            assertCleared()
        }
    }

    @Test fun aNonRingingSharedNotificationIsRemovedEvenWithoutACurrentSession() {
        val notifier = PomodoroRingingNotifier(context)
        notifier.createChannel()
        notifications.notify(PomodoroSessionNotification.ID, notifier.build(null))
        // notify() returns before the system lists the notification.
        val posted = System.nanoTime() + 10_000_000_000L
        while (notifications.activeNotifications.none { it.id == PomodoroSessionNotification.ID }) {
            assertTrue("the notification is posted", System.nanoTime() < posted)
            Thread.sleep(50)
        }
        assertTrue(runBlocking { app.signOut.run() })
        assertCleared()
    }

    @Test fun ringingSignOutRejectsLateButtonsAlarmsAndServiceChecksUnderTheNextAccount() {
        val old = HeldPhases.hold()
        HeldPhases.awaitPlaying()
        val notification = notifications.activeNotifications.single { it.id == PomodoroSessionNotification.ID }.notification
        assertTrue(runBlocking { app.signOut.run() })
        assertCleared()
        sendOldButtons(notification)
        assertCleared()
        // Authenticate a second disposable account through the normal application handoff.
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        app.storeSignIn(TokklTestServer.url, TokklTestServer.apiToken, suiteSettings)
        val next = TestAccounts.create(timezone = "Etc/UTC").also(accounts::add)
        signIn(next, suiteSettings.copy(defaultWorkspaceId = next.workspaceId, userId = next.id))
        // Twenty minutes of the next account's work phase are left.
        val current = HeldPhases.hold(PhaseTransition.WORK_TO_SHORT_REST, overtime = Duration.ofMinutes(-20), length = Duration.ofMinutes(25))
        val nextAlarm = checkNotNull(current.ring()).end.toEpochMilli()
        awaitNextAlarm(nextAlarm)
        sendOldButtons(notification)
        runBlocking {
            app.pomodoroAlarm.onAlarm(HeldPhases.key(old))
            assertFalse(app.pomodoroAlarm.withRingingPhase(HeldPhases.key(old)) { error("old service intent") })
            assertFalse(app.pomodoroAlarm.withRingingPhase(null) { error("the next account's phase has not ended") })
        }
        assertEquals(current, app.timerState.state.value)
        assertEquals(nextAlarm, alarms.nextAlarmClock?.triggerTime)
        assertFalse(app.pomodoroRinging.state.value.playing)
        assertFalse(app.pomodoroRinging.state.value.vibrationRequested)
    }

    /** Signs [owner] in on the test server and learns its timer API, as the sign-in screen and its refresh do. */
    private fun signIn(owner: TestAccounts.Account, with: AccountSettings) {
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        app.storeSignIn(TokklTestServer.url, owner.apiToken, with)
        HeldPhases.learnTheTimerApi()
    }

    private fun awaitNextAlarm(triggerTime: Long?) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (alarms.nextAlarmClock?.triggerTime != triggerTime && System.nanoTime() < deadline) Thread.sleep(50)
        assertEquals(triggerTime, alarms.nextAlarmClock?.triggerTime)
    }

    private fun goOffline() {
        ServerTrustStore(context).confirmCleartext(DEAD_SERVER)
        Session(context).save(DEAD_SERVER, account.apiToken)
        runBlocking { app.sender.send().join() }
    }

    /** A device-local session row of the kind older builds kept, for the sign-out warning's count. */
    private fun seed(kind: String): PomodoroSession = runBlocking {
        val at = Instant.ofEpochMilli(Instant.now().toEpochMilli())
        val phaseStart = if (kind == "work") at.minusSeconds(1800) else at
        val value = PomodoroSession(
            workspaceId = account.workspaceId,
            workMinutes = 60.0,
            shortRestMinutes = 60.0, longRestMinutes = 60.0, longRestEvery = 4.0,
            phaseKind = kind, phaseStartedAt = phaseStart,
            pomodoroStartedAt = at.minusSeconds(1800),
            completed = if (kind == "work") 0.0 else 1.0,
            fields = EntryFields("Old account unsaved work"),
        )
        withContext(Dispatchers.IO) { database.pomodoroSessions().upsert(value) }
        value
    }

    private fun queueCompletedPomodoro(): PomodoroStep.Saved = runBlocking {
        seed("work")
        val saved = app.pomodoroSessions.end() as PomodoroStep.Saved
        app.sender.send().join()
        withContext(Dispatchers.IO) {
            val create = checkNotNull(database.changes().first())
            database.changes().setMaybeSent(create.sequence, true)
        }
        assertEquals(1, queueCount())
        saved
    }

    private fun sendOldButtons(notification: Notification) {
        val done = CountDownLatch(3)
        PomodoroRingingActionReceiver.completed = done
        try {
            notification.actions[0].actionIntent.send()
            notification.actions[1].actionIntent.send()
            notification.deleteIntent.send()
            assertTrue("late actions finished", done.await(10, TimeUnit.SECONDS))
        } finally {
            PomodoroRingingActionReceiver.completed = null
        }
    }

    private fun assertCleared() {
        awaitStopped()
        assertFalse(Session(context).isSignedIn())
        assertNull(ServerConfigStore(context).load())
        assertNull(ApiTokenStore(context).load())
        assertNull(runBlocking { app.pomodoroSessions.current() })
        assertNull(TimerStateStore(context).load())
        awaitNextAlarm(null)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            context.packageManager.getComponentEnabledSetting(ComponentName(context, PomodoroAlarmRearmReceiver::class.java)),
        )
        // cancel() also returns before the system drops the notification from its list.
        val removed = System.nanoTime() + 10_000_000_000L
        while (notifications.activeNotifications.any { it.id == PomodoroSessionNotification.ID }) {
            assertTrue("the session notification is removed", System.nanoTime() < removed)
            Thread.sleep(50)
        }
        assertEquals(0, queueCount())
        assertEquals(0, runBlocking { app.changes.heldCount.first() })
        assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackgroundSend.WORK_NAME).get()
            .all { it.state == WorkInfo.State.CANCELLED })
        runBlocking { withContext(Dispatchers.IO) { assertNull(database.pomodoroSessions().current()) } }
    }

    private fun awaitStopped() = runBlocking {
        withTimeout(10_000) {
            app.pomodoroRinging.state.first { !it.serviceRunning && !it.playing && !it.vibrationRequested }
        }
    }

    private fun queueCount(): Int = runBlocking { app.changes.unsentCount.first() }

    private companion object {
        val DEAD_SERVER = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
