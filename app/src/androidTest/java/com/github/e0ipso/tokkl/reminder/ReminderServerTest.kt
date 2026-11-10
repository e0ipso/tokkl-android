package com.github.e0ipso.tokkl.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TimerActionToasts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.HttpMethod
import com.github.e0ipso.tokkl.api.ReminderChange
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.api.api
import com.github.e0ipso.tokkl.api.app
import com.github.e0ipso.tokkl.api.mePreferences
import com.github.e0ipso.tokkl.components.accountRefresh
import com.github.e0ipso.tokkl.components.accountSettings
import com.github.e0ipso.tokkl.components.cache
import com.github.e0ipso.tokkl.components.capabilities
import com.github.e0ipso.tokkl.components.changes
import com.github.e0ipso.tokkl.components.reminder
import com.github.e0ipso.tokkl.components.reminderSettings
import com.github.e0ipso.tokkl.components.sender
import com.github.e0ipso.tokkl.components.signOut
import com.github.e0ipso.tokkl.components.storeSignIn
import com.github.e0ipso.tokkl.feature.notification.R
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.notification.action
import com.github.e0ipso.tokkl.notification.stopAction
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.preferences.ReminderPreferences
import com.github.e0ipso.tokkl.preferences.StoredReminderPreferences
import com.github.e0ipso.tokkl.preferences.reminderPreferences
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Real-server fixtures, the production worker and notification actions, with TestDriver delays. */
class ReminderServerTest {
    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val work get() = WorkManager.getInstance(context)
    private val driver get() = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
    private lateinit var account: TestAccounts.Account

    @Before fun prepare() {
        assertTrue(runBlocking { app.signOut.run() })
        ServerTrustStore(context, Dispatchers.IO).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
        account = TestAccounts.create("Europe/Madrid")
        signIn(TokklTestServer.url)
        refresh()
        app.storeSignIn(TokklTestServer.url, account.apiToken, checkNotNull(app.accountSettings.state.value))
        refresh()
        RunningNotifications.allow()
    }

