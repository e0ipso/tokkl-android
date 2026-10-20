package com.github.e0ipso.tokkl.trash

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The trash page against the real Tokkl container (docs/PRD.md H7): it lists an account's deleted
 * entries, restores one, deletes one for good and, after a question, empties the trash. Each test
 * signs in to a throwaway account whose entries it deletes first, and reaches the page from the
 * More sheet, as a user does.
 */
class TrashServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private var account: TestAccounts.Account? = null

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
            account?.let(TestAccounts::delete)
        } finally {
            SessionRule.signOut()
        }
    }

    @Test
    fun restorePurgeAndEmptyChangeTheTrashOnTheServer() {
        val account = signInToNewAccount()
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        val ids = listOf(RESTORED, PURGED, EMPTIED, ALSO_EMPTIED).mapIndexed { index, description ->
            val from = start.plus(index * 20L, ChronoUnit.MINUTES)
            account.createEntry(description, from, from.plus(10, ChronoUnit.MINUTES)).also(account::deleteEntry)
        }
        launchOnTrash {
            for (description in listOf(RESTORED, PURGED, EMPTIED, ALSO_EMPTIED)) composeRule.awaitText(description, TIMEOUT_MS)

            control(R.string.trash_restore_named, RESTORED).performScrollTo().performClick()
            composeRule.awaitText(string(R.string.trash_restored), NETWORK_TIMEOUT_MS)
            awaitGone(RESTORED)
            val restored = account.entry(ids[0])
            assertEquals("the entry is back", RESTORED, restored.getValue("description").jsonPrimitive.content)
            assertFalse("the entry is out of the trash", ids[0] in trashIds(account))

            control(R.string.trash_purge_named, PURGED).performScrollTo().performClick()
            composeRule.awaitText(string(R.string.trash_purged), NETWORK_TIMEOUT_MS)
            awaitGone(PURGED)
            assertFalse("the entry went for good", ids[1] in trashIds(account))

            composeRule.onNode(hasText(string(R.string.trash_empty_trash)) and hasClickAction() and !inDialog()).performClick()
            composeRule.awaitText(string(R.string.trash_empty_confirm_title), TIMEOUT_MS)
            composeRule.onNode(hasText(context.resources.getQuantityString(R.plurals.trash_empty_confirm_message, 2, 2)) and inDialog())
                .assertExists()
            assertEquals("the question sends nothing", listOf(ids[2], ids[3]), trashIds(account).sorted())
            composeRule.onNode(hasText(string(R.string.trash_empty_trash)) and hasClickAction() and inDialog()).performClick()

            composeRule.awaitText(string(R.string.trash_empty_title), NETWORK_TIMEOUT_MS)
            assertEquals("the trash is empty on the server", emptyList<Long>(), trashIds(account))
        }
    }

    /** Each row reads as one item, and each control names the entry it acts on. */
    @Test
    fun everyRowAndControlHasATalkBackLabel() {
        val account = signInToNewAccount()
        val start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        account.createEntry(RESTORED, start, start.plus(90, ChronoUnit.MINUTES)).also(account::deleteEntry)
        account.createEntry("", start, start.plus(5, ChronoUnit.MINUTES)).also(account::deleteEntry)
        launchOnTrash {
            composeRule.awaitText(RESTORED, TIMEOUT_MS)
            val noDescription = string(R.string.trash_no_description)
            for (name in listOf(RESTORED, noDescription)) {
                val row = composeRule.onNode(hasText(name) and hasText(string(R.string.trash_no_project)), useUnmergedTree = false)
                row.assertExists()
                control(R.string.trash_restore_named, name).assertHasClickAction()
                control(R.string.trash_purge_named, name).assertHasClickAction()
            }
            composeRule.onNode(hasText("1:30:00")).assertExists()
            composeRule.onNode(hasText(string(R.string.trash_empty_trash)) and hasClickAction()).assertExists()
        }
    }

    @Test
    fun offlineThePageSaysItNeedsTheServer() {
        val account = signInToNewAccount()
        val start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        account.createEntry(RESTORED, start, start.plus(5, ChronoUnit.MINUTES)).also(account::deleteEntry)
        launchOnTrash {
            composeRule.awaitText(RESTORED, TIMEOUT_MS)

            AirplaneMode.set(true, account.client)

            composeRule.awaitText(string(R.string.trash_server_needed_title), NETWORK_TIMEOUT_MS)
            composeRule.awaitText(string(R.string.trash_server_needed_message), TIMEOUT_MS)
            assertFalse("no entry shows without the server", exists(hasText(RESTORED)))

            AirplaneMode.set(false, account.client)

            composeRule.awaitText(RESTORED, NETWORK_TIMEOUT_MS)
        }
    }

    /**
     * Once the capabilities say the server has no trash, the page goes back to Timer and the More
     * sheet leaves Trash out. Against Toggl Track or an older Tokkl the trash probe says so.
     */
    @Test
    fun withoutTheCapabilityThePageAndItsEntryPointHide() {
        signInToNewAccount()
        launchOnTrash {
            app.capabilities.trashMissing()

            composeRule.awaitTimerPage(TIMEOUT_MS)
            composeRule.onNode(bottomBarItem(R.string.nav_more)).performClick()
            composeRule.waitUntil("the More sheet opens", TIMEOUT_MS) { exists(hasText(string(R.string.nav_clients)) and isSelectable()) }
            assertFalse("the sheet leaves Trash out", exists(hasText(string(R.string.nav_trash)) and isSelectable()))
        }
    }

    /** Starts MainActivity, waits for the trash probe of its refresh, and opens Trash from the More sheet. */
    private fun launchOnTrash(steps: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
            composeRule.waitUntil("the refresh finds the trash", NETWORK_TIMEOUT_MS) { app.capabilities.state.value.trash }
            composeRule.onNode(bottomBarItem(R.string.nav_more)).performClick()
            val item = hasText(string(R.string.nav_trash)) and isSelectable()
            composeRule.waitUntil("the More sheet lists Trash", TIMEOUT_MS) { exists(item) }
            composeRule.onNode(item).performClick()
            composeRule.waitUntil("the trash page shows", TIMEOUT_MS) { exists(hasText(string(R.string.nav_trash)) and isHeading()) }
            steps()
        }
    }

    private fun signInToNewAccount(): TestAccounts.Account {
        val created = TestAccounts.create(timezone = "UTC").also { account = it }
        Session(context).save(TokklTestServer.url, created.apiToken)
        return created
    }

    private fun trashIds(account: TestAccounts.Account): List<Long> {
        val client = account.client
        return client.get(client.server.app("trash"), JsonArray.serializer()).orFail("GET /api/app/trash")
            .map { it.jsonObject.getValue("id").jsonPrimitive.long }
    }

    private fun control(@StringRes label: Int, name: String) =
        composeRule.onNode(hasContentDescription(context.getString(label, name)) and hasClickAction())

    private fun awaitGone(text: String) {
        composeRule.waitUntil("\"$text\" leaves the list", TIMEOUT_MS) { !exists(hasText(text)) }
    }

    private fun inDialog(): SemanticsMatcher = hasAnyAncestor(isDialog())

    private fun exists(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val RESTORED = "Trash restore"
        const val PURGED = "Trash purge"
        const val EMPTIED = "Trash empty one"
        const val ALSO_EMPTIED = "Trash empty two"
        const val TIMEOUT_MS = 10_000L
        const val NETWORK_TIMEOUT_MS = 30_000L
    }
}
