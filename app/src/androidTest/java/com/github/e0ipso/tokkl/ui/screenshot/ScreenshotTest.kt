package com.github.e0ipso.tokkl.ui.screenshot

import android.app.UiModeManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SignedInFrame
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.history.HistoryContent
import com.github.e0ipso.tokkl.history.HistoryDay
import com.github.e0ipso.tokkl.history.HistoryEntry
import com.github.e0ipso.tokkl.history.HistoryState
import com.github.e0ipso.tokkl.history.OlderDays
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.reports.RangePreset
import com.github.e0ipso.tokkl.reports.RangeSelection
import com.github.e0ipso.tokkl.reports.ReportEntry
import com.github.e0ipso.tokkl.reports.ReportRange
import com.github.e0ipso.tokkl.reports.ReportsContent
import com.github.e0ipso.tokkl.reports.ReportsState
import com.github.e0ipso.tokkl.reports.SummaryReport
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.settings.ThemeMode
import com.github.e0ipso.tokkl.settings.ThemeSettings
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import com.github.e0ipso.tokkl.settings.applicationNightMode
import com.github.e0ipso.tokkl.settings.followTheSystemNightMode
import com.github.e0ipso.tokkl.settings.systemIsDark
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.trust.CertificatePrompt
import com.github.e0ipso.tokkl.trust.CleartextWarning
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * Screenshots of every screen and trust prompt, compared with reference images by
 * [ReferenceImages]. The four classes below run them in the light and the dark theme, at font
 * scale 1.0 and at the largest, 2.0. Sign-in, Settings and Licences show in the real activity,
 * with the bottom bar. The Timer page shows above the same bar with a fixed history for a fixed
 * day, the reports screen a fixed summary for a fixed day, and each prompt fixed text, so no
 * capture depends on the date, the server's data, its port or its certificate. A capture holds
 * the app's window or the dialog's, never the status bar.
 *
 * The references are in app/src/androidTest/assets/screenshots, recorded on tokkl-api36, since
 * density and system fonts change the pixels. A change to how a screen looks updates its
 * references in the same commit:
 *
 * 1. Run the four classes, which share this package, through scripts/instrumented-tests.sh with
 *    `-Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.ui.screenshot`.
 * 2. Each capture that differs from its reference, or has none, fails its test. The capture
 *    lands in `actual/` and the diff in `diff/`, under
 *    `app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<device>/screenshots/`.
 * 3. Look at every diff and capture. Copy the captures that show the intended change into
 *    app/src/androidTest/assets/screenshots, keeping their names.
 * 4. Run step 1 again. It has to pass.
 *
 * README.md, "Screenshot tests", has the commands.
 */
