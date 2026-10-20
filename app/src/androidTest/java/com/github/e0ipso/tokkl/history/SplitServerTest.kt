package com.github.e0ipso.tokkl.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Split against the real Tokkl container, in the real activity (docs/PRD.md H6). The Split item
 * and the "Split at" field are found by the labels TalkBack reads. A time outside the entry is
 * refused with a message and queues nothing. A split in airplane mode shows both halves at once
 * and reaches the server when the network returns: the original ends at the split, and the new
 * entry runs from there to the original stop with the same fields, so the two add up to the whole.
 */
class SplitServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        signIn(TokklTestServer.url)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
        } finally {
            try {
                signIn(TokklTestServer.url)
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun aSplitMadeInAirplaneModeReachesTheServerAndTheHalvesAddUpToTheWhole() {
        val workspace = account.workspaceId
        val project = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", "Mural") },
            JsonObject.serializer(),
        ).orFail("POST a project").getValue("id").jsonPrimitive.long
        // Two hours, ending at least an hour ago, on whole hours so any resolution keeps them.
        val stop = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS)
        val start = stop.minus(2, ChronoUnit.HOURS)
        val source = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", "Paint the wall")
                put("project_id", project)
                put("tags", JsonArray(listOf(JsonPrimitive("outdoor"))))
                put("billable", true)
                put("start", start.toString())
                put("stop", stop.toString())
            },
            JsonObject.serializer(),
        ).orFail("POST an entry").getValue("id").jsonPrimitive.long
        runBlocking { app.accountSettings.refresh().join() }
        val split = start.plus(45, ChronoUnit.MINUTES)
        val madrid = ZoneId.of("Europe/Madrid")
        val clock = DateTimeFormatter.ofPattern("HH:mm")

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            composeRule.waitUntil("the entry shows", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasContentDescription(context.getString(R.string.history_more_actions, "Paint the wall")))
                    .fetchSemanticsNodes().size == 1
            }
            AirplaneMode.set(true, web)
            openSplit()

            // After the stop: refused, with the web app's message, and nothing queued.
            field().performTextReplacement(stop.plus(30, ChronoUnit.MINUTES).atZone(madrid).format(clock))
            splitButton().performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitUntil("the message shows", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(context.getString(R.string.history_split_outside), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("nothing queued", 0, runBlocking { app.changes.unsentCount.first() })

            field().performTextReplacement(split.atZone(madrid).format(clock))
            composeRule.waitUntil("the preview shows", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(preview(Duration.ofMinutes(45), Duration.ofMinutes(75)), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            splitButton().performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitUntil("the halves show at once, as a group of two marked unsent", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(
                    hasText("Paint the wall") and hasText(context.getString(R.string.history_unsent), ignoreCase = true) and
                        hasContentDescription(context.resources.getQuantityString(R.plurals.history_group_entries, 2, 2)),
                ).fetchSemanticsNodes().size == 1
            }
            composeRule.waitUntil("the message says so", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(context.getString(R.string.history_split_done))).fetchSemanticsNodes().isNotEmpty()
            }

            AirplaneMode.set(false, web)
            sendTheQueue()
        }

        val entries = serverEntries(start).filter { it.string("description") == "Paint the wall" }.sortedBy { it.instant("start") }
        assertEquals("the original and the second half", 2, entries.size)
        val (first, second) = entries
        assertEquals("the original keeps its id", source, first.getValue("id").jsonPrimitive.long)
        assertEquals(start, first.instant("start"))
        assertEquals("the original ends at the split", split, first.instant("stop"))
        assertEquals("the second half starts at the split", split, second.instant("start"))
        assertEquals("and ends at the original stop", stop, second.instant("stop"))
        assertEquals(
            "the durations add up to the original",
            Duration.between(start, stop).seconds,
            first.getValue("duration").jsonPrimitive.long + second.getValue("duration").jsonPrimitive.long,
        )
        assertEquals(project, second.getValue("project_id").jsonPrimitive.long)
        assertEquals(listOf("outdoor"), second.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("true", second.string("billable"))
    }

    /** Opens the entry's menu and its Split item, each by the label TalkBack reads. */
    private fun openSplit() {
        composeRule.onNode(hasContentDescription(context.getString(R.string.history_more_actions, "Paint the wall")) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasText(context.getString(R.string.history_split)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil("the dialog shows", LOAD_TIMEOUT_MS) {
            composeRule.onAllNodes(isDialog() and hasAnyDescendantText(R.string.history_split_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The time field, by its label. */
    private fun field() = composeRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.history_split_field)))

    private fun splitButton() = composeRule.onNode(
        hasText(context.getString(R.string.history_split_confirm)) and hasClickAction() and hasAnyAncestor(isDialog()),
    )

    private fun hasAnyDescendantText(id: Int) = androidx.compose.ui.test.hasAnyDescendant(hasText(context.getString(id)))

    private fun preview(first: Duration, second: Duration): String {
        val settings = app.accountSettings.state.value!!
        return context.getString(R.string.history_split_preview, settings.formatDuration(first.seconds), settings.formatDuration(second.seconds))
    }

    /** The server's entries that start within a day of [start]. */
    private fun serverEntries(start: Instant): List<JsonObject> {
        val url = web.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", start.minus(1, ChronoUnit.DAYS).toString())
            .addQueryParameter("end_date", start.plus(1, ChronoUnit.DAYS).toString())
            .build()
        return web.get(url, ListSerializer(JsonObject.serializer())).orFail("GET /api/v9/me/time_entries")
    }

    /** Sends the queue until it is empty, as the next refresh would. */
    private fun sendTheQueue() {
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            check(System.nanoTime() < deadline) { "the queue did not empty" }
            runBlocking { app.sender.send().join() }
        }
    }

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.content.orEmpty()

    private fun JsonObject.instant(key: String): Instant = OffsetDateTime.parse(string(key)).toInstant()

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
    }
}
