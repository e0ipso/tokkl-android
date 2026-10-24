package com.github.e0ipso.tokkl.pomodoro

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.WindowCompat
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedInPanes
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** Direct destination coverage with Room and the real Tokkl server, no bar item required. */
class PomodoroPageServerTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val scope = MainScope()
    private val instant = AtomicReference(Instant.parse("2026-10-24T09:00:00Z"))
    private val clock = object : Clock() {
        override fun instant(): Instant = instant.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val databaseName = "pomodoro-page-${UUID.randomUUID()}.db"
    private lateinit var database: CacheDatabase
    private lateinit var account: TestAccounts.Account
    private lateinit var store: PomodoroSessionStore
    private lateinit var queue: ChangeQueue
    private lateinit var model: PomodoroScreenModel
    private lateinit var sender: ChangeSender
    private val shown = MutableStateFlow<PomodoroScreenModel?>(null)

    @Before
    fun prepareDisposableAccountAndDatabase() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        account.client.send("POST", account.client.server.api("me", "preferences"), buildJsonObject {
            put("pomodoro_focus_interval_in_minutes", 40)
            put("pomodoro_break_interval_in_minutes", 8)
            put("pomodoro_long_break_interval_in_minutes", 20)
            put("pomodoro_long_break_every", 3)
        }, JsonObject.serializer()).orFail("set Pomodoro defaults")
        runBlocking { app.accountSettings.refresh().join() }
        database = Room.databaseBuilder(context, CacheDatabase::class.java, databaseName).build()
        queue = ChangeQueue(database, Dispatchers.IO, onQueued = {}, clock = clock)
        store = PomodoroSessionStore.open(
            database, queue, Dispatchers.IO, clock,
            accountTime = { app.accountSettings.state.value?.time(clock) },
            alarms = object : PomodoroAlarmScheduler {
                override val permissionDenied = false
                override fun schedule(triggerAt: Instant) = true
                override fun cancel() = Unit
                override fun watchSession(running: Boolean) = Unit
            },
            phaseEnds = MemoryPomodoroPhaseEnds(),
        )
        sender = ChangeSender(RoomChangeStore(database), { account.client }, scope, Dispatchers.IO, clock = clock)
        model = newModel()
    }

    @After
    fun removeDisposableFixtures() {
        shown.value = null
        compose.waitForIdle()
        scope.cancel()
        try {
            if (::database.isInitialized) database.close()
            context.deleteDatabase(databaseName)
            assertFalse(context.getDatabasePath(databaseName).exists())
        } finally {
            try { if (::account.isInitialized) TestAccounts.delete(account) }
            finally { SessionRule.signOut() }
        }
    }

    @Test
    fun editedLengthsSkipOvertimeCurrentFieldsAndEndSaveImmutableEntriesToTheServer() {
        show()
        field(R.string.pomodoro_work_minutes).assert(hasText("40"))
        field(R.string.pomodoro_short_rest_minutes).assert(hasText("8"))
        field(R.string.pomodoro_long_rest_minutes).assert(hasText("20"))
        field(R.string.pomodoro_long_rest_every).assert(hasText("3"))
        type(R.string.pomodoro_work_minutes, "30")
        type(R.string.pomodoro_short_rest_minutes, "5")
        type(R.string.pomodoro_long_rest_minutes, "12")
        type(R.string.pomodoro_long_rest_every, "2")
        type(R.string.entry_description, "First work")
        tap(R.string.pomodoro_start)
        await { model.state.value.session != null && !model.state.value.busy }
        val first = runBlocking { store.current() }!!
        assertEquals(30.0, first.workMinutes, 0.0)
        assertEquals(5.0, first.shortRestMinutes, 0.0)
        assertEquals(12.0, first.longRestMinutes, 0.0)
        assertEquals(2.0, first.longRestEvery, 0.0)
        instant.set(Instant.parse("2026-10-24T09:15:00Z"))
        tap(R.string.pomodoro_start_short_rest)
        await { model.state.value.session?.phaseKind == "shortRest" && !model.state.value.busy }
        instant.set(Instant.parse("2026-10-24T09:30:00Z"))
        // Let the page's foreground clock observe the changed wall clock without advancing a phase.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(context.getString(R.string.pomodoro_overtime))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.pomodoro_overtime)).performScrollTo().assertIsDisplayed()
        type(R.string.entry_description, "Edited during rest")
        tap(R.string.pomodoro_start_work)
        await { model.state.value.session?.phaseKind == "work" && !model.state.value.busy }
        val frozen = runBlocking(Dispatchers.IO) { database.changes().first() }!!
        assertEquals("Edited during rest", frozen.description)
        type(R.string.entry_description, "Second work")
        await { model.state.value.session?.fields?.description == "Second work" }
        assertEquals(frozen, runBlocking(Dispatchers.IO) { database.changes().change(frozen.sequence) })
        instant.set(Instant.parse("2026-10-24T09:45:00Z"))
        tap(R.string.pomodoro_end)
        await { model.state.value.session == null && !model.state.value.busy && model.state.value.unsentCount == 2 }
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.pomodoro_unsent, 2, 2))
            .performScrollTo().assertIsDisplayed()
        runBlocking { sender.sendAndWait() }
        await { model.state.value.unsentCount == 0 }
        val entries = runBlocking { AccountCache(database).timeEntries.first() }
        assertEquals(2, entries.size)
        assertEquals(setOf("Edited during rest", "Second work"), entries.map { it.description }.toSet())
        for (entry in entries) {
            assertTrue(entry.id > 0)
            val saved = account.entry(entry.id)
            assertEquals(entry.description, saved.getValue("description").jsonPrimitive.content)
            assertNotNull(saved["stop"])
        }
    }

    @Test
    fun rangeErrorsDisableStartAndTooShortEndShowsTheExistingResult() {
        show()
        for ((label, bad) in listOf(
            R.string.pomodoro_work_minutes to "181", R.string.pomodoro_short_rest_minutes to "61",
            R.string.pomodoro_long_rest_minutes to "0", R.string.pomodoro_long_rest_every to "13",
        )) {
            type(label, bad)
            button(R.string.pomodoro_start).performScrollTo().assertIsNotEnabled()
            type(label, "1")
        }
        button(R.string.pomodoro_start).assertIsEnabled()
        tap(R.string.pomodoro_start)
        await { model.state.value.session != null && !model.state.value.busy }
        // No elapsed time always collapses on the account's grid.
        tap(R.string.pomodoro_end)
        await { model.state.value.notice == PomodoroNotice.TOO_SHORT && !model.state.value.busy }
        compose.onNodeWithText(context.getString(R.string.pomodoro_too_short)).performScrollTo().assertIsDisplayed()
        assertEquals(0, runBlocking { queue.unsentCount.first() })
        assertNull(runBlocking { store.current() })
    }

    @Test
    fun theRealServerRefusalAndUnsentCountRemainVisibleAfterEndAndReopening() {
        show()
        compose.runOnIdle { model.editFields(model.state.value.fields.copy(description = "Invalid project", projectId = Long.MAX_VALUE)) }
        tap(R.string.pomodoro_start)
        await { model.state.value.session != null && !model.state.value.busy }
        instant.set(Instant.parse("2026-10-24T09:30:00Z"))
        tap(R.string.pomodoro_end)
        await { model.state.value.session == null && !model.state.value.busy }
        runBlocking { sender.sendAndWait() }
        val held = runBlocking { withTimeout(10_000) { queue.held.first { it.isNotEmpty() } } }.single()
        assertFalse(held.refusal.isNullOrBlank())
        // A new Room connection reads the persisted outcome, independent of screen memory.
        runBlocking(Dispatchers.IO) {
            val reopened = Room.databaseBuilder(context, CacheDatabase::class.java, databaseName).build()
            try {
                assertNull(reopened.pomodoroSessions().current())
                assertEquals(1, reopened.changes().count().first())
                assertEquals(held.refusal, reopened.changes().heldFor(held.entryId)?.refusal)
            } finally { reopened.close() }
        }
        compose.runOnIdle { model = newModel(); shown.value = model }
        await { model.state.value.unsentCount == 1 && model.state.value.refused.size == 1 && !model.state.value.loading }
        val precise = context.getString(R.string.pomodoro_send_error, "Invalid project", held.refusal)
        compose.onNodeWithText(precise).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.pomodoro_unsent, 1, 1))
            .performScrollTo().assertIsDisplayed()
    }

    private fun newModel() = PomodoroScreenModel(
        store, app.pomodoroDefaults.defaults, app.accountSettings.state, queue.unsentCount, queue.held, scope,
    )

    private fun show() {
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.enableEdgeToEdge(activity.window)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        shown.value = model
        compose.setContent {
            val page by shown.collectAsState()
            TokklTheme {
                SignedInPanes(Destination.POMODORO, entryBeside = false, entryKey = 0) { destination ->
                    check(destination == Destination.POMODORO)
                    page?.let { PomodoroScreen(it, AccountCache(database)) }
                }
            }
        }
        await { !model.state.value.loading }
    }

    private fun await(condition: () -> Boolean) = compose.waitUntil(10_000, condition)
    private fun field(label: Int) = compose.onNode(hasText(context.getString(label)) and hasSetTextAction())
    private fun button(label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction())
    private fun type(label: Int, value: String) { field(label).performScrollTo().performTextReplacement(value) }
    private fun tap(label: Int) { button(label).performScrollTo().performClick() }
}
