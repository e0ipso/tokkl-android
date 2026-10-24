package com.github.e0ipso.tokkl.widget

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.SystemDisplaySize
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement

class WidgetLargeTextTest {
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(SystemFontScale(SystemFontScale.LARGEST))

    @Test
    fun theRunningClockControlsAndFavoriteTextFitAtTheLargestFont() {
        assertWidgetFits(combinedSizes = false)
    }

    @Test
    fun theRunningClockControlsAndFavoriteTextFitAtLargestFontAndDisplaySize() {
        // NativeWidget launches its host inside this statement, after the density has changed.
        val body = object : Statement() {
            override fun evaluate() {
                assertWidgetFits(combinedSizes = true)
            }
        }
        SystemDisplaySize().apply(
            body,
            Description.createTestDescription(javaClass, "theRunningClockControlsAndFavoriteTextFitAtLargestFontAndDisplaySize"),
        ).evaluate()
    }

    private fun assertWidgetFits(combinedSizes: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("the widget uses the largest system font", SystemFontScale.LARGEST, context.resources.configuration.fontScale)
        val app = context.applicationContext as TokklApplication
        val account = TestAccounts.create(timezone = "Europe/Madrid")
        try {
            val api = account.client
            val workspace = account.workspaceId
            val project = api.send("POST", api.server.api("workspaces", "$workspace", "projects"),
                buildJsonObject { put("name", "Demo") }, JsonObject.serializer()).orFail("create project")["id"]!!.jsonPrimitive.long
            api.sendNoContent("POST", api.server.api("me", "preferences"), Json.parseToJsonElement(
                """{"favorites":[{"description":"Read","project_id":$project,"task_id":null,"tags":[],"billable":false}]}""",
            )).orFail("save favorite")
            Session(context).save(TokklTestServer.url, account.apiToken)
            runBlocking { app.accountSettings.refresh().join() }
            val offline = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
            ServerTrustStore(context).confirmCleartext(offline)
            Session(context).save(offline, account.apiToken)
            NativeWidget().use { widget ->
                if (combinedSizes) {
                    // Keep the original 360dp host case; the combined case must fit the actual
                    // display width, which Settings can reduce to 320dp at its largest size.
                    val width = minOf(360, context.resources.configuration.screenWidthDp)
                    assertTrue("the minimum widget width fits this display", width >= 240)
                    widget.resize(width, 320)
                }
                widget.awaitText("Read")
                runBlocking { app.changes.start(workspace, EntryFields("Work", projectId = project)) }
                widget.awaitText("Work")
                widget.awaitDescription("Stop the timer")
                widget.assertTextFitsVertically()
                widget.checkAccessibility()
                if (combinedSizes) {
                    widget.awaitUpdatesSettled()
                    widget.assertClockTicksWithoutUpdates()
                }
                widget.click("Stop the timer")
                widget.awaitText("No timer running")
                widget.assertTextFitsVertically()
                widget.checkAccessibility()
                widget.resize(240, 152)
                widget.await("the minimum size leaves favorites out at the largest font") { !widget.hasText("Read") }
                runBlocking { app.changes.start(workspace, EntryFields("Work", projectId = project)) }
                widget.awaitText("Work")
                widget.assertTextFitsVertically()
                widget.checkAccessibility()
            }
        } finally {
            assertTrue("sign-out clears the fixture", runBlocking { app.signOut.run() })
            ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
            Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
            TestAccounts.delete(account)
            SessionRule.signOut()
        }
    }
}
