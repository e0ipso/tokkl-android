package com.github.e0ipso.tokkl.pomodoro

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createEmptyComposeRule

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.LoopbackRelay
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Pomodoro defaults against the real Tokkl container, which keeps them in the account's four
 * `pomodoro_*` length preferences, as the web app's Settings do. Each test signs in to an account
 * of its own, since a change alters the account's preferences.
 */
class PomodoroDefaultsServerTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account

    @Before
    fun createAnAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
    }

    @After
    fun cleanUp() {
        try {
            // TestAccounts talks to the server through the app's guard, which follows the session.
            signIn(TokklTestServer.url)
        } finally {
            try {
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    /** The Tokkl release scripts/instrumented-tests.sh pins as IMAGE serves this Settings-to-server round trip. */
    @Test
    fun settingsEditsReadAndWriteReleasedPreferencesAndPopulateTheNextSessionForm() {
        setOnTheServer(buildJsonObject {
            put(WORK, 50); put(SHORT_REST, 10); put(LONG_REST, 30); put(LONG_REST_EVERY, 3)
            put("timerView", "list")
        })
        signIn(TokklTestServer.url)
        refresh()
        assertEquals(PomodoroDefaults(50, 10, 30, 3), app.pomodoroDefaults.defaults.value)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTimerPage(TIMEOUT_MS)
            compose.openBarPage(R.string.settings, TIMEOUT_MS)
            compose.onNode(hasText(context.getString(R.string.settings_pomodoro_shared))).performScrollTo().assertIsDisplayed()
            val inputs = listOf(
                R.string.pomodoro_work_minutes to "40", R.string.pomodoro_short_rest_minutes to "8",
                R.string.pomodoro_long_rest_minutes to "20", R.string.pomodoro_long_rest_every to "5",
            )
            val original = listOf("50", "10", "30", "3")
            for ((index, pair) in inputs.withIndex()) {
                val (label, value) = pair
                compose.onNode(hasText(context.getString(label)) and hasClickAction()).performScrollTo().performClick()
                val input = compose.onNode(hasText(context.getString(label)) and hasSetTextAction())
                input.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(original[index])))
                input.performTextReplacement(value)
                compose.onNode(hasText(context.getString(R.string.settings_pomodoro_save)) and hasClickAction()).performClick()
            }
            val expected = PomodoroDefaults(40, 8, 20, 5)
            compose.waitUntil("Tokkl accepts the four Settings defaults", TIMEOUT_MS) {
                PomodoroDefaultsStore(context).load() == StoredPomodoroDefaults(expected, unsent = false)
            }
            val saved = preferences()
            assertEquals(expected, saved.pomodoroDefaults())
            assertEquals(JsonPrimitive("list"), saved["timerView"])
            scenario.recreate()
            compose.openBarPage(R.string.pomodoro_title, TIMEOUT_MS)
            for ((label, value) in inputs) {
                compose.onNode(hasText(context.getString(label)) and hasSetTextAction()).performScrollTo()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
            }
            compose.checkAccessibility()
        }
    }

    /**
     * A refresh reads the server's values. A change, clamped where Tokkl would answer 400, reaches
     * the server in a post that carries the four keys and nothing else. Other preferences stay.
     */
    @Test
    fun theValuesComeFromTheServerAndAChangePostsOnlyTheFourKeys() {
        setOnTheServer(
            buildJsonObject {
                put(WORK, 50)
                put(SHORT_REST, 10)
                put(LONG_REST, 30)
                put(LONG_REST_EVERY, 3)
                put("timerView", "calendar")
            },
        )
        val sent = StringBuffer()
        val target = TokklTestServer.url.toString().toHttpUrl()
        LoopbackRelay(target.host, target.port, watchConnections = { LoopbackRelay.ConnectionWatcher { bytes, count -> sent.append(String(bytes, 0, count, Charsets.ISO_8859_1)) } }).use { relay ->
            signIn(ServerUrl.parse("http://127.0.0.1:${relay.localPort}") ?: error("not a URL"))

            refresh()
            assertEquals(PomodoroDefaults(50, 10, 30, 3), app.pomodoroDefaults.defaults.value)
            assertEquals(StoredPomodoroDefaults(PomodoroDefaults(50, 10, 30, 3), unsent = false), PomodoroDefaultsStore(context).load())

            // The guard lets the app reach only the relay now, so the phone's sent mark, which
            // only a 200 sets, shows that the server took each change.
            change(PomodoroDefaults(40, 8, 20, 5))
            waitUntil("the server takes the change") {
                PomodoroDefaultsStore(context).load() == StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = false)
            }

            change(PomodoroDefaults(0, 61, 100, 13))
            assertEquals("the change shows clamped", PomodoroDefaults(1, 60, 60, 12), app.pomodoroDefaults.defaults.value)
            waitUntil("the server takes the clamped change") {
                PomodoroDefaultsStore(context).load() == StoredPomodoroDefaults(PomodoroDefaults(1, 60, 60, 12), unsent = false)
            }

            assertEquals(emptyList<String>(), relay.failures)
        }

        val posts = preferencePosts(sent.toString())
        assertEquals("two posts", 2, posts.size)
        assertEquals(setOf(WORK, SHORT_REST, LONG_REST, LONG_REST_EVERY), posts[0].keys)
        assertEquals(PomodoroDefaults(40, 8, 20, 5), posts[0].pomodoroDefaults())
        assertEquals(setOf(WORK, SHORT_REST, LONG_REST, LONG_REST_EVERY), posts[1].keys)
        assertEquals(PomodoroDefaults(1, 60, 60, 12), posts[1].pomodoroDefaults())
        signIn(TokklTestServer.url)
        val preferences = preferences()
        assertEquals("the server keeps the last change", PomodoroDefaults(1, 60, 60, 12), preferences.pomodoroDefaults())
        assertEquals("other preferences stay", JsonPrimitive("calendar"), preferences["timerView"])
    }

    /** Offline, a change stays on the phone, marked unsent, until the next refresh sends it. */
    @Test
    fun anOfflineChangeStaysOnThePhoneUntilTheNextRefreshSendsIt() {
        signIn(TokklTestServer.url)
        refresh()
        assertEquals("a new account has Tokkl's defaults", PomodoroDefaults(25, 5, 15, 4), app.pomodoroDefaults.defaults.value)
        // A closed loopback port makes only this app session offline on every API level.
        signIn(checkNotNull(ServerUrl.parse("http://127.0.0.1:1")))

        change(PomodoroDefaults(40, 8, 20, 5))
        waitUntil("the phone keeps it unsent") {
            PomodoroDefaultsStore(context).load() == StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true)
        }

        signIn(TokklTestServer.url)
        assertEquals("nothing reached the server", PomodoroDefaults(25, 5, 15, 4), serverDefaults())
        assertEquals(
            "the phone still keeps it unsent",
            StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true),
            PomodoroDefaultsStore(context).load(),
        )

        refresh()
        waitUntil("the server has the change") { serverDefaults() == PomodoroDefaults(40, 8, 20, 5) }
        waitUntil("the phone marks it sent") {
            PomodoroDefaultsStore(context).load() == StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = false)
        }
        assertEquals(PomodoroDefaults(40, 8, 20, 5), app.pomodoroDefaults.defaults.value)
    }

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    private fun refresh() = runBlocking { app.accountSettings.refresh().join() }

    private fun change(defaults: PomodoroDefaults) {
        lateinit var sending: Job
        instrumentation.runOnMainSync { sending = app.pomodoroDefaults.change(defaults) }
        runBlocking { sending.join() }
    }

    private fun setOnTheServer(body: JsonObject) {
        account.client.send("POST", account.client.server.api("me", "preferences"), body, JsonObject.serializer())
            .orFail("POST /api/v9/me/preferences")
    }

    private fun preferences(): JsonObject =
        account.client.get(account.client.server.api("me", "preferences"), JsonObject.serializer()).orFail("GET /api/v9/me/preferences")

    private fun serverDefaults(): PomodoroDefaults = preferences().pomodoroDefaults()

    private fun JsonObject.pomodoroDefaults() = PomodoroDefaults(
        workMinutes = getValue(WORK).jsonPrimitive.int,
        shortRestMinutes = getValue(SHORT_REST).jsonPrimitive.int,
        longRestMinutes = getValue(LONG_REST).jsonPrimitive.int,
        longRestEvery = getValue(LONG_REST_EVERY).jsonPrimitive.int,
    )

    /** The bodies of the `POST /api/v9/me/preferences` requests in [stream], read by their Content-Length. */
    private fun preferencePosts(stream: String): List<JsonObject> {
        val posts = mutableListOf<JsonObject>()
        var from = 0
        while (true) {
            val start = stream.indexOf("POST /api/v9/me/preferences HTTP/1.1\r\n", from)
            if (start < 0) return posts
            val headersEnd = stream.indexOf("\r\n\r\n", start)
            check(headersEnd > 0) { "a post without the end of its headers" }
            val length = stream.substring(start, headersEnd).lineSequence()
                .firstNotNullOfOrNull { line -> line.trim().takeIf { it.startsWith("Content-Length:", ignoreCase = true) } }
                ?.substringAfter(':')?.trim()?.toInt()
                ?: error("a post without a Content-Length")
            val bodyStart = headersEnd + 4
            posts += Json.parseToJsonElement(String(stream.substring(bodyStart, bodyStart + length).toByteArray(Charsets.ISO_8859_1))).jsonObject
            from = bodyStart + length
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val WORK = "pomodoro_focus_interval_in_minutes"
        const val SHORT_REST = "pomodoro_break_interval_in_minutes"
        const val LONG_REST = "pomodoro_long_break_interval_in_minutes"
        const val LONG_REST_EVERY = "pomodoro_long_break_every"
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 100L
    }
}
