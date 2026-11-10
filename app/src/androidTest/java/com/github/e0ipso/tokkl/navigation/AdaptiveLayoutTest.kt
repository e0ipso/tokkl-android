package com.github.e0ipso.tokkl.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.appConfiguredServer
import com.github.e0ipso.tokkl.appHttp
import com.github.e0ipso.tokkl.calendar.CalendarContent
import com.github.e0ipso.tokkl.catalog.CatalogSamples
import com.github.e0ipso.tokkl.clients.ClientsActions
import com.github.e0ipso.tokkl.clients.ClientsOverlay
import com.github.e0ipso.tokkl.clients.ClientsPageState
import com.github.e0ipso.tokkl.clients.ClientsRoute
import com.github.e0ipso.tokkl.clients.ClientsScreen
import com.github.e0ipso.tokkl.entry.EntryFormActions
import com.github.e0ipso.tokkl.entry.EntryFormContent
import com.github.e0ipso.tokkl.entry.EntryFormState
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.history.HistoryContent
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.navhost.SignedInFrame
import com.github.e0ipso.tokkl.navhost.SignedInPanes
import com.github.e0ipso.tokkl.navhost.runningBadges
import com.github.e0ipso.tokkl.navhost.showsEntryBeside
import com.github.e0ipso.tokkl.navhost.syncBand
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.projects.ProjectForm
import com.github.e0ipso.tokkl.projects.ProjectsActions
import com.github.e0ipso.tokkl.projects.ProjectsPageState
import com.github.e0ipso.tokkl.projects.ProjectsRoute
import com.github.e0ipso.tokkl.projects.ProjectsScreen
import com.github.e0ipso.tokkl.reports.FilterOptions
import com.github.e0ipso.tokkl.reports.RangePreset
import com.github.e0ipso.tokkl.reports.RangeSelection
import com.github.e0ipso.tokkl.reports.ReportFilters
import com.github.e0ipso.tokkl.reports.ReportTab
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.reports.ReportsScreen
import com.github.e0ipso.tokkl.reports.ReportsState
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.settings.SettingsContent
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.settings.SignOutConfirmation
import com.github.e0ipso.tokkl.signin.ServerSignIn
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.signin.SignInScreen
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsPageState
import com.github.e0ipso.tokkl.tags.TagsRoute
import com.github.e0ipso.tokkl.tags.TagsScreen
import com.github.e0ipso.tokkl.timer.TimerBarContent
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerState
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.trash.DeletedEntry
import com.github.e0ipso.tokkl.trash.TrashActions
import com.github.e0ipso.tokkl.trash.TrashPageState
import com.github.e0ipso.tokkl.trash.TrashRoute
import com.github.e0ipso.tokkl.trash.TrashScreen
import com.github.e0ipso.tokkl.trash.TrashState
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.screenshot.ScreenshotTest
import com.github.e0ipso.tokkl.ui.theme.ThemeSettings
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import com.github.e0ipso.tokkl.core.ui.R as UiR
import com.github.e0ipso.tokkl.feature.pomodoro.R as PomodoroR
import com.github.e0ipso.tokkl.feature.projects.R as ProjectsR
import com.github.e0ipso.tokkl.feature.settings.R as SettingsR
import com.github.e0ipso.tokkl.feature.timer.R as TimerR

/**
 * Every screen at one window size, [window], of the width class [width] (docs/PRD.md, Devices):
 * the bottom bar on a compact window and the navigation rail on wider ones, with the same five
 * items, and every screen with no control or text cut off at its sides or past the window's. On an
 * expanded window, the entry form shows beside the Timer page's history; elsewhere it shows alone.
 * The window is forced with [DeviceConfigurationOverride.WindowSize], which scales the density
 * down when the window is larger than the phone's, so the tests compare positions, not sizes.
 */
