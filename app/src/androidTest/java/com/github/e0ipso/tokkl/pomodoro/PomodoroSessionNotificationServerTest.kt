package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive
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
import org.junit.rules.RuleChain

/** Acceptance source for actual posted intents, app lifecycle, offline Room actions and server sends. */
class PomodoroSessionNotificationServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val manager = context.getSystemService(NotificationManager::class.java)
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L

    @Before fun prepareDisposableAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        val settings = checkNotNull(app.accountSettings.state.value)
        workspace = checkNotNull(settings.defaultWorkspaceId)
        app.storeSignIn(TokklTestServer.url, account.apiToken, settings)
        RunningNotifications.allow()
    }

    @After fun cleanup() {
        PomodoroSessionActionReceiver.completed = null
        PomodoroRingingActionReceiver.completed = null
        try {
            runBlocking {
                app.signOut.run()
                withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning } }
            }
            assertNull(PomodoroSessionNotificationStore(context).load())
            assertFalse(manager.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
            assertNull(runBlocking { app.pomodoroSessions.current() })
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        } finally {
            try {
                if (::account.isInitialized) {
                    AirplaneMode.set(false, account.client)
                    TestAccounts.delete(account)
                } else AirplaneMode.off()
            } finally { SessionRule.signOut() }
        }
    }

    @Test fun aColdNotificationLaunchOpensPomodoroAndRotationPreservesItsDestination() {
        val phase = seedActive().phaseKey()
        val identity = checkNotNull(PomodoroSessionNotificationStore(context).load()).account
        // ActivityScenario starts a new Activity with the same immutable intent the posted tap owns.
        ActivityScenario.launch<MainActivity>(pomodoroSessionOpenIntent(context, identity, phase)).use { scenario ->
            awaitHeading(R.string.pomodoro_title)
            scenario.recreate()
            awaitHeading(R.string.pomodoro_title)
            compose.onNodeWithText("Notification acceptance").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun aPostedWarmTapUsesOnNewIntentAndIsNotReplayedAfterOpeningAnotherPage() {
        seedActive()
        val posted = awaitSessionNotification()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.openBarPage(R.string.settings)
            posted.contentIntent.send()
            awaitHeading(R.string.pomodoro_title)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitHeading(R.string.nav_timer)
            compose.openBarPage(R.string.settings)
            scenario.recreate()
            awaitHeading(R.string.settings)
        }
    }

    @Test fun aSessionTapLeavesTheFocusedTimerRouteWhileRegularTimerTapsKeepTheirLauncherBehavior() {
        AirplaneMode.set(true, account.client)
        val timer = runBlocking { app.changes.start(workspace, EntryFields("Regular timer")) }
        val regular = RunningNotifications.awaitShown("the regular timer before session navigation")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.openBarPage(R.string.settings)
            regular.notification.contentIntent.send()
            awaitHeading(R.string.settings)
            compose.openBarPage(R.string.nav_timer)
            compose.onNodeWithText(context.getString(R.string.timer_open_fullscreen))
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("fullscreen_timer").fetchSemanticsNodes().isNotEmpty() }
            // Seed the durable session before the old focused route receives its timer's idle
            // observation, then deliver the notification and idle change together.
            seedActive()
            val posted = awaitSessionNotification()
            posted.contentIntent.send()
            runBlocking { app.changes.stop(timer) }
            awaitHeading(R.string.pomodoro_title)
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitHeading(R.string.nav_timer)
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
        }
    }

    @Test fun nextAndEndWorkWithNoActivityOfflineAndEndKeepsThePagePendingCountUntilTheUsualSenderSends() {
        AirplaneMode.set(true, account.client)
        UiDevice.getInstance(instrumentation).pressHome()
        seedActive()
        val original = awaitSessionNotification()
        sendActions(1) { original.actions[0].actionIntent.send() }
        val rest = checkNotNull(runBlocking { app.pomodoroSessions.current() })
        assertEquals("shortRest", rest.phaseKind)
        sendActions(2) {
            original.actions[0].actionIntent.send()
            original.actions[1].actionIntent.send()
        }
        assertEquals(rest, runBlocking { app.pomodoroSessions.current() })
        val current = awaitSessionNotification("shortRest")
        sendActions(1) { current.actions[1].actionIntent.send() }
        assertNull(runBlocking { app.pomodoroSessions.current() })
        assertEquals(1, runBlocking { app.changes.unsentCount.first() })
        compose.waitUntil(10_000) { manager.activeNotifications.none { it.id == PomodoroSessionNotification.ID } }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.openBarPage(R.string.pomodoro_title)
            val pending = context.resources.getQuantityString(R.plurals.pomodoro_unsent, 1, 1)
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(pending)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
            AirplaneMode.set(false, account.client)
            runBlocking { app.sender.sendAndWait() }
            assertEquals(0, runBlocking { app.changes.unsentCount.first() })
            val entry = runBlocking { app.cache.timeEntries.first() }.single { it.description == "Notification acceptance" }
            assertEquals("Notification acceptance", account.entry(entry.id).getValue("description").jsonPrimitive.content)
        }
    }

    @Test fun theRingingNotificationReplacesTheActiveOneAndNextRestoresOnlyOneActiveNotification() {
        val phase = seedActive().phaseKey()
        val active = awaitSessionNotification()
        assertEquals(PomodoroSessionNotifier.CHANNEL_ID, active.channelId)
        runBlocking(Dispatchers.IO) {
            val row = checkNotNull(app.cacheDatabase.pomodoroSessions().current())
            app.cacheDatabase.pomodoroSessions().upsert(row.copy(workMinutes = 1.0))
            app.pomodoroSessions.rearm(deferRingingToAlarm = true)
        }
        runBlocking { withTimeout(10_000) { app.pomodoroRinging.state.first { it.serviceRunning && it.playing } } }
        compose.waitUntil(10_000) {
            manager.activeNotifications.singleOrNull { it.id == PomodoroSessionNotification.ID }
                ?.notification?.channelId == PomodoroRingingNotifier.CHANNEL_ID
        }
        val ring = manager.activeNotifications.single { it.id == PomodoroSessionNotification.ID }.notification
        assertEquals(phase, runBlocking { app.pomodoroSessions.current() }?.phaseKey())
        val done = CountDownLatch(1)
        PomodoroRingingActionReceiver.completed = done
        try {
            ring.actions[0].actionIntent.send()
            assertTrue(done.await(10, TimeUnit.SECONDS))
        } finally { PomodoroRingingActionReceiver.completed = null }
        runBlocking { withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning } } }
        awaitSessionNotification("shortRest")
        assertEquals(1, manager.activeNotifications.count { it.id == PomodoroSessionNotification.ID })
        assertFalse(app.pomodoroRinging.state.value.playing)
    }

    @Test fun signOutRemovesDisplayStateAndOldButtonsAndTapCannotAffectTheLaterAccount() {
        AirplaneMode.set(true, account.client)
        val original = seedActive()
        val old = awaitSessionNotification()
        val oldIdentity = checkNotNull(PomodoroSessionNotificationStore(context).load()).account
        val settings = checkNotNull(app.accountSettings.state.value)
        runBlocking { app.signOut.run() }
        assertNull(PomodoroSessionNotificationStore(context).load())
        assertFalse(manager.activeNotifications.any { it.id == PomodoroSessionNotification.ID })
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        app.storeSignIn(TokklTestServer.url, account.apiToken, settings)
        runBlocking(Dispatchers.IO) { app.cacheDatabase.pomodoroSessions().upsert(original) }
        runBlocking { app.pomodoroSessionNotification.refresh(force = true) }
        val newIdentity = checkNotNull(PomodoroSessionNotificationStore(context).load()).account
        assertNotEquals(oldIdentity, newIdentity)
        sendActions(2) { old.actions[0].actionIntent.send(); old.actions[1].actionIntent.send() }
        assertEquals(original, runBlocking { app.pomodoroSessions.current() })
        assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        ActivityScenario.launch<MainActivity>(pomodoroSessionOpenIntent(context, oldIdentity, original.phaseKey())).use {
            awaitHeading(R.string.nav_timer)
        }
        assertNotNull(awaitSessionNotification())
    }

    private fun seedActive(): PomodoroSession = runBlocking {
        // One hour of work avoids a zero-length snapped save, with two hours left before ringing.
        val start = Instant.ofEpochMilli(Instant.now().minusSeconds(3600).toEpochMilli())
        val row = PomodoroSession(
            workspaceId = workspace, workMinutes = 180.0, shortRestMinutes = 60.0, longRestMinutes = 60.0, longRestEvery = 4.0,
            phaseKind = "work", phaseStartedAt = start, pomodoroStartedAt = start, completed = 0.0,
            fields = EntryFields("Notification acceptance"),
        )
        withContext(Dispatchers.IO) { app.cacheDatabase.pomodoroSessions().upsert(row) }
        app.pomodoroSessions.rearm()
        app.pomodoroSessionNotification.refresh(force = true)
        row
    }

    private fun sendActions(count: Int, block: () -> Unit) {
        val done = CountDownLatch(count)
        PomodoroSessionActionReceiver.completed = done
        try { block(); assertTrue("notification actions finished", done.await(10, TimeUnit.SECONDS)) }
        finally { PomodoroSessionActionReceiver.completed = null }
    }

    private fun awaitSessionNotification(kind: String = "work"): Notification {
        val label = if (kind == "work") "Work" else "Short rest"
        compose.waitUntil(10_000) {
            manager.activeNotifications.singleOrNull { it.id == PomodoroSessionNotification.ID }?.notification?.let {
                it.channelId == PomodoroSessionNotifier.CHANNEL_ID &&
                    it.extras.getCharSequence(Notification.EXTRA_TITLE).toString().startsWith(label)
            } == true
        }
        return manager.activeNotifications.single { it.id == PomodoroSessionNotification.ID }.notification
    }

    private fun awaitHeading(label: Int) {
        val matcher = hasText(context.getString(label)) and isHeading()
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).assertIsDisplayed()
    }
}
