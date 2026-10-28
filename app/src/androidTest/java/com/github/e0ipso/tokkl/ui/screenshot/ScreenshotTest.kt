package com.github.e0ipso.tokkl.ui.screenshot

import android.app.UiModeManager
import android.os.Build
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedInFrame
import com.github.e0ipso.tokkl.SignedInPanes
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.calendar.CalendarContent
import com.github.e0ipso.tokkl.calendar.CalendarEntry
import com.github.e0ipso.tokkl.calendar.CalendarState
import com.github.e0ipso.tokkl.calendar.WeekSource
import com.github.e0ipso.tokkl.catalog.CatalogSamples
import com.github.e0ipso.tokkl.clients.ClientsActions
import com.github.e0ipso.tokkl.clients.ClientsContent
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.history.HistoryContent
import com.github.e0ipso.tokkl.history.HistoryDay
import com.github.e0ipso.tokkl.history.HistoryEntry
import com.github.e0ipso.tokkl.history.HistoryState
import com.github.e0ipso.tokkl.history.OlderDays
import com.github.e0ipso.tokkl.navigation.SampleEntryForm
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmContent
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmState
import com.github.e0ipso.tokkl.pomodoro.PomodoroContent
import com.github.e0ipso.tokkl.pomodoro.PomodoroLengths
import com.github.e0ipso.tokkl.pomodoro.PomodoroPageState
import com.github.e0ipso.tokkl.pomodoro.heldSession
import com.github.e0ipso.tokkl.projects.ProjectsActions
import com.github.e0ipso.tokkl.projects.ProjectsContent
import com.github.e0ipso.tokkl.reports.DetailedEntry
import com.github.e0ipso.tokkl.reports.DetailedReport
import com.github.e0ipso.tokkl.reports.FilterOptions
import com.github.e0ipso.tokkl.reports.RangePreset
import com.github.e0ipso.tokkl.reports.RangeSelection
import com.github.e0ipso.tokkl.reports.ReportEntry
import com.github.e0ipso.tokkl.reports.ReportFilters
import com.github.e0ipso.tokkl.reports.ReportRange
import com.github.e0ipso.tokkl.reports.ReportTab
import com.github.e0ipso.tokkl.reports.ReportsContent
import com.github.e0ipso.tokkl.reports.ReportsState
import com.github.e0ipso.tokkl.reports.SummaryReport
import com.github.e0ipso.tokkl.reports.WeeklyReport
import com.github.e0ipso.tokkl.reports.WeeklyRow
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.settings.ThemeMode
import com.github.e0ipso.tokkl.settings.ThemeSettings
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import com.github.e0ipso.tokkl.settings.applicationNightMode
import com.github.e0ipso.tokkl.settings.followTheSystemNightMode
import com.github.e0ipso.tokkl.settings.systemIsDark
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsContent
import com.github.e0ipso.tokkl.timer.TimerBarContent
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerState
import com.github.e0ipso.tokkl.timer.TimerTotals
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
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * Screenshots of every screen and trust prompt, compared with reference images by
 * [ReferenceImages]. The four classes below run them in the light and the dark theme, at font
 * scale 1.0 and at the largest, 2.0. Sign-in, Settings and Licences show in the real activity,
 * with the bottom bar. The Timer page shows above the same bar with a fixed history for a fixed
 * day, the reports screen's three tabs fixed reports for a fixed day, the Projects, Clients and
 * Tags pages fixed lists, and each prompt fixed text, so no capture depends on the date, the
 * server's data, its port or its certificate. A capture holds the app's window, the dialog's or
 * the More sheet's, never the status bar. Two captures force a wider window: the List view beside
 * the navigation rail on a medium one, and the entry form beside the history on an expanded one.
 *
 * The references are in app/src/androidTest/assets/screenshots, recorded on tokkl-api36, since
 * density and system fonts change the pixels. They run only on its Android version, API 36: every
 * other version draws text and shapes differently, so each comparison there would fail without
 * saying anything about the app. TKA-0255 authors the new scenarios only. After every backlog
 * implementation ticket is done, the final gate must record and review the changed navigation
 * frames and the new Pomodoro frames on tokkl-api36, then commit the references and compare again:
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
 * The kenkeep node practice-screenshot-references-on-tokkl-api36 has the commands.
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

    /** The List view, with the status above the bar for its entry the server has not received. */
    @SignedOut
    @Test
    fun timer() {
        show({
            SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}, syncStatus = SYNC_STATUS, syncStatusClock = TODAY) {
                TimerPage(TimerView.LIST)
            }
        }) {
            composeRule.awaitTimerPage()
            composeRule.awaitText(string(R.string.history_today).uppercase(Locale.ROOT))
            assertScreen("timer")
        }
    }

    /**
     * The Calendar view at noon on [TODAY], one day on this upright phone under the strip of its
     * week, with a running entry the server has not received, which the timer shows and marks as
     * not sent, and the bar marks on Timer.
     */
    @SignedOut
    @Test
    fun calendar() {
        show({
            SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}, timerRunning = true) {
                TimerPage(TimerView.CALENDAR)
            }
        }) {
            composeRule.awaitTimerPage()
            composeRule.awaitText("Thu, 15 Oct")
            assertScreen("calendar")
        }
    }

    /**
     * The List view beside the navigation rail, on a medium window, such as an unfolded foldable
     * held upright, with the status at the bottom beside the rail.
     */
    @SignedOut
    @Test
    fun timerBesideTheRail() {
        show({
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(MEDIUM_WINDOW)) {
                SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}, syncStatus = SYNC_STATUS, syncStatusClock = TODAY) {
                    TimerPage(TimerView.LIST)
                }
            }
        }) {
            composeRule.awaitTimerPage()
            composeRule.awaitText(string(R.string.history_today).uppercase(Locale.ROOT))
            assertScreen("timer-rail")
        }
    }

    /** On an expanded window, such as a tablet held sideways, the entry form beside the history. */
    @SignedOut
    @Test
    fun entryBesideTheHistory() {
        show({
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(EXPANDED_WINDOW)) {
                SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}) {
                    SignedInPanes(destination = Destination.ENTRY, entryBeside = true, entryKey = 0) { shown ->
                        if (shown == Destination.ENTRY) SampleEntryForm() else TimerPage(TimerView.LIST)
                    }
                }
            }
        }) {
            composeRule.awaitTimerPage()
            composeRule.awaitText(string(R.string.entry_form_edit))
            assertScreen("entry-beside")
        }
    }

    /** Explicit header frames keep both totals in the reference even when large text scrolls. */
    @SignedOut
    @Test
    fun timerTotals() = showTimerTotals(TimerView.LIST, "timer-totals")

    @SignedOut
    @Test
    fun calendarTotals() = showTimerTotals(TimerView.CALENDAR, "calendar-totals")

    /** The real start form, with fixed account defaults and no running session. */
    @SignedOut
    @Test
    fun pomodoroIdle() = showPomodoro(IDLE_POMODORO) {
        composeRule.awaitText(string(R.string.pomodoro_work_minutes))
        assertScreen("pomodoro-idle")
    }

    /** The bottom of the form keeps its entry controls and Start visible at font scale 2.0. */
    @SignedOut
    @Test
    fun pomodoroIdleActions() = showPomodoro(IDLE_POMODORO) {
        composeRule.onNode(hasText(string(R.string.pomodoro_start)) and hasClickAction())
            .performScrollTo().assertIsDisplayed().assertIsEnabled()
        assertScreen("pomodoro-idle-actions")
    }

    /** Work ended 65 seconds ago; the page and navigation still show a running Pomodoro. */
    @SignedOut
    @Test
    fun pomodoroOvertime() = showPomodoro(RUNNING_POMODORO) {
        composeRule.onNode(hasContentDescription(string(
            R.string.pomodoro_overtime_clock,
            POMODORO_ACCOUNT.formatDuration(65),
        ))).assertIsDisplayed()
        assertScreen("pomodoro-overtime")
    }

    @SignedOut
    @Test
    fun pomodoroOvertimeActions() = showPomodoro(RUNNING_POMODORO) {
        composeRule.onNode(hasText(string(R.string.pomodoro_end)) and hasClickAction())
            .performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeRule.onNode(hasText(string(R.string.pomodoro_start_short_rest)) and hasClickAction())
            .assertIsDisplayed().assertIsEnabled()
        assertScreen("pomodoro-overtime-actions")
    }

    /** The production alarm content fills the window without signed-in navigation. */
    @SignedOut
    @Test
    fun pomodoroAlarm() = showPomodoroAlarm {
        composeRule.onNode(hasContentDescription(string(R.string.pomodoro_overtime_clock, "1:05")))
            .assertIsDisplayed()
        assertScreen("pomodoro-alarm")
    }

    @SignedOut
    @Test
    fun pomodoroAlarmActions() = showPomodoroAlarm {
        composeRule.onNode(hasText(string(R.string.pomodoro_alarm_end)) and hasClickAction())
            .performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeRule.onNode(hasText(string(R.string.pomodoro_alarm_next)) and hasClickAction())
            .assertIsDisplayed().assertIsEnabled()
        assertScreen("pomodoro-alarm-actions")
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
        show({ ReportsTab(ReportsState.Loaded(RangeSelection(WEEK, RangePreset.THIS_WEEK), REPORT), ReportTab.SUMMARY) }) {
            composeRule.awaitText(string(R.string.reports_projects))
            assertScreen("reports")
        }
    }

    /** The Detailed tab: entries newest first with their day, times and durations, and more to load. */
    @SignedOut
    @Test
    fun detailedReport() {
        show({ ReportsTab(ReportsState.Loaded(RangeSelection(WEEK, RangePreset.THIS_WEEK), DETAILED), ReportTab.DETAILED) }) {
            composeRule.awaitText("Write the detailed report")
            assertScreen("reports-detailed")
        }
    }

    /** The Weekly tab: each project's time on each day of [WEEK], time without a project last. */
    @SignedOut
    @Test
    fun weeklyReport() {
        show({ ReportsTab(ReportsState.Loaded(RangeSelection(WEEK, RangePreset.THIS_WEEK), WEEKLY), ReportTab.WEEKLY) }) {
            composeRule.awaitText("Graffiti wall maintenance")
            assertScreen("reports-weekly")
        }
    }

    /** The Projects page above the bottom bar: clients, a billable project, a name that wraps and an archived project. */
    @SignedOut
    @Test
    fun projects() {
        show({
            SignedInFrame(current = TopLevelPage.PROJECTS, onOpen = {}) {
                ProjectsContent(CatalogSamples.PROJECTS_LOADED, offline = false, cachedProjects = emptyList(), clients = CatalogSamples.CACHED_CLIENTS, actions = ProjectsActions())
            }
        }) {
            composeRule.awaitText(CatalogSamples.PROJECTS.first().name)
            assertScreen("projects")
        }
    }

    /** The Clients page from the More sheet: a client's notes, project counts and an archived client. */
    @SignedOut
    @Test
    fun clients() {
        show({
            SignedInFrame(current = TopLevelPage.CLIENTS, onOpen = {}) {
                ClientsContent(CatalogSamples.CLIENTS_LOADED, offline = false, cachedClients = emptyList(), actions = ClientsActions())
            }
        }) {
            composeRule.awaitText(CatalogSamples.CLIENTS.first().name)
            assertScreen("clients")
        }
    }

    /** The Tags page from the More sheet, with a name long enough to wrap. */
    @SignedOut
    @Test
    fun tags() {
        show({
            SignedInFrame(current = TopLevelPage.TAGS, onOpen = {}) {
                TagsContent(CatalogSamples.TAGS_LOADED, offline = false, cachedTags = emptyList(), actions = TagsActions())
            }
        }) {
            composeRule.awaitText(CatalogSamples.TAGS.first().name)
            assertScreen("tags")
        }
    }

    /**
     * The bar's More sheet over the Tags page, which it marks. The sheet has a window of its own,
     * which a capture of a compose node leaves out, so this copies that window.
     */
    @SignedOut
    @Test
    fun moreSheet() {
        show({ SignedInFrame(current = TopLevelPage.TAGS, onOpen = {}) {} }) {
            composeRule.onNode(hasText(string(R.string.nav_more)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitUntil("the More sheet shows", TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(string(R.string.nav_more)) and isHeading()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { "PixelCopy copies a window from Android 14 on" }
            ReferenceImages.assertMatches("more-sheet-$variant", copyTheWindowOverTheActivity())
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

    private fun showPomodoro(state: PomodoroPageState, steps: () -> Unit) {
        show({
            SignedInFrame(current = TopLevelPage.POMODORO, onOpen = {}, pomodoroRunning = state.pomodoro != null) {
                PomodoroContent(
                    state = state,
                    onLengths = {}, onFields = {}, onDescription = {}, onDescriptionDone = {}, onStart = {}, onNext = {}, onEnd = {},
                    clock = TODAY,
                )
            }
        }) {
            composeRule.awaitText(string(R.string.pomodoro_title))
            steps()
        }
    }

    private fun showPomodoroAlarm(steps: () -> Unit) {
        show({
            PomodoroAlarmContent(PomodoroAlarmState(session = POMODORO_SESSION), onNext = {}, onEnd = {}, clock = TODAY)
        }) {
            composeRule.awaitText(string(R.string.pomodoro_alarm_title))
            steps()
        }
    }

    private fun showTimerTotals(view: TimerView, name: String) {
        show({
            SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}, timerRunning = view == TimerView.CALENDAR) {
                TimerPage(view)
            }
        }) {
            composeRule.awaitTimerPage()
            // The header scrolls in the Calendar view and at large text; otherwise it stays put.
            val scrolls = composeRule.onAllNodes(hasTestTag("timer_totals_week") and hasAnyAncestor(hasScrollAction()))
                .fetchSemanticsNodes().isNotEmpty()
            val week = composeRule.onNodeWithTag("timer_totals_week")
            if (scrolls) week.performScrollTo()
            week.assertIsDisplayed()
            composeRule.onNodeWithTag("timer_totals_today").assertIsDisplayed()
            assertScreen(name)
        }
    }

    /** Use the current header in every fixture, including the expanded entry/history frame. */
    @Composable
    private fun TimerPage(view: TimerView) {
        TimerScreen(
            view = view,
            onViewChange = {},
            timer = {
                if (view == TimerView.CALENDAR) Timer(RUNNING_TIMER, RUNNING_SECONDS)
                else Timer(IDLE_TIMER, 0)
            },
            listContent = { HistoryContent(state = HISTORY, onLoadOlder = {}) },
            calendarContent = {
                CalendarContent(state = CALENDAR, now = TODAY.instant(), onStep = {}, onSelect = {}, onToday = {}, onRetry = {})
            },
        )
    }

    /** The real controls and cache-derived totals, with both clocks fixed on [TODAY]. */
    @Composable
    private fun Timer(state: TimerState, elapsedSeconds: Long) {
        val entries = remember(state) {
            MutableStateFlow(if (state is TimerState.Running) CALENDAR_ROWS else HISTORY_ROWS)
        }
        val account = remember { MutableStateFlow<AccountSettings?>(ACCOUNT) }
        Column {
            TimerBarContent(
                state = state,
                elapsedSeconds = elapsedSeconds,
                onDescriptionChange = {},
                onDescriptionDone = {},
                onPick = {},
                onBillableChange = {},
                onStart = {},
                onStop = {},
                onAddEntry = {},
                picker = { _, _, _, _, _ -> },
            )
            TimerTotals(entries, account, clock = TODAY)
        }
    }

    /** The reports screen with [state] on [tab], on [TODAY]. */
    @Composable
    private fun ReportsTab(state: ReportsState, tab: ReportTab) {
        SignedInFrame(current = TopLevelPage.REPORTS, onOpen = {}) {
            ReportsContent(
                state = state,
                tab = tab,
                settings = ACCOUNT,
                time = ACCOUNT.time(TODAY),
                filters = ReportFilters(),
                filterOptions = FilterOptions.NONE,
                onTab = {},
                onShift = {},
                onPreset = {},
                onCustom = {},
                onFilters = {},
                onRetry = {},
                onLoadMore = {},
            )
        }
    }

    /**
     * The one window over the activity's, as PixelCopy copies it. captureToImage copies a node
     * from the activity's window unless the node is in a dialog, and a bottom sheet's window is
     * not one.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun copyTheWindowOverTheActivity(): ImageBitmap {
        var found: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = WindowInspector.getGlobalWindowViews().singleOrNull { root ->
                root.isShown && (root.layoutParams as? WindowManager.LayoutParams)?.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION
            }
        }
        val window = checkNotNull(found) { "no single window over the activity's" }
        val copied = CompletableFuture<PixelCopy.Result>()
        PixelCopy.request(PixelCopy.Request.Builder.ofWindow(window).build(), { it.run() }, copied::complete)
        val result = copied.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        check(result.status == PixelCopy.SUCCESS) { "PixelCopy failed with status ${result.status}" }
        return result.bitmap.asImageBitmap()
    }

    private fun assertScreen(name: String) {
        ReferenceImages.assertMatches("$name-$variant", composeRule.onRoot().captureToImage())
    }

    private fun assertDialog(name: String) {
        ReferenceImages.assertMatches("$name-$variant", composeRule.onNode(isDialog()).captureToImage())
    }

    private fun string(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun string(@StringRes id: Int, vararg arguments: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *arguments)

    internal companion object {
        const val TIMEOUT_MS = 5_000L

        /** An unfolded foldable or a small tablet held upright, a medium width. */
        val MEDIUM_WINDOW = DpSize(700.dp, 1000.dp)

        /** A tablet held sideways, an expanded width. */
        val EXPANDED_WINDOW = DpSize(1280.dp, 800.dp)

        val ACCOUNT = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /** One change not sent, the history's Code review, since the server answered at 11:52 on [TODAY]. */
        val SYNC_STATUS = SyncStatus(unsent = 1, lastAnswered = Instant.parse("2026-10-15T11:52:00Z"), settings = ACCOUNT)

        /** A Thursday at noon, in the middle of [WEEK]. */
        val TODAY: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

        val POMODORO_ACCOUNT = ACCOUNT.copy(defaultWorkspaceId = 1)

        val POMODORO_FIELDS = EntryFields(description = "Write screenshot scenarios", billable = true)

        val IDLE_POMODORO = PomodoroPageState(
            loading = false, account = POMODORO_ACCOUNT, fields = POMODORO_FIELDS,
            lengths = PomodoroLengths("25", "5", "15", "4"),
        )

        /** Default 25-minute work phase, fixed at 1:05 overtime on [TODAY]. No store or alarm runs. */
        val POMODORO_SESSION = PomodoroSession(
            workspaceId = 1, workMinutes = 25.0, shortRestMinutes = 5.0, longRestMinutes = 15.0, longRestEvery = 4.0,
            phaseKind = "work", phaseStartedAt = TODAY.instant().minusSeconds(25 * 60 + 65),
            pomodoroStartedAt = TODAY.instant().minusSeconds(25 * 60 + 65), completed = 0.0, fields = POMODORO_FIELDS,
        )

        /** The server's session, held at the start of the same phase and read at 1:05 overtime on [TODAY]. */
        val RUNNING_POMODORO = IDLE_POMODORO.copy(
            held = heldSession(phaseStart = TODAY.instant().minusSeconds(25 * 60 + 65), description = POMODORO_FIELDS.description),
        )

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

        /**
         * [WEEK] in the calendar with Thursday chosen: entries on four days, one across midnight
         * into Thursday, two that overlap, and three the server has not received, one of them
         * running. Write the history list has room for the unsent icon and words in the capture,
         * where Code review sits at its bottom edge.
         */
        val CALENDAR = CalendarState.Shown(
            selected = LocalDate.of(2026, 10, 15),
            weekStart = WEEK.from,
            today = LocalDate.of(2026, 10, 15),
            zone = ZoneOffset.UTC,
            settings = ACCOUNT,
            entries = listOf(
                calendarEntry(1, "Paint the mural", WALL, "2026-10-12T09:00", "2026-10-12T12:30"),
                calendarEntry(2, "Sprint planning", APP, "2026-10-13T08:30", "2026-10-13T12:00"),
                calendarEntry(3, "", project = null, "2026-10-14T23:00", "2026-10-15T01:00"),
                calendarEntry(4, "Stand-up", APP, "2026-10-15T08:00", "2026-10-15T08:30"),
                calendarEntry(5, "Write the history list", APP, "2026-10-15T09:00", "2026-10-15T10:30", task = "Design", unsent = true),
                calendarEntry(6, "Code review", WALL, "2026-10-15T10:45", "2026-10-15T11:50", unsent = true),
                calendarEntry(7, "Calendar grid", APP, "2026-10-15T11:15", stop = null, unsent = true),
            ),
            week = WeekSource.Cached,
        )

        /** Rows match each visible fixture, including the Calendar's single running entry. */
        val HISTORY_ROWS = HISTORY.days.flatMap { it.entries }.map { entry ->
            CachedTimeEntry(entry.id, 1, entry.projectId, entry.taskId, entry.description,
                entry.start, entry.stop, entry.billable, entry.tags, emptyList())
        }

        val CALENDAR_ROWS = CALENDAR.entries.map { entry ->
            CachedTimeEntry(entry.id, 1, null, null, entry.description,
                entry.start, entry.stop, false, emptyList(), emptyList())
        }

        /** The timer with nothing running, in the account's workspace. */
        val IDLE_TIMER = TimerState.Idle(EntryFields(), EntryLabels(), workspaceId = 1)

        /** The running entry of [CALENDAR], "Calendar grid" since 11:15, at noon on [TODAY], not sent. */
        val RUNNING_TIMER = TimerState.Running(
            entryId = 7,
            workspaceId = 1,
            start = Instant.parse("2026-10-15T11:15:00Z"),
            fields = EntryFields(description = "Calendar grid", projectId = APP.id),
            labels = EntryLabels(project = APP.name, projectColor = APP.colour),
            original = Picked(projectId = APP.id),
            unsent = true,
        )

        /** From 11:15 to noon. */
        const val RUNNING_SECONDS = 45 * 60L

        /** A page of [WEEK]'s entries, newest first, one across midnight, with a next page to load. */
        val DETAILED = DetailedReport(
            entries = listOf(
                detailedEntry(6, "Write the detailed report", APP, task = "Reports", 15, "09:00", 15, "11:00"),
                detailedEntry(5, "", project = null, task = null, 15, "08:15", 15, "09:00"),
                detailedEntry(4, "Paint the north side", WALL, task = null, 14, "13:30", 14, "17:30"),
                detailedEntry(3, "Code review", APP, task = null, 13, "23:30", 14, "00:45"),
                detailedEntry(2, "Sketches", WALL, task = null, 13, "10:00", 13, "11:15"),
                detailedEntry(1, "Plan the week", APP, task = "Design", 12, "09:00", 12, "12:00"),
            ),
            nextRow = 51,
        )

        /** [WEEK] by project, with a long project name and time without a project. */
        val WEEKLY: WeeklyReport = WeeklyReport.of(
            WEEK.from,
            listOf(
                WeeklyRow(APP.id, APP.name, APP.colour, listOf(10_800L, 9_000, 14_400, 7_200, 0, 0, 0)),
                WeeklyRow(WALL.id, "Graffiti wall maintenance", WALL.colour, listOf(0L, 4_500, 0, 7_200, 0, 0, 0)),
                WeeklyRow(null, "", null, listOf(0L, 0, 0, 2_700, 0, 0, 0)),
            ),
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

        /** An entry of [CALENDAR] from [start] to [stop] in UTC, running when [stop] is null. */
        private fun calendarEntry(
            id: Long,
            description: String,
            project: Project?,
            start: String,
            stop: String?,
            task: String? = null,
            unsent: Boolean = false,
        ) = CalendarEntry(
            id = id,
            description = description,
            project = project?.name,
            projectColor = project?.colour,
            task = task,
            start = Instant.parse("$start:00Z"),
            stop = stop?.let { Instant.parse("$it:00Z") },
            unsent = unsent,
        )

        /** An entry of [DETAILED] from October [startDay] at [start] to [stopDay] at [stop], in UTC. */
        private fun detailedEntry(
            id: Long,
            description: String,
            project: Project?,
            task: String?,
            startDay: Int,
            start: String,
            stopDay: Int,
            stop: String,
        ): DetailedEntry {
            val from = ZonedDateTime.parse("2026-10-${startDay}T$start:00Z")
            val to = ZonedDateTime.parse("2026-10-${stopDay}T$stop:00Z")
            return DetailedEntry(id, description, project?.name, project?.colour, task, from, to, to.toEpochSecond() - from.toEpochSecond())
        }

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

    internal class Project(val id: Long, val name: String, val colour: String)
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
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.applicationNightMode)
    }

    override fun after() {
        ThemeSettingsStore(context).clear()
        followTheSystemNightMode()
    }
}

// The references come from API 36 on tokkl-api36, so each class runs only there. The test runner
// reads SdkSuppress from the class it runs, not from ScreenshotTest. An assumption would not do:
// the Android Gradle plugin reports a failed assumption as a failed test.

@SdkSuppress(minSdkVersion = REFERENCE_SDK, maxSdkVersion = REFERENCE_SDK)
class LightScreenshotTest : ScreenshotTest(dark = false, fontScale = 1f)

@SdkSuppress(minSdkVersion = REFERENCE_SDK, maxSdkVersion = REFERENCE_SDK)
class DarkScreenshotTest : ScreenshotTest(dark = true, fontScale = 1f)

@SdkSuppress(minSdkVersion = REFERENCE_SDK, maxSdkVersion = REFERENCE_SDK)
class LightLargestFontScreenshotTest : ScreenshotTest(dark = false, fontScale = SystemFontScale.LARGEST)

@SdkSuppress(minSdkVersion = REFERENCE_SDK, maxSdkVersion = REFERENCE_SDK)
class DarkLargestFontScreenshotTest : ScreenshotTest(dark = true, fontScale = SystemFontScale.LARGEST)

/** The Android version of tokkl-api36, where the references were recorded. */
private const val REFERENCE_SDK = Build.VERSION_CODES.BAKLAVA
