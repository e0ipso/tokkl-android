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
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Disposable real-server accounts, real alarm/service effects and the Settings warning. */
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
                runBlocking { app.pomodoroSessions.discardRunningSession() }
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
        runBlocking { app.pomodoroSessions.rearm() }
        assertNotNull(alarms.nextAlarmClock)
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

    @Test fun signingOutDuringEitherRestRemovesTheWorkAlarmAndAllAccountStateOffline() {
        for (kind in listOf("shortRest", "longRest")) {
            app.storeSignIn(DEAD_SERVER, account.apiToken, settings)
            val rest = seed(kind)
            runBlocking { app.pomodoroSessions.rearm() }
            assertEquals(rest, runBlocking { app.pomodoroSessions.current() })
            assertEquals(1, runBlocking { app.pomodoroSessions.unsavedChangeCount() })
            assertNotNull(alarms.nextAlarmClock)
            assertTrue(runBlocking { app.signOut.run() })
            assertCleared()
            ServerTrustStore(context).confirmCleartext(DEAD_SERVER)
        }
    }

    @Test fun aNonRingingSharedNotificationIsRemovedEvenWithoutACurrentSession() {
        val notifier = PomodoroRingingNotifier(context)
        notifier.createChannel()
        notifications.notify(PomodoroSessionNotification.ID, notifier.build(null))
        assertTrue(notifications.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
        assertTrue(runBlocking { app.signOut.run() })
        assertCleared()
    }

    @Test fun ringingSignOutRejectsLateButtonsServiceStartsAndRetriesUnderTheNextAccount() {
        val saved = queueCompletedPomodoro()
        val retained = app.pomodoroSessions
        val old = seed("work", due = true)
        runBlocking { retained.rearm(deferRingingToAlarm = true) }
        runBlocking {
            withTimeout(10_000) { app.pomodoroRinging.state.first { it.playing && it.vibrationRequested } }
        }
        val notification = notifications.activeNotifications.single { it.id == PomodoroSessionNotification.ID }.notification
        assertTrue(runBlocking { app.signOut.run() })
        assertCleared()
        sendOldButtons(notification)
        assertCleared()
        // Authenticate a second disposable account through the normal application handoff.
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        app.storeSignIn(TokklTestServer.url, TokklTestServer.apiToken, suiteSettings)
        val next = TestAccounts.create(timezone = "Etc/UTC").also(accounts::add)
        app.storeSignIn(
            TokklTestServer.url, next.apiToken,
            suiteSettings.copy(defaultWorkspaceId = next.workspaceId, userId = next.id),
        )
        runBlocking { app.accountSettings.refresh().join() }
        val fresh = app.pomodoroSessions
        assertNotSame(retained, fresh)
        val current = runBlocking { fresh.start(LENGTHS, EntryFields("Next account work"), next.workspaceId) }
        val nextAlarm = alarms.nextAlarmClock?.triggerTime
        sendOldButtons(notification)
        runBlocking {
            retained.timerHandoffCommitted(saved)
            retained.onPhaseAlarm(old.phaseKey())
            retained.rearm(deferRingingToAlarm = true)
            assertFalse(retained.withRingingPhase(null) { error("old sticky service") })
            assertFalse(fresh.withRingingPhase(old.phaseKey()) { error("old service intent") })
            app.sender.send().join()
        }
        assertEquals(current, runBlocking { fresh.current() })
        assertEquals(nextAlarm, alarms.nextAlarmClock?.triggerTime)
        assertFalse(app.pomodoroRinging.state.value.playing)
        assertFalse(app.pomodoroRinging.state.value.vibrationRequested)
        assertEquals(0, queueCount())
        for (owner in listOf(account, next)) {
            val entries = owner.client.get(TokklTestServer.url.api("me", "time_entries"), JsonArray.serializer())
                .orFail("GET /api/v9/me/time_entries")
            assertTrue("old account work was never replayed for ${owner.id}", entries.isEmpty())
        }
    }

    private fun goOffline() {
        ServerTrustStore(context).confirmCleartext(DEAD_SERVER)
        Session(context).save(DEAD_SERVER, account.apiToken)
        runBlocking { app.sender.send().join() }
    }

    private fun seed(kind: String, due: Boolean = false): PomodoroSession = runBlocking {
        app.pomodoroSessions.discardRunningSession()
        val at = Instant.ofEpochMilli(Instant.now().toEpochMilli())
        val phaseStart = if (kind == "work" || due) at.minusSeconds(1800) else at
        val value = PomodoroSession(
            workspaceId = account.workspaceId,
            workMinutes = if (due) 1.0 else 60.0,
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
        assertNull(PomodoroPhaseEndStore(context).read())
        assertNull(alarms.nextAlarmClock)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            context.packageManager.getComponentEnabledSetting(ComponentName(context, PomodoroAlarmRearmReceiver::class.java)),
        )
        assertFalse(notifications.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
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
        val LENGTHS = PomodoroSessionConfig(60.0, 5.0, 15.0, 4.0)
    }
}