abstract class AdaptiveLayoutTest(private val window: DpSize, private val width: WindowWidth) {
    /** A window larger than the phone's is drawn at a smaller density; see [createComposeRule]. */
    @get:Rule
    val composeRule = createComposeRule(
        scaledSizes = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.let {
            window.width.value > it.screenWidthDp || window.height.value > it.screenHeightDp
        },
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Pixels per dp in the forced window. */
    private var density = 1f

    /** Whether the test drew a window; one that does not apply to [width] draws none. */
    private var shown = false

    @After
    fun cancelTheScope() {
        try {
            if (shown) composeRule.checkAccessibility()
        } finally {
            scope.cancel()
        }
    }

    /** Both navigation layouts expose independent, changing spoken running marks. */
    @Test
    fun thePomodoroAndTimerMarksFollowTheirOwnStateInBarAndRail() {
        var timerRunning by mutableStateOf(false)
        var sessionRunning by mutableStateOf(true)
        show {
            SignedInFrame(
                pages = topLevelPages,
                current = ProjectsRoute,
                onOpen = {},
                badges = runningBadges(timer = timerRunning, pomodoro = sessionRunning),
            ) {
                Box(Modifier.fillMaxSize())
            }
        }
        fun marked(label: Int, state: Int) = navigationItem(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(state))
        fun count(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size
        val pomodoro = marked(UiR.string.pomodoro_title, PomodoroR.string.nav_pomodoro_running)
        val timer = marked(TimerR.string.nav_timer, TimerR.string.nav_timer_running)
        assertEquals(1, count(pomodoro))
        assertEquals(0, count(timer))
        composeRule.runOnIdle { timerRunning = true }
        assertEquals(1, count(pomodoro))
        assertEquals(1, count(timer))
        composeRule.runOnIdle { sessionRunning = false }
        assertEquals(0, count(pomodoro))
        assertEquals(1, count(timer))
        composeRule.runOnIdle { timerRunning = false }
        assertEquals(0, count(timer))
    }

    /** Compact windows put the five items in a row at the bottom, wider ones in a column at the start. */
    @Test
    fun theNavigationFollowsTheWidth() {
        show {
            SignedInFrame(pages = topLevelPages, current = Route.Timer, onOpen = {}) { Box(Modifier.fillMaxSize()) }
        }

        val windowBounds = windowBounds()
        val items = navigationItems()
        assertEquals(
            "the items: ${items.map { it.label() }}",
            NAVIGATION_LABELS.map(::string),
            items.map { it.label() },
        )
        for (item in items) assertShownWhole("the ${item.label()} item", item)
        val bounds = items.map { it.boundsInRoot }
        if (width == WindowWidth.COMPACT) {
            for ((before, after) in bounds.zipWithNext()) {
                assertEquals("the bar's items share a top", before.top, after.top, TOLERANCE_PX)
                assertTrue(
                    "the bar's items run from the start: $before, then $after",
                    before.right <= after.left + TOLERANCE_PX,
                )
            }
            assertTrue(
                "the bar sits at the bottom: ${bounds.first()} in $windowBounds",
                bounds.first().bottom >= windowBounds.bottom - windowBounds.height / 4,
            )
        } else {
            for ((before, after) in bounds.zipWithNext()) {
                assertEquals("the rail's items share a centre", before.center.x, after.center.x, TOLERANCE_PX)
                assertTrue(
                    "the rail's items run from the top: $before, then $after",
                    before.bottom <= after.top + TOLERANCE_PX,
                )
            }
            assertTrue(
                "the rail sits at the start: ${bounds.first()} in $windowBounds",
                bounds.first().right <= windowBounds.left + windowBounds.width / 4,
            )
        }
    }

    /** On a window too short for every item at the largest font scale, the rail scrolls to each of them whole. */
    @Test
    fun theRailScrollsWhereTheWindowIsShort() {
        if (width == WindowWidth.COMPACT) return
        show(size = DpSize(window.width, SHORT_HEIGHT), fontScale = LARGEST_FONT_SCALE) {
            SignedInFrame(pages = topLevelPages, current = Route.Timer, onOpen = {}) { Box(Modifier.fillMaxSize()) }
        }

        for (label in NAVIGATION_LABELS) {
            val item = composeRule.onNode(navigationItem(label))
            item.performScrollTo()
            composeRule.waitForIdle()
            assertShownWhole("the ${string(label)} item", item.fetchSemanticsNode())
        }
    }

    @Test
    fun everyScreenFits() {
        var screen by mutableStateOf(screens.first())
        show { screen.content(this) }

        for (next in screens) {
            composeRule.runOnIdle { screen = next }
            composeRule.waitForIdle()
            assertNothingCutOffAtTheSides(next.name)
        }
    }

    /** On an expanded window the form shares the width with the history, which stays in sight. */
    @Test
    fun theEntryFormShowsBesideTheHistoryOnlyWhenExpanded() {
        val formRoute = Route.Entry(EntryOpening.Edit(EntryId(1)), id = "form")
        val beside = showsEntryBeside(width, listView = true, listOf(Route.Timer, formRoute))
        assertEquals(width == WindowWidth.EXPANDED, beside)
        show {
            SignedInFrame(pages = topLevelPages, current = Route.Timer, onOpen = {}) {
                SignedInPanes(top = formRoute, entryBeside = beside) { shown ->
                    when (shown) {
                        Route.Timer -> SampleTimerPage(TimerView.LIST)
                        is Route.Entry -> SampleEntryForm()
                        else -> error("$shown is not on this stack")
                    }
                }
            }
        }

        val form = composeRule.onNode(
            hasText(string(TimerR.string.entry_form_edit)) and isHeading(),
        ).fetchSemanticsNode()
        val history = composeRule.onAllNodes(hasText(HISTORY_ENTRY), useUnmergedTree = true).fetchSemanticsNodes()
        if (beside) {
            assertTrue("the history shows beside the form", history.isNotEmpty())
            val historyRight = history.maxOf { it.boundsInRoot.right }
            assertTrue(
                "the history ends where the form starts: $historyRight, ${form.boundsInRoot}",
                historyRight <= form.boundsInRoot.left,
            )
        } else {
            assertTrue("the form shows alone", history.isEmpty())
        }
        assertNothingCutOffAtTheSides("the entry form ${if (beside) "beside the history" else "alone"}")
    }

    /**
     * On an expanded window the Calendar view, the timer and the switch included, grows past a phone's column; the List
     * view does not.
     */
    @Test
    fun theCalendarTakesTheExtraWidthOnlyWhenExpanded() {
        var view by mutableStateOf(TimerView.CALENDAR)
        show { Framed(Route.Timer) { SampleTimerPage(view) } }

        val calendar = switchWidth()
        composeRule.runOnIdle { view = TimerView.LIST }
        composeRule.waitForIdle()
        val list = switchWidth()

        val column = TokklSpacing.contentMaxWidth.value
        assertTrue("the List view keeps a phone's column: $list dp", list <= column + TOLERANCE_DP)
        if (width == WindowWidth.EXPANDED) {
            assertTrue("the Calendar view grows past $column dp: $calendar dp", calendar > column + TOLERANCE_DP)
        } else {
            assertEquals("the Calendar view keeps the List view's column", list, calendar, TOLERANCE_DP)
        }
    }

    /** The width of the List and Calendar switch, which spans the page's column, in the window's dp. */
    private fun switchWidth(): Float {
        val segments = listOf(TimerR.string.timer_view_list, TimerR.string.timer_view_calendar).map { label ->
            composeRule.onNode(hasText(string(label)) and isSelectable()).fetchSemanticsNode().boundsInRoot
        }
        return (segments.maxOf { it.right } - segments.minOf { it.left }) / density
    }

    /** Shows [content] in Tokkl's theme in a window of [size], at [fontScale]. */
    private fun show(size: DpSize = window, fontScale: Float? = null, content: @Composable ScreenScope.() -> Unit) {
        val override = DeviceConfigurationOverride.WindowSize(size).let { sized ->
            fontScale?.let { DeviceConfigurationOverride.FontScale(it) then sized } ?: sized
        }
        val screenScope = ScreenScope()
        shown = true
        composeRule.setContent {
            DeviceConfigurationOverride(override) {
                density = LocalDensity.current.density
                Box(Modifier.testTag(WINDOW)) { TokklTheme(darkTheme = false) { screenScope.content() } }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Fails for a control or a text that is cut off at its sides, or that reaches past the window's
     * sides, scrolled into view or not. A row that scrolls sideways may cut off what it holds.
     */
    private fun assertNothingCutOffAtTheSides(screen: String) {
        val windowBounds = windowBounds()
        val nodes = composeRule.onAllNodes(CONTROL_OR_TEXT, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$screen shows something", nodes.isNotEmpty())
        for (node in nodes) {
            if (node.insideSidewaysScroll()) continue
            val whole = Rect(node.positionInRoot, node.size.toSize())
            assertTrue(
                "$screen: ${node.describe()} stays inside the window's sides: $whole in $windowBounds",
                whole.left >= windowBounds.left - TOLERANCE_PX && whole.right <= windowBounds.right + TOLERANCE_PX,
            )
            val shown = node.boundsInRoot
            if (shown.height > 0f) {
                assertEquals(
                    "$screen: ${node.describe()} shows its whole width",
                    whole.width,
                    shown.width,
                    TOLERANCE_PX,
                )
            }
        }
    }

    private fun assertShownWhole(what: String, node: SemanticsNode) {
        val shown = node.boundsInRoot
        assertTrue(
            "$what shows whole: $shown of ${node.size}",
            abs(shown.width - node.size.width) <= TOLERANCE_PX && abs(shown.height - node.size.height) <= TOLERANCE_PX,
        )
    }

    private fun windowBounds(): Rect = composeRule.onNodeWithTag(WINDOW).fetchSemanticsNode().boundsInRoot

    private fun navigationItems(): List<SemanticsNode> =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))

    private fun navigationItem(label: Int): SemanticsMatcher =
        hasText(string(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun SemanticsNode.label(): String = (
        config.getOrNull(
            SemanticsProperties.Text,
        ) ?: children.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        )
        .joinToString { it.text }

    private fun SemanticsNode.describe(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        return "\"${text ?: description ?: "a node"}\""
    }

    private fun SemanticsNode.insideSidewaysScroll(): Boolean =
        generateSequence(parent) { it.parent }.any { SemanticsProperties.HorizontalScrollAxisRange in it.config }

    private fun string(id: Int): String = context.getString(id)

    /** What [everyScreenFits] draws: [ScreenScope]'s screens, by name. */
    private class Screen(val name: String, val content: @Composable ScreenScope.() -> Unit)

    /** The screens, each with fixed data, inside the signed-in frame where they have one. */
    private inner class ScreenScope {
        private val signInModel by lazy {
            SignInViewModel(
                signIn = ServerSignIn(
                    storeSignIn = { _, _, _ -> },
                    connector = appHttp,
                    configured = appConfiguredServer,
                    scope = scope,
                    io = Dispatchers.IO,
                    signedInAccount = { null },
                ),
                trust = ServerTrustStore(context, Dispatchers.IO),
                cooldowns = SignInCooldownStore(context, Dispatchers.IO),
                scope = scope,
                io = Dispatchers.IO,
                clock = Clock.systemUTC(),
                refreshes = CountingRefresh(),
            )
        }

        @Composable
        fun SignInPage() = SignInScreen(signInModel, onSignedIn = {})

        @Composable
        fun Framed(page: Route, content: @Composable () -> Unit) = SignedInFrame(
            pages = topLevelPages,
            current = page,
            onOpen = {},
            band = syncBand(ScreenshotTest.SYNC_STATUS, ScreenshotTest.TODAY),
        ) { content() }
    }

    private val screens: List<Screen> = listOf(
        Screen("sign-in") { SignInPage() },
        Screen("the Timer page's List view") { Framed(Route.Timer) { SampleTimerPage(TimerView.LIST) } },
        Screen("the Timer page's Calendar view") { Framed(Route.Timer) { SampleTimerPage(TimerView.CALENDAR) } },
        Screen("the entry form") { Framed(Route.Timer) { SampleEntryForm() } },
        Screen("the Summary report") { Framed(ReportsRoute) { Reports(ReportTab.SUMMARY) } },
        Screen("the Detailed report") { Framed(ReportsRoute) { Reports(ReportTab.DETAILED) } },
        Screen("the Weekly report") { Framed(ReportsRoute) { Reports(ReportTab.WEEKLY) } },
        Screen("Projects") {
            Framed(ProjectsRoute) {
                ProjectsScreen(
                    ProjectsPageState(
                        CatalogSamples.PROJECTS_LOADED,
                        false,
                        emptyList(),
                        CatalogSamples.CACHED_CLIENTS,
                    ),
                    actions = ProjectsActions(),
                )
            }
        },
        Screen("the project form") {
            Framed(ProjectsRoute) {
                ProjectsScreen(
                    ProjectsPageState(
                        CatalogSamples.PROJECTS_LOADED.copy(
                            overlay = ProjectForm.of(null, CatalogSamples.PROJECTS.size),
                        ),
                        false,
                        emptyList(),
                        CatalogSamples.CACHED_CLIENTS,
                    ),
                    actions = ProjectsActions(),
                )
            }
        },
        Screen("Clients") {
            Framed(ClientsRoute) {
                ClientsScreen(
                    ClientsPageState(CatalogSamples.CLIENTS_LOADED, false, emptyList()),
                    actions = ClientsActions(),
                )
            }
        },
        Screen("the client form") {
            Framed(ClientsRoute) {
                ClientsScreen(
                    ClientsPageState(
                        CatalogSamples.CLIENTS_LOADED.copy(overlay = ClientsOverlay.New()),
                        false,
                        emptyList(),
                    ),
                    actions = ClientsActions(),
                )
            }
        },
        Screen("Tags") {
            Framed(TagsRoute) {
                TagsScreen(
                    TagsPageState(CatalogSamples.TAGS_LOADED, false, emptyList()),
                    actions = TagsActions(),
                )
            }
        },
        Screen("Trash") {
            Framed(
                TrashRoute,
            ) { TrashScreen(TrashPageState(TRASH, false, ScreenshotTest.ACCOUNT), actions = TrashActions()) }
        },
        Screen("Settings") {
            Framed(SettingsRoute) {
                SettingsContent(
                    settings = ThemeSettings(),
                    onModeChange = {},
                    onDynamicColorChange = {},
                    signOut = SignOutConfirmation.State(),
                    onSignOut = {},
                    onConfirmSignOut = {},
                    onCancelSignOut = {},
                    onOpenLicences = {},
                )
            }
        },
        Screen("Licences") { Framed(SettingsRoute) { LicencesScreen(onBack = {}) } },
    )

    private companion object {
        const val WINDOW = "window"

        /** Pixel rounding, at a density that may be scaled down. */
        const val TOLERANCE_PX = 1.5f

        /** Pixel rounding in the window's dp, at a density that may be scaled down. */
        const val TOLERANCE_DP = 2f

        const val LARGEST_FONT_SCALE = 2f

        /** A phone held sideways, short of room for the rail's items at the largest font scale. */
        val SHORT_HEIGHT = 360.dp

        /** The bar's and the rail's items, in order. */
        val NAVIGATION_LABELS = listOf(
            TimerR.string.nav_timer,
            UiR.string.pomodoro_title,
            ProjectsR.string.nav_projects,
            SettingsR.string.settings,
            R.string.nav_more,
        )

        /** Anything a person reads or uses. */
        val CONTROL_OR_TEXT: SemanticsMatcher =
            hasClickAction() or hasSetTextAction() or isToggleable() or isSelectable() or
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

        /** Today's first entry in ScreenshotTest's history, not the one the form edits. */
        const val HISTORY_ENTRY = "Code review"

        val TRASH = TrashState(
            entries = listOf(
                DeletedEntry(
                    id = EntryId(1),
                    description = "Review the quarterly report with the whole team",
                    projectId = ProjectId(3),
                    projectName = "Website redesign for the new brand",
                    start = Instant.parse("2026-10-12T07:00:00Z"),
                    duration = Duration.ofSeconds(5400),
                    deletedAt = Instant.parse("2026-10-14T08:00:00Z"),
                ),
            ),
        )
    }
}

/** The Timer page in [view], with ScreenshotTest's idle timer, history and week. */
@Composable
internal fun SampleTimerPage(view: TimerView) {
    TimerScreen(
        view = view,
        onViewChange = {},
        timer = {
            TimerBarContent(
                state = TimerState.Idle(EntryFields(), EntryLabels(), workspaceId = WorkspaceId(1)),
                elapsedSeconds = 0,
                onDescriptionChange = {},
                onDescriptionFinish = {},
                onPick = {},
                onBillableChange = {},
                onStart = {},
                onStop = {},
                onAddEntry = {},
                picker = { _, _, _, _, _ -> },
            )
        },
        listContent = { HistoryContent(state = ScreenshotTest.HISTORY, onLoadOlder = {}) },
        calendarContent = {
            CalendarContent(
                state = ScreenshotTest.CALENDAR,
                now = ScreenshotTest.TODAY.instant(),
                onStep = {},
                onSelect = {},
                onToday = {},
                onRetry = {},
            )
        },
    )
}

/** The entry form for ScreenshotTest's Sprint planning, a stopped entry, on Tokkl, which offers No times. */
@Composable
internal fun SampleEntryForm() {
    EntryFormContent(
        state = EntryFormState.Open(
            editing = true,
            workspaceId = WorkspaceId(1),
            fields = EntryFields(description = "Sprint planning", projectId = ProjectId(ScreenshotTest.APP.id)),
            labels = EntryLabels(project = ScreenshotTest.APP.name, projectColor = ScreenshotTest.APP.colour),
            original = Picked(projectId = ProjectId(ScreenshotTest.APP.id)),
            date = LocalDate.of(2026, 10, 14),
            start = "08:30",
            end = "12:00",
            duration = "3:30:00",
            mistakes = emptyMap(),
            endsOn = null,
            saving = false,
            noTimesOffered = true,
        ),
        actions = EntryFormActions(),
        picker = { _, _, _, _, _ -> },
    )
}

/** A report tab with ScreenshotTest's reports for its week. */
@Composable
private fun Reports(tab: ReportTab) {
    val report = when (tab) {
        ReportTab.SUMMARY -> ScreenshotTest.REPORT
        ReportTab.DETAILED -> ScreenshotTest.DETAILED
        ReportTab.WEEKLY -> ScreenshotTest.WEEKLY
    }
    ReportsScreen(
        state = ReportsState.Loaded(RangeSelection(ScreenshotTest.WEEK, RangePreset.THIS_WEEK), report),
        tab = tab,
        settings = ScreenshotTest.ACCOUNT,
        time = ScreenshotTest.ACCOUNT.time(ScreenshotTest.TODAY),
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

/** A phone held upright. */
class CompactAdaptiveLayoutTest : AdaptiveLayoutTest(DpSize(411.dp, 891.dp), WindowWidth.COMPACT)

/** An unfolded foldable or a small tablet held upright. */
class MediumAdaptiveLayoutTest : AdaptiveLayoutTest(DpSize(700.dp, 1000.dp), WindowWidth.MEDIUM)

/** A tablet held sideways. */
class ExpandedAdaptiveLayoutTest : AdaptiveLayoutTest(DpSize(1280.dp, 800.dp), WindowWidth.EXPANDED)
