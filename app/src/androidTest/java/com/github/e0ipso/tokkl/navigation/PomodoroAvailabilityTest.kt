package com.github.e0ipso.tokkl.navigation

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklNavHost
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.trash.TrashViewModel
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Pomodoro destination follows the server's timer API in the production navigation host, in
 * the bottom bar and in the rail: without the capability it is not an item, and when the server
 * loses it while the page is open, the host returns to Timer. The capabilities are the test's own
 * flow, so no refresh can bring the flag back mid-test.
 */
class PomodoroAvailabilityTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val capabilities = MutableStateFlow(Capabilities.NONE.copy(trash = true, favorites = true, timerState = true))
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun closeTheHost() {
        scenario?.close()
    }

    @Test
    fun theBarOffersPomodoroOnlyWhileTheServerHasTheTimerApi() = exercise(width = 400)

    @Test
    fun theRailOffersPomodoroOnlyWhileTheServerHasTheTimerApi() = exercise(width = 700)

    private fun exercise(width: Int) {
        host(width)
        assertTrue("Pomodoro is an item with the timer API", exists(item()))
        compose.onNode(item()).performClick()
        compose.waitUntil("the Pomodoro page opens", TIMEOUT_MS) { exists(hasText(string(R.string.pomodoro_title)) and isHeading()) }
        assertTrue("the item is marked", exists(item() and isSelected()))

        // The server loses the timer API while the page is open.
        capabilities.value = capabilities.value.copy(timerState = false)
        compose.waitUntil("the item goes", TIMEOUT_MS) { !exists(item()) }
        compose.awaitTimerPage(TIMEOUT_MS)
        assertTrue("Timer is shown instead", !exists(hasText(string(R.string.pomodoro_title)) and isHeading()))

        // And it comes back when the server has it again, without opening on its own.
        capabilities.value = capabilities.value.copy(timerState = true)
        compose.waitUntil("the item returns", TIMEOUT_MS) { exists(item()) }
        compose.awaitTimerPage(TIMEOUT_MS)
    }

    @Test
    fun withoutTheTimerApiThereIsNoPomodoroItemAtAnyWidth() {
        capabilities.value = capabilities.value.copy(timerState = false)
        for (width in listOf(400, 700)) {
            host(width)
            assertTrue("no Pomodoro item at $width dp", !exists(item()))
            scenario!!.close()
        }
    }

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun item() = hasText(string(R.string.pomodoro_title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun string(id: Int) = context.getString(id)

    private fun host(width: Int) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            WindowCompat.enableEdgeToEdge(activity.window)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val models = ViewModelProvider(activity)
            val history = models[HistoryViewModel::class.java]
            val timer = models[TimerViewModel::class.java]
            activity.setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(DpSize(width.dp, 900.dp))) {
                    TokklTheme(darkTheme = false) {
                        // The override wraps the context, and the host takes view models from the activity.
                        CompositionLocalProvider(LocalContext provides activity) {
                        TokklNavHost(
                            signInModel = models[SignInViewModel::class.java].model,
                            themeSettings = app.themeSettings,
                            signOut = app.signOutModel,
                            sessionTrust = app.sessionTrust,
                            reports = models[ReportsViewModel::class.java].model,
                            history = history.model,
                            entryActions = history.actions,
                            calendar = models[CalendarViewModel::class.java].model,
                            projects = models[ProjectsViewModel::class.java].model,
                            clients = models[ClientsViewModel::class.java].model,
                            tags = models[TagsViewModel::class.java].model,
                            timerView = app.timerView,
                            timer = timer.model,
                            entryForm = models[EntryFormViewModel::class.java].model,
                            cache = app.cache,
                            trash = models[TrashViewModel::class.java].model,
                            capabilities = capabilities,
                            settings = app.accountSettings.state,
                            favorites = timer.favorites,
                        )
                        }
                    }
                }
            }
        }
        compose.awaitTimerPage(TIMEOUT_MS)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