    @After fun cleanup() {
        try {
            signIn(TokklTestServer.url)
            TestAccounts.delete(account)
        } finally {
            assertTrue("production sign-out succeeds", runBlocking { app.signOut.run() })
        }
        runBlocking { assertNull(ReminderStore(context, Dispatchers.IO).load()) }
        runBlocking { assertNull(ReminderStore(context, Dispatchers.IO).loadTimer()) }
        assertTrue(work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().all { it.state.isFinished })
        awaitNotificationGone()
        assertNull(notification())
    }
    private fun signIn(url: ServerUrl) {
        ServerTrustStore(context, Dispatchers.IO).confirmCleartext(url)
        Session(context).save(url, account.apiToken)
    }
    private fun refresh() = runBlocking { app.accountRefresh.refresh().join() }
    private fun preferences() = runBlocking {
        account.client.get(
            account.client.server.api("me", "preferences"),
            JsonObject.serializer(),
        )
    }.orFail("read preferences")
    private fun change(value: ReminderPreferences) = runBlocking { app.reminderSettings.change(value).join() }
    private fun seed(): Long {
        val id = account.createEntry(
            "Reminder fixture",
            Instant.now().minusSeconds(7200).truncatedTo(ChronoUnit.SECONDS),
        )
        refresh()
        return id
    }
    private fun awaitWork(predicate: (WorkInfo) -> Boolean): WorkInfo = runBlocking {
        withTimeout(60_000) {
            work.getWorkInfosForUniqueWorkFlow(
                ReminderWork.NAME,
            ).first { infos -> infos.any(predicate) }.first(predicate)
        }
    }
    private fun awaitFinished(id: UUID) = runBlocking {
        withTimeout(60_000) { work.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true } }
    }
    private fun notification() = manager.activeNotifications.firstOrNull { it.id == ReminderNotifier.ID }?.notification
    private fun awaitNotificationGone() = runBlocking {
        withTimeout(10_000) {
            while (notification() != null) delay(25)
        }
    }

    /**
     * NotificationManager.notify() returns before the system lists the notification, so the
     * worker can finish before activeNotifications holds the reminder it posted.
     */
    private fun awaitNotification(): Notification? = runBlocking {
        withTimeoutOrNull(10_000) {
            var shown = notification()
            while (shown == null) {
                delay(25)
                shown = notification()
            }
            shown
        }
    }
    private fun fire(): Notification {
        val pending = awaitWork { !it.state.isFinished }
        assertNull("the reminder waits", notification())
        assertEquals("no periodic reminder", null, pending.periodicityInfo)
        assertEquals("works offline", NetworkType.NOT_REQUIRED, pending.constraints.requiredNetworkType)
        assertTrue("future delay", pending.initialDelayMillis > 0)
        driver.setInitialDelayMet(pending.id)
        awaitFinished(pending.id)
        return checkNotNull(awaitNotification()) { "the worker posted no reminder" }
    }
    private fun send(action: PendingIntent) {
        val finished = CompletableFuture<Unit>()
        action.send(context, 0, null, { _, _, _, _, _ -> finished.complete(Unit) }, Handler(Looper.getMainLooper()))
        finished.get(30, TimeUnit.SECONDS)
    }

    @Test fun oneWakeupAndKeepRunningConfirmsUntilStartCorrection() {
        assertTrue(
            "idle schedules nothing",
            work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().all { it.state.isFinished },
        )
        val id = seed()
        val pending = awaitWork { !it.state.isFinished }
        runBlocking { app.reminder.reconcile() }
        assertEquals(
            listOf(pending.id),
            work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().filter { !it.state.isFinished }.map { it.id },
        )
        val first = fire()
        assertEquals(context.getString(R.string.reminder_question), first.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(ReminderNotifier.CHANNEL, first.channelId)
        assertEquals(context.getString(R.string.reminder_keep_running), first.actions[0].title.toString())
        send(first.actions[0].actionIntent)
        // NotificationManager processes cancellation asynchronously after the action.
        awaitNotificationGone()
        assertNull(notification())
        assertEquals(id, runBlocking { app.cache.running.first() }!!.entry.id.value)
        runBlocking { app.reminder.reconcile() }
        assertTrue(
            "confirmed timer adds no wakeup",
            work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().all { it.state.isFinished },
        )
        val old = runBlocking { app.cache.running.first() }!!.entry
        signIn(NO_ROUTE)
        assertTrue(
            runBlocking {
                app.changes.updateRunning(
                    RunningEntryIdentity(EntryId(id), old.workspaceId, old.start),
                    com.github.e0ipso.tokkl.model.EntryFields(description = "Corrected"),
                    old.start.minusSeconds(3600),
                )
            },
        )
        val cached = runBlocking { app.cache.running.first() }!!.entry
        assertEquals(id, cached.id.value)
        assertEquals(old.start.minusSeconds(3600), cached.start)
        assertNull(cached.stop)
        val corrected = fire()
        assertNotNull(corrected)
        send(first.stopAction().actionIntent)
        assertNotNull("old start's Stop is inert", runBlocking { app.cache.running.first() })
    }

    @Test fun stopBeforeDelayCancelsAndDisableEnableKeepsOnlyOneRequest() {
        val id = seed()
        val pending = awaitWork { !it.state.isFinished }
        change(ReminderPreferences(false, 8))
        awaitFinished(pending.id)
        assertNull(notification())
        change(ReminderPreferences(true, 8))
        val resumed = awaitWork { !it.state.isFinished }
        assertNotEquals(pending.id, resumed.id)
        signIn(NO_ROUTE)
        assertTrue(runBlocking { app.changes.stop(EntryId(id)) })
        awaitFinished(resumed.id)
        assertNull(notification())
        runBlocking { app.reminder.fire(id, 0, 0) }
        assertNull("a late cancelled worker posts nothing", notification())
    }

    @Test fun reminderStopQueuesOfflineAndSendsAfterReconnect() {
        val id = seed()
        val notification = fire()
        signIn(NO_ROUTE)
        send(notification.stopAction().actionIntent)
        runBlocking { withTimeout(60_000) { app.cache.running.first { it == null } } }
        assertEquals(1, runBlocking { app.changes.unsentCount.first() })
        assertNull(this.notification())
        signIn(TokklTestServer.url)
        assertEquals("the server still runs it", JsonNull, account.entry(id)["stop"])
        runBlocking { app.sender.sendAndWait() }
        assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        assertNotEquals(JsonNull, account.entry(id)["stop"])
    }

    @Test fun reminderPauseGoesToTheServerAndOfflineKeepsTheReminder() {
        // A new, empty timer state for this account, as the sign-in screen stores it.
        app.storeSignIn(TokklTestServer.url, account.apiToken, checkNotNull(app.accountSettings.state.value))
        refresh()
        assertTrue("the test server offers the timer API", app.capabilities.state.value.timerState)
        val id = seed()
        val reminder = fire()
        assertEquals(
            listOf(
                R.string.reminder_keep_running,
                R.string.notification_pause,
                R.string.notification_stop,
            ).map(context::getString),
            reminder.actions.map { it.title.toString() },
        )

        signIn(NO_ROUTE)
        TimerActionToasts.await(
            TimerActionToasts.offline,
        ) { send(reminder.action(R.string.notification_pause).actionIntent) }
        assertNotNull("the reminder stays for another try", notification())
        assertEquals("the timer runs on", id, runBlocking { app.cache.running.first() }?.entry?.id?.value)
        assertEquals("Pause queues nothing", 0, runBlocking { app.changes.unsentCount.first() })

        signIn(TokklTestServer.url)
        send(reminder.action(R.string.notification_pause).actionIntent)
        runBlocking { withTimeout(60_000) { app.cache.running.first { it == null } } }
        awaitNotificationGone()
        val state = runBlocking {
            account.client.get(
                account.client.server.app("timer"),
                TimerState.serializer(),
            )
        }.orFail("GET /api/app/timer")
        assertEquals("the server paused the reminded timer", true, state.pause?.paused)
        assertNotEquals(JsonNull, account.entry(id)["stop"])
    }

    @Test fun withoutTheTimerApiTheReminderOffersNoPause() {
        seed()
        runBlocking { app.capabilities.forget() }
        val reminder = fire()
        assertEquals(
            listOf(R.string.reminder_keep_running, R.string.notification_stop).map(context::getString),
            reminder.actions.map { it.title.toString() },
        )
    }

    @Test fun preferenceRoundtripAndOfflineRetryPreserveOtherKeys() {
        runBlocking { assertTrue(ReminderStore(context, Dispatchers.IO).load()!!.shared) }
        runBlocking {
            account.client.send(
                HttpMethod.POST,
                account.client.server.api("me", "preferences"),
                buildJsonObject {
                    put(ReminderChange.ENABLED, false)
                    put(ReminderChange.HOURS, 12)
                },
                JsonObject.serializer(),
            )
        }.orFail("web preference edit")
        refresh()
        assertEquals(ReminderPreferences(false, 12), app.reminderSettings.state.value)
        change(ReminderPreferences(false, 168))
        assertEquals(
            ReminderPreferences(false, 168),
            mePreferences(preferences().toString()).reminderPreferences(),
        )
        signIn(NO_ROUTE)
        change(ReminderPreferences(true, 1))
        runBlocking { assertTrue(ReminderStore(context, Dispatchers.IO).load()!!.unsent) }
        signIn(TokklTestServer.url)
        refresh()
        // Join the retry path without changing the values again or polling a file.
        runBlocking {
            app.reminderSettings.refreshed(
                mePreferences(preferences().toString()),
                true,
            ).join()
        }
        runBlocking { assertFalse(ReminderStore(context, Dispatchers.IO).load()!!.unsent) }
        val result = preferences()
        assertEquals(
            ReminderPreferences(true, 1),
            mePreferences(result.toString()).reminderPreferences(),
        )
        assertNotNull("Pomodoro preferences remain", result["pomodoro_focus_interval_in_minutes"])
    }

    @Test fun preferenceStoreRoundtripClampsAndClearsAccountData() = runBlocking<Unit> {
        val store = ReminderStore(context, Dispatchers.IO)
        store.save(StoredReminderPreferences(ReminderPreferences(false, 999), true, false))
        assertEquals(StoredReminderPreferences(ReminderPreferences(false, 168), true, false), store.load())
        store.saveTimer(ReminderTimer(42, 500, 1000, true))
        assertEquals(ReminderTimer(42, 500, 1000, true), ReminderStore(context, Dispatchers.IO).loadTimer())
        store.clear()
        assertNull(store.load())
        assertNull(store.loadTimer())
    }

    private companion object {
        val NO_ROUTE = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
