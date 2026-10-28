package com.github.e0ipso.tokkl.settings

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsStore
import com.github.e0ipso.tokkl.pomodoro.StoredPomodoroDefaults
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import java.security.KeyStore
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Sign-out in the real activity, with an account of its own on the real Tokkl container
 * (docs/PRD.md S6). The account's project and entry reach the cache with a refresh. Then the
 * stored server moves to a port where nothing listens, so the two changes queued after that stay
 * unsent and their background work waits, on every API level, which airplane mode does not reach
 * yet. Sign-out has to warn with their count, keep everything on Cancel, and on confirmation leave
 * nothing of the account on the phone.
 */
class SignOutServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val workManager get() = WorkManager.getInstance(context)
    private lateinit var account: TestAccounts.Account
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun fillTheAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        createProject(PROJECT)
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        account.createEntry(ENTRY, now.minusSeconds(3600), now.minusSeconds(1800))
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        // A certificate pinned for another server, which sign-out has to forget too.
        ServerTrustStore(context).pinCertificate(PINNED)
    }

    @After
    fun cleanUp() {
        try {
            scenario?.let { finishWithin("closing the activity") { it.close() } }
        } finally {
            try {
                // TestAccounts talks to the server through the app's guard, which follows the session.
                ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun withUnsentChangesItWarnsAndCancelKeepsEverythingThenConfirmingClearsEveryStore() {
        assertTrue("the refresh cached the entry", runBlocking { app.cache.entries.first() }.any { it.entry.description == ENTRY })
        assertTrue("the refresh cached the project", runBlocking { app.cache.projects.first() }.any { it.name == PROJECT })
        assertNotNull(AccountSettingsStore(context).load())
        assertNotNull(CapabilitiesStore(context).load())
        assertNotNull(TimerViewStore(context).load())
        assertNotNull(PomodoroDefaultsStore(context).load())
        queueTwoUnsentChanges()
        changeThePomodoroDefaults()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.openBarPage(R.string.settings)
        val before = viewModels()
        composeRule.onNode(signOutRow()).performScrollTo().performClick()

        composeRule.awaitText(string(R.string.settings_sign_out_title))
        val warning = context.resources.getQuantityString(R.plurals.settings_sign_out_unsent, 2, 2)
        composeRule.onNode(hasText(warning) and inDialog()).assertExists()
        composeRule.onNode(hasText(string(R.string.catalog_cancel)) and hasClickAction() and inDialog()).performClick()

        composeRule.waitUntil("the warning closes", TIMEOUT_MS) { !exists(hasText(string(R.string.settings_sign_out_title))) }
        assertTrue("Cancel keeps the session", Session(context).isSignedIn())
        assertEquals("Cancel keeps the queue", 2, unsentCount())
        assertEquals("Cancel keeps the background work", WorkInfo.State.ENQUEUED, queueWork().single().state)
        assertTrue("Cancel keeps the cache", runBlocking { app.cache.entries.first() }.any { it.entry.description == ENTRY })
        assertNotNull("Cancel keeps the settings", app.accountSettings.state.value)
        assertEquals("Cancel keeps the pin", PINNED.sha256, ServerTrustStore(context).pinnedSha256(PINNED.host, PINNED.port))

        composeRule.onNode(signOutRow()).performScrollTo().performClick()
        composeRule.awaitText(string(R.string.settings_sign_out_title))
        composeRule.onNode(hasText(string(R.string.settings_sign_out)) and hasClickAction() and inDialog()).performClick()

        composeRule.awaitText(string(R.string.sign_in_title), TIMEOUT_MS)
        assertFalse("the bottom bar went with the session", exists(hasText(string(R.string.nav_timer))))
        assertNothingOfTheAccountStays()
        val after = viewModels()
        for ((kind, old) in before) assertNotSame("$kind is a new view model", old, after.getValue(kind))

        // The next account starts from nothing: the cache holds none of the first one's data.
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        assertNotNull("the next account's settings load", app.accountSettings.state.value)
        assertFalse(runBlocking { app.cache.entries.first() }.any { it.entry.description == ENTRY })
        assertFalse(runBlocking { app.cache.projects.first() }.any { it.name == PROJECT })
    }

    /** Points the session at a dead port, queues a start and its stop, and lets the send fail. */
    private fun queueTwoUnsentChanges() {
        val workspace = checkNotNull(app.accountSettings.state.value?.defaultWorkspaceId) { "no default workspace" }
        // The phone confirms one http server at a time. Moving the confirmation while the test
        // server is stored would leave it unconfirmed, and a background request from the refresh
        // above would open the http warning beside the sign-out dialog. With no server stored, such
        // a request is refused without a prompt.
        ServerConfigStore(context).clear()
        ServerTrustStore(context).confirmCleartext(DEAD_SERVER)
        Session(context).save(DEAD_SERVER, account.apiToken)
        assertEquals("no trust prompt waits", SessionTrustModel.State(), app.sessionTrust.state.value)
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Never sent")) }
        assertTrue(runBlocking { app.changes.stop(id) })
        runBlocking { app.sender.send().join() }
        assertEquals(2, unsentCount())
        assertEquals(WorkInfo.State.ENQUEUED, queueWork().single().state)
    }

    /** Changes the Pomodoro defaults while the session points at the dead port, so they stay unsent. */
    private fun changeThePomodoroDefaults() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.pomodoroDefaults.change(PomodoroDefaults(40, 8, 20, 5)) }
        val unsent = StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true)
        // No activity runs yet, so this waits without the compose rule.
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (PomodoroDefaultsStore(context).load() != unsent) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out: the phone keeps the change unsent")
            Thread.sleep(100)
        }
    }

    private fun assertNothingOfTheAccountStays() {
        assertFalse(Session(context).isSignedIn())
        assertNull("the server", ServerConfigStore(context).load())
        assertNull("the token", ApiTokenStore(context).load())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse("the token's Keystore key", keyStore.containsAlias(ApiTokenStore.KEY_ALIAS))
        assertNull("the stored settings", AccountSettingsStore(context).load())
        assertNull("the settings in memory", app.accountSettings.state.value)
        assertNull("the stored capabilities", CapabilitiesStore(context).load())
        assertEquals("the capabilities in memory", Capabilities.NONE, app.capabilities.state.value)
        assertNull("the stored Timer view", TimerViewStore(context).load())
        assertEquals("the Timer view in memory", TimerView.LIST, app.timerView.view.value)
        assertNull("the stored Pomodoro defaults", PomodoroDefaultsStore(context).load())
        assertEquals("the Pomodoro defaults in memory", PomodoroDefaults.DEFAULT, app.pomodoroDefaults.defaults.value)
        assertFalse("the http confirmation", ServerTrustStore(context).isCleartextConfirmed(DEAD_SERVER))
        assertNull("the pinned certificate", ServerTrustStore(context).pinnedSha256(PINNED.host, PINNED.port))
        assertEquals("the trust prompt", SessionTrustModel.State(), app.sessionTrust.state.value)
        assertEquals("the queue", 0, unsentCount())
        assertEquals("rows left in the cache's tables", emptyMap<String, Long>(), cacheRows().filterValues { it > 0 })
        assertTrue("the background work", queueWork().all { it.state == WorkInfo.State.CANCELLED })
    }

    /** Every table of the cache's database with its row count, read on a connection of its own. */
    private fun cacheRows(): Map<String, Long> {
        val path = context.getDatabasePath(CacheDatabase.FILE_NAME).path
        val database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { db ->
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertTrue("the database has its tables: $tables", "time_entries" in tables && "pending_changes" in tables)
            tables.filterNot { it in BOOKKEEPING_TABLES }.associateWith { table ->
                db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { it.moveToFirst(); it.getLong(0) }
            }
        }
    }

    /** Each view model of the activity, by class, as the activity reads them. */
    private fun viewModels(): Map<String, ViewModel> {
        val models = mutableMapOf<String, ViewModel>()
        checkNotNull(scenario).onActivity { activity ->
            val provider = ViewModelProvider(activity)
            for (kind in VIEW_MODELS) models[kind.simpleName] = provider[kind]
        }
        return models
    }

    private fun createProject(name: String) {
        val api = account.client
        val body = buildJsonObject {
            put("name", name)
            put("color", "#06aaf5")
        }
        val created = api.send("POST", api.server.api("workspaces", account.workspaceId.toString(), "projects"), body, JsonObject.serializer())
            .orFail("POST /api/v9/workspaces/<id>/projects")
        assertNotEquals(0L, created.getValue("id").jsonPrimitive.long)
    }

    private fun signOutRow(): SemanticsMatcher =
        hasText(string(R.string.settings_sign_out)) and hasClickAction() and !inDialog()

    private fun inDialog(): SemanticsMatcher = hasAnyAncestor(isDialog())

    private fun exists(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    private fun queueWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(BackgroundSend.WORK_NAME).get()

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val PROJECT = "Sign-out project"
        const val ENTRY = "Sign-out entry"

        /** Nothing listens on this port, so every request fails for want of a route. */
        val DEAD_SERVER = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))

        val PINNED = ServerCertificate(host = "10.0.2.2", port = 8443, sha256 = "ab".repeat(32), subject = "CN=sign-out test")

        /** SQLite's and Room's own tables, which hold no account data. */
        val BOOKKEEPING_TABLES = setOf("android_metadata", "room_master_table", "sqlite_sequence")

        val VIEW_MODELS = listOf(
            SignInViewModel::class.java,
            ReportsViewModel::class.java,
            HistoryViewModel::class.java,
            CalendarViewModel::class.java,
            ProjectsViewModel::class.java,
            ClientsViewModel::class.java,
            TagsViewModel::class.java,
            TimerViewModel::class.java,
            EntryFormViewModel::class.java,
        )
    }
}
