package com.github.e0ipso.tokkl.navigation

import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.HttpMethod
import com.github.e0ipso.tokkl.awaitPomodoroOffered
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.clients.ClientsRoute
import com.github.e0ipso.tokkl.components.accountRefresh
import com.github.e0ipso.tokkl.components.capabilities
import com.github.e0ipso.tokkl.components.timerActions
import com.github.e0ipso.tokkl.components.timerState
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.openMorePage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.projects.ProjectsRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.tags.TagsRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.trash.TrashRoute
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.github.e0ipso.tokkl.core.ui.R as UiR
import com.github.e0ipso.tokkl.feature.clients.R as ClientsR
import com.github.e0ipso.tokkl.feature.pomodoro.R as PomodoroR
import com.github.e0ipso.tokkl.feature.projects.R as ProjectsR
import com.github.e0ipso.tokkl.feature.reports.R as ReportsR
import com.github.e0ipso.tokkl.feature.tags.R as TagsR
import com.github.e0ipso.tokkl.feature.timer.R as TimerR
import com.github.e0ipso.tokkl.feature.trash.R as TrashR

/**
 * The bottom bar in the real activity (docs/PRD.md B1 to B3): each item opens its page, Back goes
 * to Timer and from Timer leaves the app, the More sheet holds Clients, Tags and Trash, the
 * screen shown survives the activity's recreation, and the Timer item says when a timer runs.
 * Tests start signed in to the test account, which has no entries, unless they sign in to one of
 * their own.
 */
class BottomBarTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private var account: TestAccounts.Account? = null

    @After
    fun deleteTheAccount() {
        try {
            account?.let(TestAccounts::delete)
        } finally {
            SessionRule.signOut()
        }
    }

    @Test
    fun eachBarItemOpensItsPageAndBackGoesToTimer() {
        launch { scenario ->
            composeRule.awaitPomodoroOffered(NETWORK_TIMEOUT_MS)
            for (page in listOf(PomodoroRoute, ProjectsRoute, SettingsRoute)) {
                barItem(page.label).performClick()
                heading(page.label).assertIsDisplayed()
                barItem(page.label).assertIsSelected()
                barItem(TimerR.string.nav_timer).assertIsNotSelected()

                pressBack(scenario)

                composeRule.awaitTimerPage()
            }
        }
    }

    /** A page opened from another page replaces it above Timer, so Back still goes to Timer. */
    @Test
    fun aPageOpenedFromAnotherGoesBackToTimer() {
        launch { scenario ->
            composeRule.openMorePage(ReportsR.string.reports, NETWORK_TIMEOUT_MS)
            heading(ReportsR.string.reports).assertIsDisplayed()
            barItem(ProjectsR.string.nav_projects).performClick()
            heading(ProjectsR.string.nav_projects).assertIsDisplayed()

            pressBack(scenario)

            composeRule.awaitTimerPage()
        }
    }

    @Test
    fun backFromTimerLeavesTheApp() {
        launch { scenario ->
            pressBack(scenario)

            // On Android 12 and later, Back on a task's root activity moves the task back instead
            // of finishing it. The compose rule's waits need an activity in the foreground.
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (scenario.state.isAtLeast(Lifecycle.State.STARTED)) {
                assertTrue("the activity is still ${scenario.state}", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(POLL_MS)
            }
        }
    }

    @Test
    fun theMoreSheetListsReportsTemplatesClientsTagsAndTrashAndOpensEach() {
        launch { scenario ->
            // The sheet lists Trash and Timer templates once the refresh's probe has found them.
            composeRule.waitUntil(
                "the refresh finds the trash",
                NETWORK_TIMEOUT_MS,
            ) { app.capabilities.state.value.trash }
            composeRule.waitUntil(
                "the refresh finds timer templates",
                NETWORK_TIMEOUT_MS,
            ) { app.capabilities.state.value.favorites }
            for (page in listOf(
                ReportsRoute,
                TemplatesRoute,
                ClientsRoute,
                TagsRoute,
                TrashRoute,
            )) {
                openMore()
                val items = composeRule.onAllNodes(sheetItem())
                    .fetchSemanticsNodes()
                    .sortedBy { it.boundsInRoot.top }
                    .map { node -> node.config[SemanticsProperties.Text].joinToString(" ") { it.text } }
                assertEquals(
                    listOf(
                        ReportsR.string.reports,
                        TimerR.string.nav_templates,
                        ClientsR.string.nav_clients,
                        TagsR.string.nav_tags,
                        TrashR.string.nav_trash,
                    ).map(::string),
                    items,
                )

                composeRule.onNode(sheetItem() and hasText(string(page.label))).performScrollTo().performClick()

                heading(page.label).assertIsDisplayed()
                awaitSheetClosed()
                barItem(R.string.nav_more).assertIsSelected()
                pressBack(scenario)
                composeRule.awaitTimerPage()
            }
        }
    }

    @Test
    fun theMoreSheetClosesOnCloseAndOnBack() {
        launch { scenario ->
            openMore()
            composeRule.onNode(
                hasContentDescription(string(R.string.nav_more_close)) and hasClickAction(),
            ).performClick()
            awaitSheetClosed()
            barItem(TimerR.string.nav_timer).assertIsSelected()

            openMore()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitSheetClosed()
            barItem(TimerR.string.nav_timer).assertIsSelected()
            assertEquals("Back closed the sheet, not the app", Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * A rotation, theme or font change recreates the activity. The view choice goes to the
     * account's preferences, so the test uses an account of its own.
     */
    @Test
    fun thePageTheStackTheViewAndTheSheetSurviveRecreation() {
        signInToNewAccount()
        launch { scenario ->
            composeRule.onNode(hasText(string(TimerR.string.timer_view_calendar)) and isSelectable()).performClick()
            composeRule.openMorePage(ReportsR.string.reports, NETWORK_TIMEOUT_MS)
            heading(ReportsR.string.reports).assertIsDisplayed()

            scenario.recreate()

            heading(ReportsR.string.reports).assertIsDisplayed()
            barItem(R.string.nav_more).assertIsSelected()
            pressBack(scenario)
            composeRule.awaitTimerPage()
            composeRule.onNode(hasText(string(TimerR.string.timer_view_calendar)) and isSelectable()).assertIsSelected()

            openMore()
            scenario.recreate()

            composeRule.waitUntil("the sheet shows again", TIMEOUT_MS) { sheetShows() }
        }
    }

    /**
     * While an entry runs, the Timer item carries the running dot, and TalkBack reads "Timer
     * running" as its state. Once the entry stops, both go. The dot's look is in the calendar
     * screenshot references, which show a running entry.
     */
    @Test
    fun theTimerItemSaysWhenATimerRuns() {
        val account = signInToNewAccount()
        val running = account.createEntry(
            "Running",
            Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS),
        )
        launch {
            val runningItem = barItemMatcher(TimerR.string.nav_timer) and
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(TimerR.string.nav_timer_running),
                )
            composeRule.waitUntil("the Timer item says a timer runs", NETWORK_TIMEOUT_MS) {
                composeRule.onAllNodes(runningItem).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.openMorePage(ReportsR.string.reports, NETWORK_TIMEOUT_MS)
            composeRule.onNode(runningItem).assertIsDisplayed()

            val stop = account.client.server.api(
                "workspaces",
                account.workspaceId.toString(),
                "time_entries",
                running.toString(),
                "stop",
            )
            runBlocking {
                account.client.send(
                    HttpMethod.PATCH,
                    stop,
                    buildJsonObject {},
                    JsonObject.serializer(),
                )
            }.orFail("PATCH .../time_entries/<id>/stop")
            runBlocking { app.accountRefresh.refresh().join() }

            composeRule.waitUntil("the Timer item drops its state", NETWORK_TIMEOUT_MS) {
                composeRule.onAllNodes(runningItem).fetchSemanticsNodes().isEmpty()
            }
            barItem(TimerR.string.nav_timer).assertIsDisplayed().assertIsNotSelected()
        }
    }

    /** The server's session keeps its mark off-page, through rest and recreation, until End removes it. */
    @Test
    fun thePomodoroItemFollowsTheServersSessionAndBackReturnsToTimer() {
        val account = signInToNewAccount()
        launch { scenario ->
            composeRule.awaitPomodoroOffered(NETWORK_TIMEOUT_MS)
            val marker = barItemMatcher(UiR.string.pomodoro_title) and
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(PomodoroR.string.nav_pomodoro_running),
                )
            assertEquals(0, composeRule.onAllNodes(marker).fetchSemanticsNodes().size)
            barItem(UiR.string.pomodoro_title).performClick()
            heading(UiR.string.pomodoro_title).assertIsDisplayed()
            barItem(UiR.string.pomodoro_title).assertIsSelected()
            // Another device starts the session; the phone's own action answers go to the same holder.
            assertEquals(
                TimerActionResult.Done,
                runBlocking {
                    app.timerActions.startSession(
                        WorkspaceId(account.workspaceId),
                        EntryFields(description = "Navigation session"),
                        SessionLengths(25, 5, 15, 4),
                    )
                },
            )
            composeRule.waitUntil("Pomodoro has its running state", TIMEOUT_MS) {
                composeRule.onAllNodes(marker).fetchSemanticsNodes().isNotEmpty()
            }
            pressBack(scenario)
            composeRule.awaitTimerPage()
            composeRule.onNode(marker).assertIsDisplayed()
            barItem(ProjectsR.string.nav_projects).performClick()
            assertEquals(TimerActionResult.Done, runBlocking { app.timerActions.nextPhase(heldRevision()) })
            composeRule.onNode(marker).assertIsDisplayed()
            scenario.recreate()
            heading(ProjectsR.string.nav_projects).assertIsDisplayed()
            composeRule.onNode(marker).assertIsDisplayed()
            assertEquals(TimerActionResult.Done, runBlocking { app.timerActions.endSession(heldRevision()) })
            composeRule.waitUntil("Pomodoro drops its running state", TIMEOUT_MS) {
                composeRule.onAllNodes(marker).fetchSemanticsNodes().isEmpty()
            }
            barItem(UiR.string.pomodoro_title).assertIsDisplayed().assertIsNotSelected()
        }
    }

    private fun heldRevision(): Long = app.timerState.state.value!!.state.revision

    /** Signs in to a new account of the test server, which the test deletes at its end. */
    private fun signInToNewAccount(): TestAccounts.Account {
        val created = TestAccounts.create(timezone = "UTC").also { account = it }
        Session(context).save(TokklTestServer.url, created.apiToken)
        return created
    }

    /** Starts MainActivity and runs [steps] on the Timer page. */
    private fun launch(steps: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
            steps(scenario)
        }
    }

    /** System Back, once the activity's window has input focus, so the key reaches it. */
    private fun pressBack(scenario: ActivityScenario<MainActivity>) {
        composeRule.waitUntil("the app window has input focus", TIMEOUT_MS) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun openMore() {
        barItem(R.string.nav_more).performClick()
        composeRule.waitUntil("the More sheet opens", TIMEOUT_MS) { sheetShows() }
    }

    private fun sheetShows(): Boolean =
        composeRule.onAllNodes(hasText(string(R.string.nav_more)) and isHeading()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitSheetClosed() {
        composeRule.waitUntil("the More sheet closes", TIMEOUT_MS) { !sheetShows() }
    }

    private fun barItem(@StringRes label: Int): SemanticsNodeInteraction = composeRule.onNode(barItemMatcher(label))

    private fun barItemMatcher(@StringRes label: Int): SemanticsMatcher =
        hasText(string(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** The sheet's page items. No item of the bar has their labels. */
    private fun sheetItem(): SemanticsMatcher = isSelectable() and (
        hasText(
            string(ReportsR.string.reports),
        ) or hasText(string(TimerR.string.nav_templates)) or hasText(string(ClientsR.string.nav_clients)) or
            hasText(string(TagsR.string.nav_tags)) or hasText(string(TrashR.string.nav_trash))
        )

    private fun heading(@StringRes label: Int): SemanticsNodeInteraction = composeRule.onNode(
        hasText(string(label)) and isHeading(),
    )

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val NETWORK_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
    }
}
