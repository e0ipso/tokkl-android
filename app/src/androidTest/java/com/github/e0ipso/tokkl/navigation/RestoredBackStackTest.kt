package com.github.e0ipso.tokkl.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.Session
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * A back stack restored from saved state shows the page it held only while somebody is signed
 * in. The session can end without the navigation starting over, as when a sign-out finishes in
 * the background just before the process dies, or when the token stops decrypting. Recreating the
 * activity restores the stack the same way a new process does.
 */
class RestoredBackStackTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun signOut() {
        SessionRule.signOut()
    }

    @Test
    fun aStackRestoredWithoutASessionOpensSignIn() {
        onSettings { scenario ->
            Session(context).clear()

            scenario.recreate()

            composeRule.awaitText(string(R.string.sign_in_title), TIMEOUT_MS)
            assertFalse("the bottom bar went with the session", exists(bottomBarItem(R.string.nav_timer)))
            assertFalse("Settings went with the session", exists(heading(R.string.settings)))
        }
    }

    @Test
    fun aStackRestoredWithASessionShowsThePageItHeld() {
        onSettings { scenario ->
            scenario.recreate()

            composeRule.waitUntil("Settings shows again", TIMEOUT_MS) { exists(heading(R.string.settings)) }
            composeRule.onNode(bottomBarItem(R.string.settings)).assertIsSelected()
            assertFalse("sign-in stays away", exists(hasText(string(R.string.sign_in_title))))
        }
    }

    /** Starts MainActivity signed in and runs [steps] on Settings. */
    private fun onSettings(steps: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
            composeRule.openBarPage(R.string.settings)
            composeRule.waitUntil("Settings shows", TIMEOUT_MS) { exists(heading(R.string.settings)) }
            steps(scenario)
        }
    }

    private fun heading(@StringRes label: Int): SemanticsMatcher = hasText(string(label)) and isHeading()

    private fun exists(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val NETWORK_TIMEOUT_MS = 30_000L
    }
}
