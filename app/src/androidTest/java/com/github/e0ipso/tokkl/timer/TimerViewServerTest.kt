package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Timer page's List or Calendar choice against the real Tokkl container, which keeps it in
 * the account's `timerView` preference, as the web app does. Each test signs in to an account of
 * its own, since a choice changes the account's preferences.
 */
class TimerViewServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.set(false, account.client)
        } finally {
            try {
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    /** With nothing stored on the phone, the first launch opens the view the server keeps. */
    @Test
    fun aColdStartOpensTheServersCalendar() {
        setOnTheServer("calendar")
        assertNull("nothing stored on the phone", TimerViewStore(context).load())

        launch {
            awaitSelected(R.string.timer_view_calendar)
            awaitCalendar()
            composeRule.waitUntil("the phone keeps it", TIMEOUT_MS) {
                TimerViewStore(context).load() == StoredTimerView(TimerView.CALENDAR, unsent = false)
            }
        }
    }

    @Test
    fun aMissingViewOpensListAndTapsReachTheServer() {
        assertNull("a new account has no timerView", preferences()["timerView"])

        launch {
            refresh()
            awaitSelected(R.string.timer_view_list)

            segment(R.string.timer_view_calendar).performSemanticsAction(SemanticsActions.OnClick)
            awaitSelected(R.string.timer_view_calendar)
            composeRule.waitUntil("the server has calendar", TIMEOUT_MS) { serverView() == "calendar" }

            segment(R.string.timer_view_list).performSemanticsAction(SemanticsActions.OnClick)
            awaitSelected(R.string.timer_view_list)
            composeRule.waitUntil("the server has list", TIMEOUT_MS) { serverView() == "list" }
        }
    }

    /**
     * A view the server does not have opens List, over the one the phone keeps. Tokkl refuses
     * any timerView but list and calendar, so TimerViewSettingTest covers an unknown one.
     */
    @Test
    fun aViewMissingOnTheServerOpensListOverTheStoredOne() {
        TimerViewStore(context).save(StoredTimerView(TimerView.CALENDAR, unsent = false))

        launch {
            refresh()

            awaitSelected(R.string.timer_view_list)
            composeRule.waitUntil("the phone keeps List", TIMEOUT_MS) {
                TimerViewStore(context).load() == StoredTimerView(TimerView.LIST, unsent = false)
            }
        }
    }

    /**
     * Offline, a tap shows the view at once and stays on the phone until the next refresh sends it.
     * On screen, the network's return is such a refresh (MainActivity.networkChanged), so the
     * network comes back while the app is in the background, which watches none, and the trip
     * back to the foreground is the refresh that sends it.
     */
    @Test
    fun anOfflineTapGoesWithTheNextRefresh() {
        launch { scenario ->
            refresh()
            AirplaneMode.set(true, account.client)

            segment(R.string.timer_view_calendar).performSemanticsAction(SemanticsActions.OnClick)
            awaitSelected(R.string.timer_view_calendar)
            composeRule.waitUntil("the phone keeps it unsent", TIMEOUT_MS) {
                TimerViewStore(context).load() == StoredTimerView(TimerView.CALENDAR, unsent = true)
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            AirplaneMode.set(false, account.client)
            assertEquals("nothing reached the server", null, serverView())
            assertEquals("the phone still keeps it unsent", StoredTimerView(TimerView.CALENDAR, unsent = true), TimerViewStore(context).load())

            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil("the server has calendar", TIMEOUT_MS) { serverView() == "calendar" }
            composeRule.waitUntil("the phone marks it sent", TIMEOUT_MS) {
                TimerViewStore(context).load() == StoredTimerView(TimerView.CALENDAR, unsent = false)
            }
            awaitSelected(R.string.timer_view_calendar)
        }
    }

    private fun launch(steps: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(TIMEOUT_MS)
            steps(it)
        }
    }

    private fun refresh() = runBlocking { app.accountSettings.refresh().join() }

    private fun setOnTheServer(view: String) {
        account.client.send("POST", account.client.server.api("me", "preferences"), buildJsonObject { put("timerView", view) }, JsonObject.serializer())
            .orFail("POST /api/v9/me/preferences")
    }

    private fun preferences(): JsonObject =
        account.client.get(account.client.server.api("me", "preferences"), JsonObject.serializer()).orFail("GET /api/v9/me/preferences")

    private fun serverView(): String? = preferences()["timerView"]?.jsonPrimitive?.content

    private fun segment(@StringRes label: Int) = composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun awaitSelected(@StringRes label: Int) {
        composeRule.waitUntil("${string(label)} is chosen", TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(string(label)) and isSelectable() and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The Calendar view shows: its toolbar's arrows are there. */
    private fun awaitCalendar() {
        composeRule.waitUntil("the calendar shows", TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription(string(R.string.calendar_previous_day))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
