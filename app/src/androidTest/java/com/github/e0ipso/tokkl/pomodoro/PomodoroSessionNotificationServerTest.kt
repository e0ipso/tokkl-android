package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.PhaseKind
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.components.accountRefresh
import com.github.e0ipso.tokkl.components.accountSettings
import com.github.e0ipso.tokkl.components.changes
import com.github.e0ipso.tokkl.components.pomodoroRinging
import com.github.e0ipso.tokkl.components.sender
import com.github.e0ipso.tokkl.components.signOut
import com.github.e0ipso.tokkl.components.storeSignIn
import com.github.e0ipso.tokkl.components.timerActions
import com.github.e0ipso.tokkl.components.timerState
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import com.github.e0ipso.tokkl.core.ui.R as UiR
import com.github.e0ipso.tokkl.feature.pomodoro.R as PomodoroR
import com.github.e0ipso.tokkl.feature.settings.R as SettingsR

/**
 * The session notification of a real session on the test server: its posted intents, taps through
 * the app's lifecycle, its Next phase and End session sent to the server with the revision shown,
 * offline failures that change nothing, and sign-out.
 */
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
        runBlocking { app.accountRefresh.refresh().join() }
        val settings = checkNotNull(app.accountSettings.state.value)
        workspace = checkNotNull(settings.defaultWorkspaceId).value
        app.storeSignIn(TokklTestServer.url, account.apiToken, settings)
        HeldPhases.learnTheTimerApi()
        RunningNotifications.allow()
    }

    @After fun cleanup() {
        try {
            AirplaneMode.set(false, account.client)
            runBlocking {
                app.signOut.run()
                withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning } }
            }
            runBlocking { assertNull(PomodoroSessionNotificationStore(context, Dispatchers.IO).load()) }
            assertFalse(manager.activeNotifications.any { it.id == PomodoroSessionNotifier.NOTIFICATION_ID })
            assertNull(app.timerState.state.value)
        } finally {
            try {
                if (::account.isInitialized) TestAccounts.delete(account) else AirplaneMode.off()
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test fun aColdNotificationLaunchOpensPomodoroAndRotationPreservesItsDestination() {
        val phase = startSession()
        awaitSessionNotification()
        val identity = runBlocking {
            checkNotNull(
                PomodoroSessionNotificationStore(context, Dispatchers.IO).load(),
            ).account
        }
        // ActivityScenario starts a new Activity with the same immutable intent the posted tap owns.
        ActivityScenario.launch<MainActivity>(pomodoroSessionOpenIntent(context, identity, phase)).use { scenario ->
            awaitHeading(UiR.string.pomodoro_title)
            scenario.recreate()
            awaitHeading(UiR.string.pomodoro_title)
            compose.onNodeWithText("Notification acceptance").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun aPostedWarmTapUsesOnNewIntentAndIsNotReplayedAfterOpeningAnotherPage() {
        startSession()
        val posted = awaitSessionNotification()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.openBarPage(SettingsR.string.settings)
            posted.contentIntent.send()
            awaitHeading(UiR.string.pomodoro_title)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitHeading(R.string.nav_timer)
            compose.openBarPage(SettingsR.string.settings)
            scenario.recreate()
            awaitHeading(SettingsR.string.settings)
        }
    }

    @Test fun aSessionTapLeavesTheFocusedTimerRoute() {
        runBlocking {
            app.changes.start(WorkspaceId(workspace), EntryFields("Regular timer"))
            app.sender.sendAndWait()
        }
        RunningNotifications.awaitShown("the regular timer before session navigation")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.openBarPage(R.string.nav_timer)
            compose.onNodeWithText(context.getString(R.string.timer_open_fullscreen))
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(
                10_000,
            ) { compose.onAllNodesWithTag("fullscreen_timer").fetchSemanticsNodes().isNotEmpty() }
            // The server stops the regular timer at the session start.
            startSession()
            awaitSessionNotification().contentIntent.send()
            awaitHeading(UiR.string.pomodoro_title)
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitHeading(R.string.nav_timer)
            compose.onNodeWithTag("fullscreen_timer").assertDoesNotExist()
        }
    }

    @Test fun nextAndEndGoToTheServerWithTheShownRevisionAndOfflineChangesNothing() {
        UiDevice.getInstance(instrumentation).pressHome()
        startSession()
        val work = awaitSessionNotification()
        val shown = serverTimer()

        AirplaneMode.set(true, account.client)
        sendActions(2) {
            work.actions[0].actionIntent.send()
            work.actions[1].actionIntent.send()
        }
        // Neither reached the server: the phone holds the same state and shows the same phase.
        assertEquals(shown.revision, app.timerState.state.value?.state?.revision)
        assertEquals(PhaseKind.WORK, app.timerState.state.value?.state?.pomodoro?.phase?.kind)
        assertEquals(
            work.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
            awaitSessionNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )

        AirplaneMode.set(false, account.client)
        sendActions(1) { work.actions[0].actionIntent.send() }
        val rest = awaitSessionNotification(PhaseKind.SHORT_REST)
        assertEquals(PhaseKind.SHORT_REST, serverTimer().pomodoro?.phase?.kind)
        // The work phase's buttons are gone with it.
        val before = serverTimer().revision
        sendActions(2) {
            work.actions[0].actionIntent.send()
            work.actions[1].actionIntent.send()
        }
        assertEquals(before, serverTimer().revision)
        assertEquals(PhaseKind.SHORT_REST, serverTimer().pomodoro?.phase?.kind)

        sendActions(1) { rest.actions[1].actionIntent.send() }
        assertNull(serverTimer().pomodoro)
        compose.waitUntil(
            10_000,
        ) { manager.activeNotifications.none { it.id == PomodoroSessionNotifier.NOTIFICATION_ID } }
    }

    @Test fun theRingingNotificationReplacesTheActiveOneAndNextRestoresOnlyOneActiveNotification() = runBlocking<Unit> {
        val phase = startSession(
            SessionLengths(workMinutes = 1, shortRestMinutes = 60, longRestMinutes = 60, longRestEvery = 4),
        )
        val active = awaitSessionNotification()
        assertEquals(PomodoroSessionNotifier.CHANNEL_ID, active.channelId)
        // The server's one-minute work phase ends, and the phone rings from its planned end.
        withTimeout(90_000) { app.pomodoroRinging.state.first { it.serviceRunning && it.playing } }
        compose.waitUntil(10_000) {
            manager.activeNotifications.singleOrNull { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
                ?.notification?.channelId == PomodoroRingingNotifier.CHANNEL_ID
        }
        val ring = manager.activeNotifications.single { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }.notification
        assertEquals(phase, app.pomodoroRinging.state.value.ring?.phase)
        PomodoroWorkWaits.ringingButtons(1, what = "Next phase on the ringing notification") {
            ring.actions[0].actionIntent.send()
        }
        withTimeout(10_000) { app.pomodoroRinging.state.first { !it.serviceRunning } }
        awaitSessionNotification(PhaseKind.SHORT_REST)
        assertEquals(1, manager.activeNotifications.count { it.id == PomodoroSessionNotifier.NOTIFICATION_ID })
        assertFalse(app.pomodoroRinging.state.value.playing)
        assertEquals(PhaseKind.SHORT_REST, serverTimer().pomodoro?.phase?.kind)
    }

    @Test fun signOutRemovesDisplayStateAndOldButtonsAndTapCannotAffectTheLaterAccount() = runBlocking<Unit> {
        val phase = startSession()
        val old = awaitSessionNotification()
        val oldIdentity = checkNotNull(PomodoroSessionNotificationStore(context, Dispatchers.IO).load()).account
        val settings = checkNotNull(app.accountSettings.state.value)
        app.signOut.run()
        assertNull(PomodoroSessionNotificationStore(context, Dispatchers.IO).load())
        assertFalse(manager.activeNotifications.any { it.id == PomodoroSessionNotifier.NOTIFICATION_ID })

        // Sign in again: the server still has the session, and a new notification identity shows it.
        ServerTrustStore(context, Dispatchers.IO).confirmCleartext(TokklTestServer.url)
        app.storeSignIn(TokklTestServer.url, account.apiToken, settings)
        HeldPhases.learnTheTimerApi()
        awaitSessionNotification()
        val newIdentity = checkNotNull(PomodoroSessionNotificationStore(context, Dispatchers.IO).load()).account
        assertNotEquals(oldIdentity, newIdentity)
        val revision = serverTimer().revision
        sendActions(2) {
            old.actions[0].actionIntent.send()
            old.actions[1].actionIntent.send()
        }
        assertEquals(revision, serverTimer().revision)
        assertEquals(PhaseKind.WORK, serverTimer().pomodoro?.phase?.kind)
        ActivityScenario.launch<MainActivity>(pomodoroSessionOpenIntent(context, oldIdentity, phase)).use {
            awaitHeading(R.string.nav_timer)
        }
        assertNotNull(awaitSessionNotification())
    }

    /** Starts a session on the server, three hours of work by default, and returns its phase. */
    private fun startSession(lengths: SessionLengths = SessionLengths(180, 60, 60, 4)): PomodoroPhaseKey {
        assertEquals(
            TimerActionResult.Done,
            runBlocking {
                app.timerActions.startSession(WorkspaceId(workspace), EntryFields("Notification acceptance"), lengths)
            },
        )
        return HeldPhases.key(checkNotNull(app.timerState.state.value))
    }

    /** The server's timer now, which the phone also holds. */
    private fun serverTimer(): TimerState = runBlocking {
        assertEquals(TimerActionResult.Done, app.timerActions.read())
        checkNotNull(app.timerState.state.value).state
    }

    private fun sendActions(count: Int, block: () -> Unit) = PomodoroWorkWaits.sessionButtons(count, send = block)

    private fun awaitSessionNotification(kind: PhaseKind = PhaseKind.WORK): Notification {
        val label = context.getString(
            if (kind ==
                PhaseKind.WORK
            ) {
                PomodoroR.string.pomodoro_session_work
            } else {
                PomodoroR.string.pomodoro_session_short_rest
            },
        )
        compose.waitUntil(10_000) {
            manager.activeNotifications.singleOrNull {
                it.id == PomodoroSessionNotifier.NOTIFICATION_ID
            }?.notification?.let {
                it.channelId == PomodoroSessionNotifier.CHANNEL_ID &&
                    it.extras.getCharSequence(Notification.EXTRA_TITLE).toString().startsWith(label)
            } == true
        }
        return manager.activeNotifications.single { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }.notification
    }

    private fun awaitHeading(label: Int) {
        val matcher = hasText(context.getString(label)) and isHeading()
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).assertIsDisplayed()
    }
}
