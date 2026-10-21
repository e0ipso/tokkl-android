package com.github.e0ipso.tokkl.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
        account = TestAccounts.create("Europe/Madrid")
        signIn(TokklTestServer.url)
        refresh()
        RunningNotifications.allow()
    }
    @After fun cleanup() {
        try { signIn(TokklTestServer.url); TestAccounts.delete(account) }
        finally { assertTrue("production sign-out succeeds", runBlocking { app.signOut.run() }) }
        assertNull(ReminderStore(context).load())
        assertNull(ReminderStore(context).loadTimer())
        assertTrue(work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().all { it.state.isFinished })
        assertNull(notification())
    }
    private fun signIn(url: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(url); Session(context).save(url, account.apiToken)
    }
    private fun refresh() = runBlocking { app.accountSettings.refresh().join() }
    private fun preferences() = account.client.get(account.client.server.api("me", "preferences"), JsonObject.serializer()).orFail("read preferences")
    private fun change(value: ReminderPreferences) = runBlocking { app.reminderSettings.change(value).join() }
    private fun seed(): Long {
        val id = account.createEntry("Reminder fixture", Instant.now().minusSeconds(7200).truncatedTo(ChronoUnit.SECONDS))
        refresh()
        return id
    }
    private fun awaitWork(predicate: (WorkInfo) -> Boolean): WorkInfo = runBlocking {
        withTimeout(60_000) {
            work.getWorkInfosForUniqueWorkFlow(ReminderWork.NAME).first { infos -> infos.any(predicate) }.first(predicate)
        }
    }
    private fun awaitFinished(id: UUID) = runBlocking {
        withTimeout(60_000) { work.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true } }
    }
    private fun notification() = manager.activeNotifications.firstOrNull { it.id == ReminderNotifier.ID }?.notification
    private fun fire(): Notification {
        val pending = awaitWork { !it.state.isFinished }
        assertNull("the reminder waits", notification())
        assertEquals("no periodic reminder", null, pending.periodicityInfo)
        assertEquals("works offline", NetworkType.NOT_REQUIRED, pending.constraints.requiredNetworkType)
        assertTrue("future delay", pending.initialDelayMillis > 0)
        driver.setInitialDelayMet(pending.id)
        awaitFinished(pending.id)
        return checkNotNull(notification()) { "the worker posted no reminder" }
    }
    private fun send(action: PendingIntent) {
        val finished = CompletableFuture<Unit>()
        action.send(context, 0, null, { _, _, _, _, _ -> finished.complete(Unit) }, Handler(Looper.getMainLooper()))
        finished.get(30, TimeUnit.SECONDS)
    }

    @Test fun oneWakeupAndKeepRunningConfirmsUntilStartCorrection() {
        assertTrue("idle schedules nothing", work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().all { it.state.isFinished })
        val id = seed()
        val pending = awaitWork { !it.state.isFinished }
        runBlocking { app.reminder.reconcile() }
        assertEquals(listOf(pending.id), work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().filter { !it.state.isFinished }.map { it.id })
        val first = fire()
        assertEquals(context.getString(R.string.reminder_question), first.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(ReminderNotifier.CHANNEL, first.channelId)
        assertEquals(context.getString(R.string.reminder_keep_running), first.actions[0].title.toString())
        send(first.actions[0].actionIntent)
        assertNull(notification())
        assertEquals(id, runBlocking { app.cache.running.first() }!!.entry.id)
        runBlocking { app.reminder.reconcile() }
        assertTrue("confirmed timer adds no wakeup", work.getWorkInfosForUniqueWork(ReminderWork.NAME).get().all { it.state.isFinished })
        val old = runBlocking { app.cache.running.first() }!!.entry
        signIn(NO_ROUTE)
        runBlocking { app.changes.update(id, com.github.e0ipso.tokkl.cache.EntryFields(description = "Corrected"), old.start.minusSeconds(3600), null) }
        val corrected = fire()
        assertNotNull(corrected)
        send(first.actions[1].actionIntent)
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
        assertTrue(runBlocking { app.changes.stop(id) })
        awaitFinished(resumed.id)
        assertNull(notification())
        runBlocking { app.reminder.fire(id, 0, 0) }
        assertNull("a late cancelled worker posts nothing", notification())
    }

    @Test fun reminderStopQueuesOfflineAndSendsAfterReconnect() {
        val id = seed()
        val notification = fire()
        signIn(NO_ROUTE)
        send(notification.actions[1].actionIntent)
        runBlocking { withTimeout(60_000) { app.cache.running.first { it == null } } }
        assertEquals(1, runBlocking { app.changes.unsentCount.first() })
        assertNull(this.notification())
        signIn(TokklTestServer.url)
        assertEquals("the server still runs it", JsonNull, account.entry(id)["stop"])
        runBlocking { app.sender.sendAndWait() }
        assertEquals(0, runBlocking { app.changes.unsentCount.first() })
        assertNotEquals(JsonNull, account.entry(id)["stop"])
    }

    @Test fun preferenceRoundtripAndOfflineRetryPreserveOtherKeys() {
        assertTrue(ReminderStore(context).load()!!.shared)
        account.client.send("POST", account.client.server.api("me", "preferences"), buildJsonObject {
            put(ReminderPreferences.ENABLED_KEY, false); put(ReminderPreferences.HOURS_KEY, 12)
        }, JsonObject.serializer()).orFail("web preference edit")
        refresh()
        assertEquals(ReminderPreferences(false, 12), app.reminderSettings.state.value)
        change(ReminderPreferences(false, 168))
        assertEquals(ReminderPreferences(false, 168), com.github.e0ipso.tokkl.session.MePreferences(preferences()).reminderPreferences())
        signIn(NO_ROUTE); change(ReminderPreferences(true, 1))
        assertTrue(ReminderStore(context).load()!!.unsent)
        signIn(TokklTestServer.url); refresh()
        // Join the retry path without changing the values again or polling a file.
        runBlocking { app.reminderSettings.refreshed(com.github.e0ipso.tokkl.session.MePreferences(preferences()), true).join() }
        assertFalse(ReminderStore(context).load()!!.unsent)
        val result = preferences()
        assertEquals(ReminderPreferences(true, 1), com.github.e0ipso.tokkl.session.MePreferences(result).reminderPreferences())
        assertNotNull("Pomodoro preferences remain", result["pomodoro_focus_interval_in_minutes"])
    }

    @Test fun preferenceStoreRoundtripClampsAndClearsAccountData() {
        val store = ReminderStore(context)
        store.save(StoredReminderPreferences(ReminderPreferences(false, 999), true, false))
        assertEquals(StoredReminderPreferences(ReminderPreferences(false, 168), true, false), store.load())
        store.saveTimer(ReminderTimer(42, 500, 1000, true))
        assertEquals(ReminderTimer(42, 500, 1000, true), ReminderStore(context).loadTimer())
        store.clear(); assertNull(store.load()); assertNull(store.loadTimer())
    }

    private companion object {
        val NO_ROUTE = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