abstract class ScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    private val composeRule = createEmptyComposeRule()

    /** Session, theme and font scale are set before an activity starts, so it never has to restart. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(ThemeRule(dark))
        .around(SystemFontScale(fontScale))
        .around(composeRule)

    /** Such as `light-100`, the end of each reference's name. */
    private val variant = "${if (dark) "dark" else "light"}-${(fontScale * 100).roundToInt()}"

    @SignedOut
    @Test
    fun signInWithApiToken() {
        launchApp {
            composeRule.awaitText(string(R.string.sign_in_server_url))
            assertScreen("sign-in-api-token")
        }
    }

    @SignedOut
    @Test
    fun signInWithEmailAndPassword() {
        launchApp {
            composeRule.awaitText(string(R.string.sign_in_server_url))
            // A click through semantics, not a touch, so no ripple shows in the capture.
            composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable())
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.awaitText(string(R.string.sign_in_password))
            assertScreen("sign-in-email-password")
        }
    }

    @SignedOut
    @Test
    fun timer() {
        show({
            SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}) {
                TimerScreen(
                    view = TimerView.LIST,
                    onViewChange = {},
                    listContent = { HistoryContent(state = HISTORY, onLoadOlder = {}) },
                )
            }
        }) {
            composeRule.awaitTimerPage()
            composeRule.awaitText(string(R.string.history_today).uppercase(Locale.ROOT))
            assertScreen("timer")
        }
    }

    @Test
    fun settings() {
        launchApp {
            openSettings()
            assertScreen("settings")
        }
    }

    @Test
    fun licences() {
        launchApp {
            openSettings()
            composeRule.onNode(hasText(string(R.string.licences)) and hasClickAction())
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.awaitText(string(R.string.licence_anybody_title))
            assertScreen("licences")
        }
    }

    @SignedOut
    @Test
    fun reports() {
        show({
            ReportsContent(
                state = ReportsState.Loaded(RangeSelection(WEEK, RangePreset.THIS_WEEK), REPORT),
                settings = ACCOUNT,
                time = ACCOUNT.time(TODAY),
                onShift = {},
                onPreset = {},
                onCustom = {},
                onRetry = {},
            )
        }) {
            composeRule.awaitText(string(R.string.reports_projects))
            assertScreen("reports")
        }
    }

    @SignedOut
    @Test
    fun httpWarning() {
        show({ CleartextWarning(HTTP_SERVER, onConfirm = {}, onCancel = {}) }) {
            composeRule.awaitText(string(R.string.sign_in_http_warning_title))
            assertDialog("http-warning")
        }
    }

    @SignedOut
    @Test
    fun certificatePrompt() {
        show({ CertificatePrompt(CERTIFICATE, changed = false, onTrust = {}, onCancel = {}) }) {
            composeRule.awaitText(string(R.string.sign_in_certificate_title))
            assertDialog("certificate-prompt")
        }
    }

    /**
     * Opens Settings from the bottom bar with a click through semantics, not a touch, since the
     * bar stays in the capture and a touch's ripple would show on it.
     */
    private fun openSettings() {
        composeRule.awaitTimerPage()
        composeRule.onNode(hasText(string(R.string.settings)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText(string(R.string.settings_theme))
    }

    /** Starts the app and runs [steps] once it shows in this class's theme. */
    private fun launchApp(steps: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitUntil("the app runs in the ${if (dark) "dark" else "light"} night mode", TIMEOUT_MS) {
                var shown = !dark
                scenario.onActivity { shown = systemIsDark(it) }
                shown == dark
            }
            steps()
        }
    }

    /**
     * Shows [content] in this class's theme in an activity of its own, drawn edge to edge as
     * MainActivity draws, and runs [steps].
     */
    private fun show(content: @Composable () -> Unit, steps: () -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.enableEdgeToEdge(activity.window)
                activity.setContent { TokklTheme(darkTheme = dark) { content() } }
            }
            steps()
        }
    }

    private fun assertScreen(name: String) {
        ReferenceImages.assertMatches("$name-$variant", composeRule.onRoot().captureToImage())
    }

    private fun assertDialog(name: String) {
        ReferenceImages.assertMatches("$name-$variant", composeRule.onNode(isDialog()).captureToImage())
    }

    private fun string(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        const val TIMEOUT_MS = 5_000L

        val ACCOUNT = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /** A Thursday at noon, in the middle of [WEEK]. */
        val TODAY: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

        val WEEK = ReportRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18))

        val APP = Project(1, "Tokkl for Android", "#0b83d9")
        val WALL = Project(2, "Graffiti wall", "#d94182")

        /** Two projects, time without a project, billable and non-billable time, and one currency. */
        val REPORT: SummaryReport = SummaryReport.of(
            listOf(
                entry(day = 12, hours = 3.0, billable = true, project = APP),
                entry(day = 13, hours = 2.5, billable = true, project = APP),
                entry(day = 13, hours = 1.25, billable = false, project = WALL),
                entry(day = 14, hours = 4.0, billable = true, project = APP),
                entry(day = 15, hours = 0.75, billable = false, project = null),
                entry(day = 15, hours = 2.0, billable = false, project = WALL),
            ),
            WEEK,
            DayOfWeek.MONDAY,
        )

        /**
         * Three days of history on [TODAY]: projects with and without a task, an entry without a
         * description or project, a billable one, tags, and one the server has not received.
         */
        val HISTORY = HistoryState.Shown(
            days = listOf(
                HistoryDay(
                    LocalDate.of(2026, 10, 15),
                    listOf(
                        historyEntry(1, "Code review", WALL, task = null, "10:45", "11:50", tags = listOf("review"), unsent = true),
                        historyEntry(2, "Write the history list", APP, task = "Design", "09:00", "10:30", billable = true),
                    ),
                ),
                HistoryDay(
                    LocalDate.of(2026, 10, 14),
                    listOf(
                        historyEntry(3, "", project = null, task = null, "14:00", "17:15", day = 14),
                        historyEntry(4, "Sprint planning", APP, task = null, "08:30", "12:00", day = 14),
                    ),
                ),
                HistoryDay(
                    LocalDate.of(2026, 10, 12),
                    listOf(historyEntry(5, "Paint the mural", WALL, task = null, "09:00", "12:30", day = 12, billable = true)),
                ),
            ),
            today = LocalDate.of(2026, 10, 15),
            zone = ZoneOffset.UTC,
            settings = ACCOUNT,
            older = OlderDays.End,
        )

        val HTTP_SERVER: ServerUrl = checkNotNull(ServerUrl.parse("http://10.0.2.2:8080"))

        val CERTIFICATE = ServerCertificate(
            host = "10.0.2.2",
            port = 8443,
            sha256 = "4f1d7c2a9b3e8d6f0a5c1e7b2d9f4a6c8e3b0d5f7a2c9e1b4d6f8a0c3e5b7d9f",
            subject = "CN=Tokkl instrumented tests",
        )

        /** A stopped entry of [HISTORY] on October [day], from [start] to [stop] in UTC. */
        private fun historyEntry(
            id: Long,
            description: String,
            project: Project?,
            task: String?,
            start: String,
            stop: String,
            day: Int = 15,
            tags: List<String> = emptyList(),
            billable: Boolean = false,
            unsent: Boolean = false,
        ) = HistoryEntry(
            id = id,
            description = description,
            project = project?.name,
            projectColor = project?.colour,
            task = task,
            tags = tags,
            billable = billable,
            start = Instant.parse("2026-10-${day}T$start:00Z"),
            stop = Instant.parse("2026-10-${day}T$stop:00Z"),
            unsent = unsent,
        )

        /** An entry in [WEEK], billed at 80 EUR an hour when [billable]. */
        private fun entry(day: Int, hours: Double, billable: Boolean, project: Project?): ReportEntry {
            val seconds = (hours * 3600).toLong()
            return ReportEntry(
                seconds = seconds,
                billable = billable,
                amountCents = if (billable) seconds * 8_000 / 3600 else null,
                currency = "EUR",
                projectId = project?.id,
                projectName = project?.name,
                projectColor = project?.colour,
                date = LocalDate.of(2026, 10, day),
            )
        }
    }

    private class Project(val id: Long, val name: String, val colour: String)
}

/**
 * Stores the light or dark theme setting and gives Android the matching night mode before the
 * app starts, so the app never changes it mid-test. Puts both back to following the system after.
 */
private class ThemeRule(private val dark: Boolean) : ExternalResource() {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    override fun before() {
        val mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
        ThemeSettingsStore(context).save(ThemeSettings(mode = mode))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.applicationNightMode)
        }
    }

    override fun after() {
        ThemeSettingsStore(context).clear()
        followTheSystemNightMode()
    }
}

class LightScreenshotTest : ScreenshotTest(dark = false, fontScale = 1f)

class DarkScreenshotTest : ScreenshotTest(dark = true, fontScale = 1f)

class LightLargestFontScreenshotTest : ScreenshotTest(dark = false, fontScale = SystemFontScale.LARGEST)

class DarkLargestFontScreenshotTest : ScreenshotTest(dark = true, fontScale = SystemFontScale.LARGEST)
